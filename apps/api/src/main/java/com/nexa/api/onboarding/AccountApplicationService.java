package com.nexa.api.onboarding;

import static com.nexa.api.onboarding.AccountApplicationDtos.*;

import com.nexa.api.beans.*;
import com.nexa.api.exep.*;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.repository.CustomerDao;
import com.nexa.api.service.BusinessDateResolver;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.OpeningCashPostingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Manual review and actual cash acknowledgements; never a government-verification service. */
@Service
@Transactional
public class AccountApplicationService {
  public static final BigDecimal MINIMUM = new BigDecimal("1000.00");
  public static final BigDecimal MAXIMUM = new BigDecimal("10000000.00");
  public static final String CONSENT_VERSION = "in-person-identity-v1";
  public static final String VERIFICATION_METHOD = "IN_PERSON_ORIGINAL";
  private static final Set<String> STATES = Set.of("DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED",
      "APPROVED_AWAITING_CASH", "CASH_RECEIVED", "OPENED", "REJECTED", "CANCELLED", "REFUND_PENDING", "REFUNDED");
  private final JdbcTemplate db;
  private final CurrentUserProvider current;
  private final Clock clock;
  private final BusinessDateResolver dates;
  private static final java.security.SecureRandom ACCOUNT_RANDOM = new java.security.SecureRandom();
  private final IdentifierProtector identities;
  private final OpeningCashPostingService cash;
  private final AccountDao accounts;
  private final CustomerDao customers;
  private final boolean enabled;

  public AccountApplicationService(JdbcTemplate db, CurrentUserProvider current, Clock clock,
      BusinessDateResolver dates, IdentifierProtector identities, OpeningCashPostingService cash,
      AccountDao accounts, CustomerDao customers, @Value("${nexa.onboarding.enabled:false}") boolean enabled) {
    this.db=db; this.current=current; this.clock=clock; this.dates=dates;
    this.identities=identities; this.cash=cash; this.accounts=accounts; this.customers=customers; this.enabled=enabled;
  }

  @Transactional(readOnly=true)
  public Map<String,Object> requirements() {
    Map<String,Object> actor=actor("CUSTOMER",false);
    boolean identityReady=enabled && identities.isReady();
    return Map.ofEntries(Map.entry("minimumOpeningAmount",MINIMUM.toPlainString()),
        Map.entry("maximumOpeningAmount",MAXIMUM.toPlainString()),Map.entry("currencyCode","INR"),
        Map.entry("businessDate",today().toString()),Map.entry("latestDateOfBirth",today().minusYears(18).toString()),
        Map.entry("allowedAccountTypes",hasSavings(number(actor,"ID"))?List.of():List.of("SAVINGS")),
        Map.entry("identityTypes",List.of("AADHAAR","PAN","PASSPORT")),
        Map.entry("verificationMethod",VERIFICATION_METHOD),
        Map.entry("consentVersion",CONSENT_VERSION),
        Map.entry("consentNotice","I request in-person manual review of my profile and original identity document for opening a savings account. I provide only the last four Aadhaar digits, or my PAN or Indian passport number, which is stored encrypted. No document copy is uploaded. The admin must inspect the original in person; this is not government verification. My opening deposit must be recorded as actual cash received before opening."),
        Map.entry("applicationsAvailable",enabled),Map.entry("identityDetailsAvailable",identityReady),
        Map.entry("cashReceiptAvailable",identityReady && cashReady()),Map.entry("governmentVerification",false));
  }

  /** Infrastructure snapshot only; each action still checks its application and receipt atomically. */
  @Transactional(readOnly=true)
  public Map<String,Object> readiness() {
    actor("ADMIN",false);
    boolean readable=identities.isReady();
    var infrastructure=cash.infrastructureReadiness();
    boolean cashAvailable=infrastructure.cashAvailable(), holdingAvailable=infrastructure.holdingAvailable();
    var blockers=new ArrayList<String>();
    if(!enabled)blockers.add("WORKFLOW_DISABLED");
    if(!readable)blockers.add("IDENTITY_CONFIGURATION_UNAVAILABLE");
    if(!cashAvailable)blockers.add("CASH_ACCOUNT_UNAVAILABLE");
    if(!holdingAvailable)blockers.add("OPENING_HOLD_UNAVAILABLE");
    return Map.of("checkedAt",now().toString(),"applicationsAvailable",enabled,
        "identityDetailsAvailable",enabled && readable,
        "reviewsAvailable",enabled && readable,
        "cashReceiptAvailable",enabled && readable && cashAvailable && holdingAvailable,
        "accountOpeningAvailable",enabled && readable && holdingAvailable,
        "cashRefundAvailable",enabled && cashAvailable && holdingAvailable,
        "blockers",List.copyOf(blockers));
  }

  public Map<String,Object> create(CreateRequest r) {
    enabled(); require(r!=null,"Application details are required.");
    String key=key(r.requestKey()); BigDecimal amount=amount(r.openingAmount()); adult(r.dateOfBirth());
    require("SAVINGS".equals(r.accountType()) && "INR".equals(r.currencyCode()),"Only personal SAVINGS/INR applications are available.");
    require(Boolean.TRUE.equals(r.consentAccepted()) && CONSENT_VERSION.equals(r.consentVersion()),"Read and accept the current review notice.");
    var owner=actor("CUSTOMER",true); long customerId=number(owner,"ID");
    String normalized=identities.normalize(r.identityType(),r.identityNumber());
    String suppliedPhone=r.phoneNumber()==null?null:phone(r.phoneNumber());
    String legacyHash=identities.fingerprint(identities.currentKeyId(),"CREATE",text(owner,"USER_ID"),r.accountType(),r.currencyCode(),r.dateOfBirth(),amount,r.consentVersion(),true,r.identityType(),normalized);
    // Fingerprint explicit phone input; a profile fallback must stay replayable after profile edits.
    String hash=identities.fingerprint(identities.currentKeyId(),"CREATE_WITH_PHONE",legacyHash,suppliedPhone);
    var prior=rows("SELECT * FROM account_applications WHERE customer_id=? AND request_key=?",customerId,key);
    if(!prior.isEmpty()) {
      var ctx=new Context(owner,owner,prior.get(0));
      if(replayed(ctx,key,"APPLICATION_CREATED",hash,suppliedPhone==null?legacyHash:hash)) return view(ctx.app());
      throw new ConflictException("The request key already exists without a matching audit event.");
    }
    eligibleOwner(owner,null);
    requireProfile(owner);
    String applicationPhone=suppliedPhone==null?phone(text(owner,"PHONE_NUMBER")):suppliedPhone;
    requireAvailablePhone(customerId,applicationPhone);
    if(count("SELECT COUNT(*) FROM account_applications WHERE customer_id=? AND status NOT IN ('REJECTED','CANCELLED','REFUNDED')",customerId)>0)
      throw new ConflictException("A live application or opened savings account already exists.");
    String id=UUID.randomUUID().toString(); OffsetDateTime now=now();
    var identity=identities.protect(id,text(owner,"USER_ID"),r.identityType(),normalized);
    db.update("INSERT INTO account_applications(id,customer_id,request_key,account_type,currency_code,full_name,email,phone_number,date_of_birth,business_date,requested_amount,consent_notice_version,consented_at,created_at,updated_at,identity_type,identity_ciphertext,identity_key_id,identity_last4) VALUES(?,?,?,'SAVINGS','INR',?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,customerId,key,text(owner,"FULL_NAME"),text(owner,"EMAIL"),applicationPhone,r.dateOfBirth(),today(),amount,CONSENT_VERSION,now,now,now,identity.type(),identity.ciphertext(),identity.keyId(),identity.last4());
    var app=application(id,false);
    event(new Context(owner,owner,app),key,"APPLICATION_CREATED",hash,null,null,null,null,null,0);
    return view(app);
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> listMine() {
    var owner=actor("CUSTOMER",false);
    return rows("SELECT * FROM account_applications WHERE customer_id=? ORDER BY created_at DESC FETCH FIRST 100 ROWS ONLY",number(owner,"ID"))
        .stream().map(this::summary).toList();
  }
  @Transactional(readOnly=true)
  public Map<String,Object> getMine(UUID id) {return view(context(id,false,false).app());}
  @Transactional(readOnly=true)
  public Map<String,Object> getAdmin(UUID id) {return view(context(id,true,false).app());}

  /** Explicit administrator-only reveal; ordinary detail/list calls always return a mask. */
  @Transactional(readOnly=true)
  public Map<String,Object> revealIdentity(UUID id) {
    enabled();var ctx=context(id,true,false);state(ctx,"PENDING_REVIEW");
    return Map.of("identityType",text(ctx.app(),"IDENTITY_TYPE"),"identityNumber",identityNumber(ctx),
        "verificationMethod",VERIFICATION_METHOD);
  }

  /** Changes remain private draft data until the customer submits them for a new review. */
  public Map<String,Object> updateDetails(UUID id,DetailsRequest r) {
    enabled();require(r!=null,"Application details are required.");var ctx=context(id,false,true);
    String applicationPhone=phone(r.phoneNumber());adult(r.dateOfBirth());BigDecimal requested=amount(r.openingAmount());
    boolean replaceIdentity=r.identityType()!=null || r.identityNumber()!=null;
    require(!replaceIdentity || (r.identityType()!=null && r.identityNumber()!=null),
        "To change identity details, provide both the identity type and number.");
    String normalized=replaceIdentity?identities.normalize(r.identityType(),r.identityNumber()):null;
    String hash=identities.fingerprint(identities.currentKeyId(),"APPLICATION_UPDATED",r.expectedVersion(),
        applicationPhone,r.dateOfBirth(),requested,r.identityType(),normalized);
    if(replayed(ctx,key(r.requestKey()),"APPLICATION_UPDATED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"DRAFT","CHANGES_REQUESTED");
    eligibleOwner(ctx.owner(),null);requireProfile(ctx.owner());requireConsent(ctx.app());
    requireAvailablePhone(number(ctx.owner(),"ID"),applicationPhone);
    if(count("SELECT COUNT(*) FROM opening_cash_receipts WHERE application_id=?",id.toString())>0)
      throw new ConflictException("Recorded opening cash prevents changes to application details.");
    if(replaceIdentity) {
      var identity=identities.protect(id.toString(),text(ctx.owner(),"USER_ID"),r.identityType(),normalized);
      db.update("UPDATE account_applications SET identity_type=?,identity_ciphertext=?,identity_key_id=?,identity_last4=? WHERE id=?",
          identity.type(),identity.ciphertext(),identity.keyId(),identity.last4(),id.toString());
    }
    db.update("UPDATE account_applications SET phone_number=?,date_of_birth=?,business_date=?,requested_amount=?,review_decision='PENDING',reviewed_by=NULL,reviewed_at=NULL,review_reason=NULL WHERE id=?",
        applicationPhone,r.dateOfBirth(),today(),requested,id.toString());
    return advance(ctx,key(r.requestKey()),"APPLICATION_UPDATED",hash,text(ctx.app(),"STATUS"),null,null,null,null);
  }

  public Map<String,Object> updateIdentity(UUID id,IdentityRequest r) {
    enabled();require(r!=null,"Identity details are required.");var ctx=context(id,false,true);
    String normalized=identities.normalize(r.identityType(),r.identityNumber());
    String hash=identities.fingerprint(identities.currentKeyId(),"IDENTITY_UPDATED",r.expectedVersion(),r.identityType(),normalized);
    if(replayed(ctx,key(r.requestKey()),"IDENTITY_UPDATED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"DRAFT","CHANGES_REQUESTED");
    var identity=identities.protect(id.toString(),text(ctx.owner(),"USER_ID"),r.identityType(),normalized);
    db.update("UPDATE account_applications SET identity_type=?,identity_ciphertext=?,identity_key_id=?,identity_last4=?,review_decision='PENDING',reviewed_by=NULL,reviewed_at=NULL,review_reason=NULL WHERE id=?",
        identity.type(),identity.ciphertext(),identity.keyId(),identity.last4(),id.toString());
    return advance(ctx,key(r.requestKey()),"IDENTITY_UPDATED",hash,text(ctx.app(),"STATUS"),null,null,null,null);
  }

  @Transactional(readOnly=true)
  public Map<String,Object> listAdmin(String status,Integer page,Integer size) {
    actor("ADMIN",false);
    require(page!=null && page>=0 && page<=100000 && size!=null && size>=1 && size<=100,"Supply a page and size from 1 to 100.");
    require(status==null || STATES.contains(status),"Unknown application status.");
    String where=status==null?"":" WHERE status=?";
    Object[] params=status==null?new Object[]{}:new Object[]{status};
    var args=new ArrayList<Object>(Arrays.asList(params));args.add((long)page*size);args.add(size);
    return Map.of("items",rows("SELECT * FROM account_applications"+where+" ORDER BY created_at,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()).stream().map(this::summary).toList(),
        "total",count("SELECT COUNT(*) FROM account_applications"+where,params),"page",page,"size",size);
  }

  /** Kept as a fail-closed compatibility boundary; never opens or reads uploaded bytes. */
  public Map<String,Object> uploadDocument(UUID id,DocumentUploadRequest r,MultipartFile file) {
    context(id,false,false);
    throw new ConflictException("Document uploads have been retired. Supply identity details and show the original in person.");
  }

  public Map<String,Object> submit(UUID id,ActionRequest r) {
    enabled();var ctx=context(id,false,true);String hash=action("SUBMITTED",r);
    if(replayed(ctx,key(r.requestKey()),"SUBMITTED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"DRAFT","CHANGES_REQUESTED");eligibleOwner(ctx.owner(),ctx.app());
    requireConsent(ctx.app());adult(birth(ctx.app()));
    identityNumber(ctx);
    db.update("UPDATE account_applications SET status='PENDING_REVIEW',submitted_at=? WHERE id=?",now(),id.toString());
    return advance(ctx,key(r.requestKey()),"SUBMITTED",hash,"PENDING_REVIEW",null,null,null,null);
  }

  public Map<String,Object> cancel(UUID id,ActionRequest r) {
    enabled();var ctx=context(id,false,true);String hash=action("CANCELLED",r);
    if(replayed(ctx,key(r.requestKey()),"CANCELLED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"DRAFT","PENDING_REVIEW","CHANGES_REQUESTED","APPROVED_AWAITING_CASH");
    if(count("SELECT COUNT(*) FROM opening_cash_receipts WHERE application_id=?",id.toString())>0)
      throw new ConflictException("Cash was already recorded. An administrator must arrange a recorded refund.");
    db.update("UPDATE account_applications SET status='CANCELLED',ended_at=?,end_reason_code='CUSTOMER_CANCELLED' WHERE id=?",now(),id.toString());
    return advance(ctx,key(r.requestKey()),"CANCELLED",hash,"CANCELLED","CUSTOMER_CANCELLED",null,null,null);
  }

  public Map<String,Object> reviewDocument(UUID id,UUID docId,ReviewDocumentRequest r) {
    context(id,true,false);
    throw new ConflictException("Document-copy review has been retired. Review the original identity document in person.");
  }

  public Map<String,Object> reviewApplication(UUID id,ReviewRequest r) {
    enabled();require(r!=null,"Review details are required.");var ctx=context(id,true,true);reason(r.reason());
    require(Set.of("APPROVED","REJECTED","CHANGES_REQUESTED").contains(r.decision()),"Choose a supported review decision.");
    boolean approval="APPROVED".equals(r.decision());
    require(r.inPersonChecked()!=null && r.inPersonChecked()==approval,
        "Approval requires confirmation that the original identity document was checked in person; other decisions must not confirm approval.");
    String hash=approval?identities.fingerprint(identities.currentKeyId(),"APPROVED",r.expectedVersion(),r.reason().trim(),VERIFICATION_METHOD,true,
        text(ctx.app(),"IDENTITY_TYPE"),identityNumber(ctx)):digest(r.decision(),r.expectedVersion(),r.reason().trim(),VERIFICATION_METHOD,false);
    if(replayed(ctx,key(r.requestKey()),r.decision(),hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"PENDING_REVIEW");
    if("CHANGES_REQUESTED".equals(r.decision()))
      return advance(ctx,key(r.requestKey()),r.decision(),hash,"CHANGES_REQUESTED","INCOMPLETE_DOCUMENTS",r.reason().trim(),null,null);
    if(approval){eligibleOwner(ctx.owner(),ctx.app());requireConsent(ctx.app());identityNumber(ctx);}
    if("REJECTED".equals(r.decision()))
      db.update("UPDATE account_applications SET status='REJECTED',review_decision='REJECTED',reviewed_by=?,reviewed_at=?,review_reason=?,ended_at=?,end_reason_code='REVIEW_REJECTED' WHERE id=?",actorId(ctx),now(),r.reason().trim(),now(),id.toString());
    else
      db.update("UPDATE account_applications SET status='APPROVED_AWAITING_CASH',review_decision='APPROVED',reviewed_by=?,reviewed_at=?,review_reason=? WHERE id=?",actorId(ctx),now(),r.reason().trim(),id.toString());
    return advance(ctx,key(r.requestKey()),r.decision(),hash,"APPROVED".equals(r.decision())?"APPROVED_AWAITING_CASH":"REJECTED",
        "APPROVED".equals(r.decision())?"REVIEW_ACCEPTED":"OTHER_REVIEW",r.reason().trim(),null,null);
  }

  public Map<String,Object> receiveCash(UUID id,CashReceiptRequest r) {
    enabled();require(r!=null,"Cash confirmation is required.");var ctx=context(id,true,true);
    // The reviewed customer request is authoritative: neither amount nor number is client input.
    BigDecimal paid=money(ctx.app(),"REQUESTED_AMOUNT").setScale(2);
    require(Boolean.TRUE.equals(r.cashReceivedConfirmed()),"Only confirm after physically receiving the cash.");
    String hash=digest("CASH_RECEIVED",r.expectedVersion(),paid,true);
    if(replayed(ctx,key(r.requestKey()),"CASH_RECEIVED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"APPROVED_AWAITING_CASH");eligibleOwner(ctx.owner(),ctx.app());requireConsent(ctx.app());identityNumber(ctx);
    if(count("SELECT COUNT(*) FROM opening_cash_receipts WHERE application_id=? OR (received_by=? AND request_key=?)",id.toString(),actorId(ctx),key(r.requestKey()))>0)
      throw new ConflictException("That application, receipt number or cashier request key already has a receipt. Check its status; do not collect cash twice.");
    String transaction=cash.receive(paid,id.toString());String receipt=UUID.randomUUID().toString();OffsetDateTime now=now();
    String receiptNumber="NEXA-RCP-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
    db.update("INSERT INTO opening_cash_receipts(id,application_id,receipt_number,request_key,currency_code,amount,received_by,cash_received_at,recorded_at,receipt_transaction_id) VALUES(?,?,?,?,'INR',?,?,?,?,?)",
        receipt,id.toString(),receiptNumber,key(r.requestKey()),paid,actorId(ctx),now,now,transaction);
    return advance(ctx,key(r.requestKey()),"CASH_RECEIVED",hash,"CASH_RECEIVED",null,null,null,receipt);
  }

  public Map<String,Object> open(UUID id,ActionRequest r) {
    enabled();var ctx=context(id,true,true);String hash=action("ACCOUNT_OPENED",r);
    if(replayed(ctx,key(r.requestKey()),"ACCOUNT_OPENED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"CASH_RECEIVED");eligibleOwner(ctx.owner(),ctx.app());requireConsent(ctx.app());identityNumber(ctx);
    var receipt=receipt(id);if(!"RECEIVED".equals(text(receipt,"STATUS")))throw new ConflictException("This receipt is not available for opening.");
    validateReceiptPosting(ctx,receipt);
    Account account=new Account();account.setCustomer(customers.findById(number(ctx.owner(),"ID")).orElseThrow(()->new ResourceNotFoundException("Customer not found.")));
    // A generated bank account number and label: never supplied by a customer/admin.
    account.setAccountNumber(uniqueAccountNumber());
    account.setAccountName("Savings account");account.setAccountType(AccountType.SAVINGS);account.setAccountCategory(AccountCategory.CUSTOMER);
    account.setCurrencyCode("INR");account.setStatus(AccountStatus.ACTIVE);account.setBalance(BigDecimal.ZERO);
    account=accounts.saveAndFlush(account);
    String transaction=cash.allocate(account.getId(),money(receipt,"AMOUNT"),id.toString());OffsetDateTime now=now();
    // Composite FK requires this OPENED link before the receipt allocation update.
    db.update("UPDATE account_applications SET status='OPENED',account_id=?,opened_at=? WHERE id=?",account.getId(),now,id.toString());
    db.update("UPDATE opening_cash_receipts SET status='APPLIED',allocation_transaction_id=?,allocated_account_id=?,allocated_at=?,version=version+1 WHERE id=?",transaction,account.getId(),now,text(receipt,"ID"));
    try {
      db.update("UPDATE customers SET date_of_birth=?,phone_number=?,updated_at=? WHERE id=?",birth(ctx.app()),phone(text(ctx.app(),"PHONE_NUMBER")),LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC),number(ctx.owner(),"ID"));
    } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
      // A competing profile write can claim the number after the availability check.
      // Throwing out of this transaction rolls back the account, allocation and receipt changes.
      throw new ConflictException("The phone number became unavailable while opening the account. No account was opened. Ask an administrator to arrange a refund, then submit a corrected application.");
    }
    return advance(ctx,key(r.requestKey()),"ACCOUNT_OPENED",hash,"OPENED",null,null,null,text(receipt,"ID"));
  }

  public Map<String,Object> requestRefund(UUID id,ReasonRequest r) {
    enabled();var ctx=context(id,true,true);reason(r.reason());String hash=digest("REFUND_REQUESTED",r.expectedVersion(),r.reason().trim());
    if(replayed(ctx,key(r.requestKey()),"REFUND_REQUESTED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"CASH_RECEIVED");var receipt=receipt(id);
    if(!"RECEIVED".equals(text(receipt,"STATUS")))throw new ConflictException("Only unallocated received cash can be refunded here.");
    validateReceiptPosting(ctx,receipt);
    db.update("UPDATE opening_cash_receipts SET status='REFUND_PENDING',refund_requested_by=?,refund_requested_at=?,refund_reason=?,version=version+1 WHERE id=?",actorId(ctx),now(),r.reason().trim(),text(receipt,"ID"));
    return advance(ctx,key(r.requestKey()),"REFUND_REQUESTED",hash,"REFUND_PENDING","OTHER_REVIEW",r.reason().trim(),null,text(receipt,"ID"));
  }

  public Map<String,Object> refund(UUID id,RefundRequest r) {
    enabled();var ctx=context(id,true,true);require(Boolean.TRUE.equals(r.cashReturnedConfirmed()),"Only confirm after physically returning the cash.");
    String hash=digest("CASH_REFUNDED",r.expectedVersion(),true);
    if(replayed(ctx,key(r.requestKey()),"CASH_REFUNDED",hash))return view(ctx.app());
    version(ctx,r.expectedVersion());state(ctx,"REFUND_PENDING");var receipt=receipt(id);
    if(!"REFUND_PENDING".equals(text(receipt,"STATUS")))throw new ConflictException("This receipt is not awaiting refund.");
    validateReceiptPosting(ctx,receipt);String transaction=cash.refund(money(receipt,"AMOUNT"),id.toString());OffsetDateTime now=now();
    db.update("UPDATE opening_cash_receipts SET status='REFUNDED',refund_transaction_id=?,refunded_by=?,refunded_at=?,version=version+1 WHERE id=?",transaction,actorId(ctx),now,text(receipt,"ID"));
    db.update("UPDATE account_applications SET status='REFUNDED',ended_at=?,end_reason_code='CASH_RETURNED' WHERE id=?",now,id.toString());
    return advance(ctx,key(r.requestKey()),"CASH_REFUNDED",hash,"REFUNDED","CASH_RETURNED",null,null,text(receipt,"ID"));
  }

  @Transactional(readOnly=true)
  public DocumentDownload downloadMine(UUID id,UUID documentId) {context(id,false,false);throw new ConflictException("Document copies are not retained. Show the original identity document in person.");}
  @Transactional(readOnly=true)
  public DocumentDownload downloadAdmin(UUID id,UUID documentId) {context(id,true,false);throw new ConflictException("Document copies are not retained. Review the original identity document in person.");}

  private record Context(Map<String,Object> actor,Map<String,Object> owner,Map<String,Object> app) {}
  private Context context(UUID id,boolean admin,boolean lock) {
    require(id!=null,"Application ID is required.");var initialActor=actor(admin?"ADMIN":"CUSTOMER",false);
    var app=application(id.toString(),false);long ownerId=number(app,"CUSTOMER_ID");
    if(!admin && number(initialActor,"ID")!=ownerId)throw new ResourceNotFoundException("Application not found.");
    if(admin && number(initialActor,"ID")==ownerId)throw new AccessDeniedException("Self-review and self-receipt are not allowed.");
    if(lock)rows("SELECT id FROM customers WHERE id IN (?,?) ORDER BY id FOR UPDATE",number(initialActor,"ID"),ownerId);
    var actor=actor(admin?"ADMIN":"CUSTOMER",false);
    var owner=one("SELECT * FROM customers WHERE id=?",ownerId);
    app=application(id.toString(),lock);
    if(number(app,"CUSTOMER_ID")!=ownerId)throw new ConflictException("Application ownership changed.");
    return new Context(actor,owner,app);
  }
  private Map<String,Object> actor(String role,boolean lock) {
    var result=rows("SELECT * FROM customers WHERE user_id=?"+(lock?" FOR UPDATE":""),current.userId());
    if(result.isEmpty() || !role.equals(text(result.get(0),"ROLE")) || !"ACTIVE".equals(text(result.get(0),"STATUS")))
      throw new AccessDeniedException("An active "+role.toLowerCase(Locale.ROOT)+" profile is required.");
    return result.get(0);
  }
  private Map<String,Object> application(String id,boolean lock) {return one("SELECT * FROM account_applications WHERE id=?"+(lock?" FOR UPDATE":""),id);}
  private Map<String,Object> document(UUID app,UUID doc) {require(doc!=null,"Document ID is required.");return one("SELECT * FROM application_documents WHERE application_id=? AND id=?",app.toString(),doc.toString());}
  private Map<String,Object> receipt(UUID app) {return one("SELECT * FROM opening_cash_receipts WHERE application_id=? FOR UPDATE",app.toString());}
  private String actorId(Context ctx){return text(ctx.actor(),"USER_ID");}
  private void enabled(){if(!enabled)throw new ConflictException("Manual onboarding is not activated yet. No account or money movement was created.");}
  private void version(Context ctx,Long expected){require(expected!=null && expected>=0,"Supply the current application version.");if(number(ctx.app(),"VERSION")!=expected)throw new ConflictException("The application changed. Refresh before continuing.");}
  private void state(Context ctx,String... allowed){if(!Arrays.asList(allowed).contains(text(ctx.app(),"STATUS")))throw new ConflictException("This action is not allowed in the current application state.");}
  private boolean replayed(Context ctx,String key,String type,String hash,String... previousHashes) {
    var result=rows("SELECT actor_user_id,event_type,request_fingerprint FROM application_events WHERE application_id=? AND event_key=?",text(ctx.app(),"ID"),key);
    if(result.isEmpty())return false;var prior=result.get(0);
    if(!actorId(ctx).equals(text(prior,"ACTOR_USER_ID")) || !type.equals(text(prior,"EVENT_TYPE")) || (!hash.equals(text(prior,"REQUEST_FINGERPRINT")) && !Arrays.asList(previousHashes).contains(text(prior,"REQUEST_FINGERPRINT"))))
      throw new ConflictException("The request key was already used with different details or by another actor.");
    return true;
  }
  private Map<String,Object> advance(Context ctx,String key,String type,String hash,String to,String reasonCode,String reason,String document,String receipt) {
    long next=number(ctx.app(),"VERSION")+1;
    int changed=db.update("UPDATE account_applications SET status=?,version=?,updated_at=? WHERE id=? AND version=?",to,next,now(),text(ctx.app(),"ID"),number(ctx.app(),"VERSION"));
    if(changed!=1)throw new ConflictException("The application changed. Refresh before retrying.");
    event(ctx,key,type,hash,text(ctx.app(),"STATUS"),reasonCode,reason,document,receipt,next);
    return view(application(text(ctx.app(),"ID"),false));
  }
  private void event(Context ctx,String key,String type,String hash,String from,String reasonCode,String reason,String document,String receipt,long version) {
    String to=version==0?"DRAFT":text(application(text(ctx.app(),"ID"),false),"STATUS");
    db.update("INSERT INTO application_events(id,application_id,event_key,application_version,event_type,actor_user_id,actor_role,from_status,to_status,reason_code,review_reason,document_id,receipt_id,correlation_id,request_fingerprint,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),text(ctx.app(),"ID"),key,version,type,actorId(ctx),text(ctx.actor(),"ROLE"),from,to,reasonCode,reason,document,receipt,UUID.randomUUID().toString(),hash,now());
  }
  private void eligibleOwner(Map<String,Object> owner,Map<String,Object> app) {
    if(!"CUSTOMER".equals(text(owner,"ROLE")) || !"ACTIVE".equals(text(owner,"STATUS")))throw new ConflictException("The customer is no longer eligible.");
    if(hasSavings(number(owner,"ID")))throw new ConflictException("This customer already has a savings account, including closed accounts.");
    if(app!=null){adult(birth(app));requireAvailablePhone(number(owner,"ID"),phone(text(app,"PHONE_NUMBER")));for(String field:List.of("FULL_NAME","EMAIL"))
      if(!Objects.equals(text(owner,field),text(app,field)))throw new ConflictException("The profile has changed since submission. Cancel and submit a newly reviewed application, or arrange a refund if cash was received.");}
  }
  private void requireAvailablePhone(long owner,String normalizedPhone) {
    if(count("SELECT COUNT(*) FROM customers WHERE id<>? AND phone_number IS NOT NULL AND REGEXP_REPLACE(phone_number,'[() -]','')=?",owner,normalizedPhone)>0)
      throw new ConflictException("This phone number is already in use. Enter a different phone number or contact support.");
  }
  private boolean hasSavings(long owner){return count("SELECT COUNT(*) FROM accounts WHERE customer_id=? AND account_category='CUSTOMER' AND account_type='SAVINGS'",owner)>0;}
  private void requireConsent(Map<String,Object> app){if(app.get("CONSENT_WITHDRAWN_AT")!=null || !CONSENT_VERSION.equals(text(app,"CONSENT_NOTICE_VERSION")))throw new ConflictException("Current consent is required.");}
  private void requireProfile(Map<String,Object> owner){require(text(owner,"FULL_NAME")!=null && !text(owner,"FULL_NAME").isBlank() && text(owner,"EMAIL")!=null,"Complete the customer profile first.");}
  private String identityNumber(Context ctx) {
    return identities.reveal(text(ctx.app(),"ID"),text(ctx.owner(),"USER_ID"),text(ctx.app(),"IDENTITY_TYPE"),
        text(ctx.app(),"IDENTITY_CIPHERTEXT"),text(ctx.app(),"IDENTITY_KEY_ID"),text(ctx.app(),"IDENTITY_LAST4"));
  }

  private String uniqueAccountNumber() {
    for(int attempt=0;attempt<10;attempt++) {
      StringBuilder number=new StringBuilder("9");
      while(number.length()<12)number.append(ACCOUNT_RANDOM.nextInt(10));
      if(!accounts.existsByAccountNumber(number.toString()))return number.toString();
    }
    throw new ConflictException("Unable to allocate an account number. Retry the opening request.");
  }
  private boolean cashReady(){var readiness=cash.infrastructureReadiness();return readiness.cashAvailable() && readiness.holdingAvailable();}
  private void validateReceiptPosting(Context ctx,Map<String,Object> receipt) {
    if(money(receipt,"AMOUNT").compareTo(money(ctx.app(),"REQUESTED_AMOUNT"))!=0)throw new ConflictException("Receipt amount does not match this application.");
    String tx=text(receipt,"RECEIPT_TRANSACTION_ID");BigDecimal amount=money(receipt,"AMOUNT");
    if(count("SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id=t.destination_account_id WHERE t.id=? AND t.record_kind='PAYMENT' AND t.transaction_type='DEPOSIT' AND t.status='SUCCESS' AND t.operation='OPENING_CASH_RECEIPT' AND t.target_id=? AND t.user_id=? AND t.currency_code='INR' AND t.amount=? AND t.source_account_id IS NULL AND a.account_number='NEXA-OPENING-HOLD'",tx,text(ctx.app(),"ID"),text(receipt,"RECEIVED_BY"),amount)!=1)
      throw new ConflictException("The original cash posting requires reconciliation; no new movement was made.");
    var entries=rows("SELECT l.entry_type,l.amount,a.account_number FROM journal_entries j JOIN ledger_entries l ON l.journal_entry_id=j.id JOIN accounts a ON a.id=l.account_id WHERE j.transaction_id=? AND j.status='POSTED'",tx);
    if(entries.size()!=2 || entries.stream().filter(e->"DEBIT".equals(text(e,"ENTRY_TYPE")) && "SYSTEM-CASH".equals(text(e,"ACCOUNT_NUMBER")) && money(e,"AMOUNT").compareTo(amount)==0).count()!=1
        || entries.stream().filter(e->"CREDIT".equals(text(e,"ENTRY_TYPE")) && "NEXA-OPENING-HOLD".equals(text(e,"ACCOUNT_NUMBER")) && money(e,"AMOUNT").compareTo(amount)==0).count()!=1)
      throw new ConflictException("The original cash journal requires reconciliation.");
    if(count("SELECT COUNT(*) FROM opening_cash_receipts WHERE (receipt_transaction_id=? AND id<>?) OR allocation_transaction_id=? OR refund_transaction_id=?",tx,text(receipt,"ID"),tx,tx)>0)
      throw new ConflictException("Cash posting reference was reused; reconciliation is required.");
  }

  private Map<String,Object> summary(Map<String,Object> app) {
    var result=new LinkedHashMap<String,Object>();
    for(String field:List.of("ID","ACCOUNT_TYPE","CURRENCY_CODE","FULL_NAME","EMAIL","PHONE_NUMBER","DATE_OF_BIRTH","STATUS","REVIEW_DECISION","REVIEW_REASON","VERSION","CREATED_AT","UPDATED_AT","SUBMITTED_AT","REVIEWED_AT","OPENED_AT","ENDED_AT","END_REASON_CODE","ACCOUNT_ID"))result.put(camel(field),safe(app.get(field)));
    result.put("dateOfBirth",birth(app).toString());
    result.put("identityType",text(app,"IDENTITY_TYPE"));
    result.put("identityMasked","••••"+text(app,"IDENTITY_LAST4"));
    result.put("openingAmount",money(app,"REQUESTED_AMOUNT").setScale(2).toPlainString());return result;
  }
  private Map<String,Object> view(Map<String,Object> app) {
    var result=summary(app);String id=text(app,"ID");
    result.put("verificationMethod",VERIFICATION_METHOD);
    result.put("documents",rows("SELECT id,document_type,media_type,byte_size,storage_status,safety_status,review_status,review_reason,uploaded_at,reviewed_at FROM application_documents WHERE application_id=? ORDER BY uploaded_at,id",id).stream().map(this::publicMap).toList());
    result.put("events",rows("SELECT id,event_type,application_version,actor_role,from_status,to_status,reason_code,review_reason,document_id,receipt_id,created_at FROM application_events WHERE application_id=? ORDER BY application_version",id).stream().map(this::publicMap).toList());
    result.put("receipts",rows("SELECT id,receipt_number,currency_code,amount,status,cash_received_at,recorded_at,allocated_at,refund_reason,refunded_at FROM opening_cash_receipts WHERE application_id=?",id).stream().map(r->{var m=publicMap(r);m.put("amount",money(r,"AMOUNT").setScale(2).toPlainString());return m;}).toList());
    return result;
  }
  private Map<String,Object> publicMap(Map<String,Object> row){var result=new LinkedHashMap<String,Object>();row.forEach((k,v)->result.put(camel(k),safe(v)));return result;}
  private Object safe(Object value){return value instanceof java.time.temporal.TemporalAccessor?value.toString():value;}
  private String camel(String key){String[] parts=key.toLowerCase(Locale.ROOT).split("_");StringBuilder b=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)b.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));return b.toString();}
  private List<Map<String,Object>> rows(String sql,Object... args){return db.query(sql,(rs,n)->{var row=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++) {
    Object value=switch(meta.getColumnType(i)){case Types.DATE -> rs.getObject(i,LocalDate.class);case Types.TIMESTAMP_WITH_TIMEZONE,-101 ->rs.getObject(i,OffsetDateTime.class);case Types.TIMESTAMP->rs.getObject(i,LocalDateTime.class);default->rs.getObject(i);};row.put(meta.getColumnLabel(i).toUpperCase(Locale.ROOT),value);}return row;},args);}
  private Map<String,Object> one(String sql,Object... args){var result=rows(sql,args);if(result.isEmpty())throw new ResourceNotFoundException("Requested application resource not found.");return result.get(0);}
  private long count(String sql,Object... args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
  private static String text(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:v.toString();}
  private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
  private static BigDecimal money(Map<String,Object> row,String key){return new BigDecimal(row.get(key).toString());}
  private LocalDate birth(Map<String,Object> row){Object value=row.get("DATE_OF_BIRTH");return value instanceof LocalDate date?date:LocalDate.parse(value.toString().substring(0,10));}
  private LocalDate today(){return LocalDate.now(clock.withZone(dates.zone()));}
  private OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
  private void adult(LocalDate birth){require(birth!=null && birth.getYear()>=1 && birth.getYear()<=9999 && birth.isBefore(today()) && !birth.isAfter(today().minusYears(18)),"Provide a valid date of birth; applicants must be at least 18.");}
  private static String key(UUID key){require(key!=null,"A UUID request key is required.");return key.toString();}
  private static BigDecimal amount(String input){require(input!=null && input.matches("(?:0|[1-9][0-9]{0,7})(?:\\.[0-9]{1,2})?"),"Enter an INR decimal amount with at most two decimal places.");BigDecimal value=new BigDecimal(input);require(value.compareTo(MINIMUM)>=0 && value.compareTo(MAXIMUM)<=0,"Opening cash must be between INR 1,000 and INR 1,00,00,000.");return value.setScale(2);}
  private static String phone(String value) {
    require(value!=null && !value.isBlank(),"Enter a phone number to open an account.");
    require(value.length()<=32 && value.matches("[+0-9() -]+"),
        "Enter a valid phone number with 10–15 digits and an optional leading +.");
    String normalized=value.replaceAll("[() -]","");
    require(normalized.matches("\\+?[0-9]{10,15}"),
        "Enter a valid phone number with 10–15 digits and an optional leading +.");
    return normalized;
  }
  private static void reason(String reason){
    require(reason!=null && !reason.isBlank() && reason.getBytes(StandardCharsets.UTF_8).length<=500
        && reason.codePoints().noneMatch(Character::isISOControl),
        "Provide a review reason of up to 500 UTF-8 bytes without control characters.");
    require(!java.util.regex.Pattern.compile("(?i)\\b[A-Z]{5}[0-9]{4}[A-Z]\\b").matcher(reason).find()
        && !java.util.regex.Pattern.compile("(?<![0-9])(?:[0-9][ -]?){11}[0-9](?![0-9])").matcher(reason).find()
        && !java.util.regex.Pattern.compile("(?i)\\b(?=[A-Z0-9]{0,7}[0-9])[A-Z][A-Z0-9]{7}\\b").matcher(reason).find(),
        "Do not put PAN, Aadhaar or passport numbers in review comments; use the private identity field instead.");
  }
  private static String action(String action,ActionRequest r){require(r!=null,"Request details are required.");key(r.requestKey());return digest(action,r.expectedVersion());}
  private static String digest(Object... parts){StringBuilder b=new StringBuilder();for(Object part:parts){String value=Objects.toString(part,"<null>");b.append(value.length()).append(':').append(value);}return hex(b.toString().getBytes(StandardCharsets.UTF_8));}
  private static String hex(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is unavailable.");}}
  private static void require(boolean valid,String message){if(!valid)throw new InvalidRequestException(message);}
}
