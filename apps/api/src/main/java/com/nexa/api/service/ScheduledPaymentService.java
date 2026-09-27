package com.nexa.api.service;

import com.nexa.api.beans.*;
import com.nexa.api.exep.*;
import com.nexa.api.repository.AccountDao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable one-time authorization. Schedule completion and its ledger posting commit together. */
@Service
public class ScheduledPaymentService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final BigDecimal MAX = new BigDecimal("9999999999999.99");
  private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
  public record Request(UUID requestKey, String sourceAccountId, String payeeId, BigDecimal amount, String dueAt) {}
  public record Authorization(boolean authorizationAccepted) {}
  public record Requirements(String businessDate, String earliestDueDate, String latestDueDate,
      String timezone, boolean executionEnabled) {}
  public record Receipt(String id, String payee, String amount, String currencyCode, String dueAt,
      String status, String accountId, String reference, boolean managed, String sourceName,
      String sourceMasked, String payeeId, String recipientName, String destinationMasked,
      String createdAt, String expiresAt, String authorizedAt, String completedAt,
      String failureReason, String timezone) {}
  private record Stored(Receipt receipt, String owner, long source, long destination, String fingerprint, Instant runAt) {}
  private record Route(Account source, Account destination) {}
  private static final class Rejected extends RuntimeException { Rejected(String message) { super(message); } }
  private final JdbcTemplate db;
  private final CurrentUserProvider current;
  private final AccountDao accounts;
  private final TransactionService transactions;
  private final EntityManager entities;
  private final Clock clock;
  private final boolean enabled;

  public ScheduledPaymentService(JdbcTemplate db, CurrentUserProvider current, AccountDao accounts,
      TransactionService transactions, EntityManager entities, Clock clock,
      @Value("${nexa.scheduled-payments.enabled:false}") boolean enabled) {
    this.db=db; this.current=current; this.accounts=accounts; this.transactions=transactions;
    this.entities=entities; this.clock=clock; this.enabled=enabled;
  }

  @Transactional(readOnly=true)
  public Requirements requirements() {
    owner(); LocalDate today=today();
    return new Requirements(today.toString(),today.plusDays(1).toString(),today.plusDays(365).toString(),ZONE.getId(),enabled);
  }

  @Transactional
  public Receipt prepare(Request request) {
    requireEnabled(); validate(request); String owner=owner(); requireActiveLockedOwner(owner);
    String id="SP-"+request.requestKey(), fingerprint=fingerprint(request);
    var previous=find(id,owner,false);
    if (previous.isPresent()) {
      if (!previous.get().fingerprint().equals(fingerprint)) throw new ConflictException("This request key was already used for different schedule details.");
      return expire(previous.get()).receipt();
    }
    if (db.queryForObject("SELECT COUNT(*) FROM authorized_scheduled_payments WHERE id=?",Long.class,id)>0)
      throw new ConflictException("This request key is already in use.");
    LocalDate date=futureDate(request.dueAt());
    Route route;
    try { route=route(Long.parseLong(request.sourceAccountId()),payee(request.payeeId(),owner),owner,request.amount(),false); }
    catch (Rejected rejected) { throw new InvalidRequestException(rejected.getMessage()); }
    Instant now=clock.instant();
    db.update("INSERT INTO authorized_scheduled_payments(id,request_key,request_fingerprint,user_id,source_account_id,source_name,source_masked,"
        +"payee_id,destination_account_id,recipient_name,destination_masked,amount,currency_code,due_date,run_at,status,created_at,updated_at,expires_at)"
        +" VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'INR',?,?,'READY',?,?,?)",id,request.requestKey().toString(),fingerprint,owner,
        route.source().getId(),route.source().getAccountName(),mask(route.source()),request.payeeId(),route.destination().getId(),
        route.destination().getCustomer().getFullName(),mask(route.destination()),request.amount(),date.toString(),
        Timestamp.from(date.atStartOfDay(ZONE).toInstant()),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now.plusSeconds(300)));
    return require(id,owner,false).receipt();
  }

  @Transactional
  public Receipt confirm(String id, Authorization authorization) {
    requireEnabled(); String owner=owner(); requireActiveLockedOwner(owner); Stored stored=expire(require(id,owner,true));
    if (!"READY".equals(stored.receipt().status())) return stored.receipt();
    if (authorization==null || !authorization.authorizationAccepted())
      throw new InvalidRequestException("Explicitly authorize this one-time transfer before scheduling it.");
    try {
      if (!LocalDate.parse(stored.receipt().dueAt()).isAfter(today())) throw new Rejected("The scheduled date has arrived. Create a new future-dated review.");
      verifyRoute(stored,false);
    } catch (Rejected rejected) { return terminal(stored,"FAILED",rejected.getMessage()); }
    Instant now=clock.instant();
    requireOne(db.update("UPDATE authorized_scheduled_payments SET status='SCHEDULED',authorization_version='one-time-internal-v1',authorized_at=?,updated_at=? WHERE id=? AND status='READY'",
        Timestamp.from(now),Timestamp.from(now),id));
    return require(id,owner,false).receipt();
  }

  @Transactional
  public Receipt cancel(String id) {
    String owner=owner(); lockOwner(owner); Stored stored=expire(require(id,owner,true));
    if (!Set.of("READY","SCHEDULED").contains(stored.receipt().status())) return stored.receipt();
    return terminal(stored,"CANCELLED","You cancelled this scheduled payment before execution.");
  }

  @Transactional(readOnly=true)
  public Receipt detail(String id) {
    String owner=owner(); validateId(id);
    var stored=find(id,owner,false);
    if (stored.isPresent()) return visible(stored.get().receipt());
    var legacy=db.query("SELECT * FROM transactions WHERE record_kind='SCHEDULED_PAYMENT' AND id=? AND user_id=?",(rs,n)->legacy(rs),id,owner);
    if (legacy.isEmpty()) throw new ResourceNotFoundException("Scheduled payment not found.");
    return legacy.get(0);
  }

  @Transactional(readOnly=true)
  public List<Receipt> list(String status,int page,int size) {
    if(page<0 || page>100000 || size<1 || size>100 || status!=null && !status.matches("[A-Za-z_]{1,32}"))
      throw new InvalidRequestException("Choose a valid status, page and size.");
    String owner=owner(), filter=status==null?null:status.toUpperCase(Locale.ROOT);
    Instant snapshot=clock.instant();
    // Page a shared ID index first; never load an unbounded history into the application.
    var ids=db.query("SELECT id,managed FROM (SELECT id,1 managed,created_at,CASE WHEN status='READY' AND expires_at<=? THEN 'EXPIRED' ELSE status END status FROM authorized_scheduled_payments WHERE user_id=?"
        +" UNION ALL SELECT id,0 managed,created_at,status FROM transactions WHERE record_kind='SCHEDULED_PAYMENT' AND user_id=?)"
        +" WHERE (? IS NULL OR status=?) ORDER BY created_at DESC,id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs,n)->new Object[]{rs.getString(1),rs.getInt(2)},Timestamp.from(snapshot),owner,owner,filter,filter,(long)page*size,size);
    List<Receipt> receipts=new ArrayList<>();
    for(var row:ids) {
      String id=(String)row[0];
      if ((Integer)row[1]==1) receipts.add(visible(require(id,owner,false).receipt(),snapshot));
      else receipts.add(db.queryForObject("SELECT * FROM transactions WHERE id=? AND user_id=? AND record_kind='SCHEDULED_PAYMENT'",(rs,n)->legacy(rs),id,owner));
    }
    return List.copyOf(receipts);
  }

  @Transactional(readOnly=true)
  public List<String> dueIds() {
    if(!enabled) return List.of();
    return db.queryForList("SELECT id FROM authorized_scheduled_payments WHERE status='SCHEDULED' AND authorized_at IS NOT NULL"
        +" AND authorization_version='one-time-internal-v1' AND run_at<=? ORDER BY run_at,id FETCH NEXT 50 ROWS ONLY",String.class,Timestamp.from(clock.instant()));
  }

  /** Called only by the internal worker, with each invocation receiving its own transaction. */
  @Transactional
  public void executeDue(String id) {
    if(!enabled) return;
    var owners=db.queryForList("SELECT user_id FROM authorized_scheduled_payments WHERE id=?",String.class,id);
    if(owners.isEmpty()) return;
    String owner=owners.get(0); boolean active=lockOwner(owner);
    Stored stored=require(id,owner,true);
    if(!"SCHEDULED".equals(stored.receipt().status()) || stored.runAt().isAfter(clock.instant()) || stored.receipt().authorizedAt()==null) return;
    if(db.queryForObject("SELECT COUNT(*) FROM authorized_scheduled_payments WHERE id=? AND authorization_version='one-time-internal-v1'",Long.class,id)!=1L) return;
    Route route;
    try {
      if(!active) throw new Rejected("The authorizing customer is no longer active. No payment was made.");
      route=verifyRoute(stored,true);
    } catch(Rejected rejected) { terminal(stored,"FAILED",rejected.getMessage()); return; }
    TransactionRequest request=new TransactionRequest(); request.setSourceAccountId(route.source().getId());
    request.setDestinationAccountId(route.destination().getId()); request.setAmount(new BigDecimal(stored.receipt().amount()));
    var posting=transactions.transfer(request);
    entities.flush();
    requireOne(db.update("UPDATE transactions SET operation='SCHEDULED_TRANSFER',target_id=?,merchant_name=?,category='Transfer',"
        +"payment_method='SCHEDULED_TRANSFER',user_id=?,currency_code='INR',transaction_reference=? WHERE id=?",
        id,stored.receipt().recipientName(),owner,posting.getId(),posting.getId()));
    Instant completed=clock.instant();
    requireOne(db.update("UPDATE authorized_scheduled_payments SET status='COMPLETED',transaction_reference=?,completed_at=?,updated_at=? WHERE id=? AND status='SCHEDULED'",
        posting.getId(),Timestamp.from(completed),Timestamp.from(completed),id));
    // Any exception after posting starts rolls back the schedule, balances, journal and ledger.
  }

  private Route verifyRoute(Stored stored,boolean funded) {
    if(payee(stored.receipt().payeeId(),stored.owner())!=stored.destination())
      throw new Rejected("The saved payee's account changed. Cancel this instruction and create a new schedule.");
    Route route=route(stored.source(),stored.destination(),stored.owner(),new BigDecimal(stored.receipt().amount()),funded);
    if(!Objects.equals(route.destination().getCustomer().getFullName(),stored.receipt().recipientName())
        || !Objects.equals(mask(route.destination()),stored.receipt().destinationMasked()))
      throw new Rejected("The reviewed recipient details changed. Create a new schedule after checking the payee.");
    return route;
  }
  private Route route(long sourceId,long destinationId,String owner,BigDecimal amount,boolean funded) {
    if(sourceId==destinationId) throw new Rejected("Choose different source and recipient accounts.");
    Account first=lockedAccount(Math.min(sourceId,destinationId)),second=lockedAccount(Math.max(sourceId,destinationId));
    Account source=first.getId()==sourceId?first:second,destination=first.getId()==sourceId?second:first;
    if(source.getCustomer()==null || !owner.equals(source.getCustomer().getUserId())) throw new Rejected("Choose your own active source account.");
    for(Account account:List.of(source,destination)) {
      if(account.getAccountCategory()!=AccountCategory.CUSTOMER || account.getCustomer()==null
          || !Set.of(AccountType.SAVINGS,AccountType.CURRENT).contains(account.getAccountType())
          || account.getStatus()!=AccountStatus.ACTIVE || !"INR".equals(account.getCurrencyCode()))
        throw new Rejected("Both accounts must remain active Nexa INR deposit accounts.");
    }
    if(db.queryForObject("SELECT COUNT(*) FROM customers WHERE id=? AND role='CUSTOMER' AND status='ACTIVE'",Long.class,destination.getCustomer().getId())!=1L)
      throw new Rejected("The recipient customer is no longer active.");
    if(funded && source.getBalance().compareTo(amount)<0) throw new Rejected("Insufficient funds on the scheduled date. No money was moved.");
    if(funded && destination.getBalance().add(amount).compareTo(MAX_BALANCE)>0) throw new Rejected("The recipient account cannot receive this amount.");
    return new Route(source,destination);
  }
  private Account lockedAccount(long id) {
    Account account=accounts.findLockedById(id).orElseThrow(()->new Rejected("A scheduled payment account is unavailable."));
    entities.refresh(account); return account;
  }
  private long payee(String id,String owner) {
    var rows=db.query("SELECT destination_account_id,status,destination_hash FROM transactions WHERE id=? AND record_kind='BENEFICIARY' AND user_id=? FOR UPDATE",
        (rs,n)->new Object[]{rs.getObject(1,Long.class),rs.getString(2),rs.getString(3)},id,owner);
    if(rows.isEmpty() || rows.get(0)[0]==null || !"ACTIVE".equals(rows.get(0)[1])) throw new Rejected("Choose an active saved payee linked to a Nexa account.");
    long destination=(Long)rows.get(0)[0];
    if(!hash("NEXA:"+destination).equals(rows.get(0)[2])) throw new Rejected("Link this payee's Nexa account before scheduling a payment.");
    return destination;
  }
  private String owner() {
    String owner=current.userId();
    if(db.queryForObject("SELECT COUNT(*) FROM customers WHERE user_id=? AND role='CUSTOMER' AND status='ACTIVE'",Long.class,owner)!=1L)
      throw new AccessDeniedException("An active customer is required.");
    return owner;
  }
  private boolean lockOwner(String owner) {
    var rows=db.query("SELECT status,role FROM customers WHERE user_id=? FOR UPDATE",(rs,n)->List.of(rs.getString(1),rs.getString(2)),owner);
    return !rows.isEmpty() && "ACTIVE".equals(rows.get(0).get(0)) && "CUSTOMER".equals(rows.get(0).get(1))
        && db.queryForObject("SELECT COUNT(*) FROM customer_credentials WHERE user_id=? AND credential_type='PASSWORD' AND password_hash IS NOT NULL",Long.class,owner)==1L;
  }
  private void requireActiveLockedOwner(String owner) {
    if(!lockOwner(owner)) throw new AccessDeniedException("The authorizing customer is no longer active.");
  }
  private Stored require(String id,String owner,boolean lock) { validateId(id);return find(id,owner,lock).orElseThrow(()->new ResourceNotFoundException("Scheduled payment not found.")); }
  private Optional<Stored> find(String id,String owner,boolean lock) {
    return db.query("SELECT * FROM authorized_scheduled_payments WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),(rs,n)->stored(rs),id,owner).stream().findFirst();
  }
  private Stored stored(ResultSet rs) throws SQLException {
    Receipt receipt=new Receipt(rs.getString("id"),rs.getString("recipient_name"),rs.getBigDecimal("amount").setScale(2).toPlainString(),"INR",rs.getString("due_date"),rs.getString("status"),
        Long.toString(rs.getLong("source_account_id")),rs.getString("transaction_reference"),true,rs.getString("source_name"),rs.getString("source_masked"),rs.getString("payee_id"),
        rs.getString("recipient_name"),rs.getString("destination_masked"),time(rs,"created_at"),time(rs,"expires_at"),time(rs,"authorized_at"),time(rs,"completed_at"),rs.getString("failure_reason"),ZONE.getId());
    return new Stored(receipt,rs.getString("user_id"),rs.getLong("source_account_id"),rs.getLong("destination_account_id"),rs.getString("request_fingerprint"),rs.getTimestamp("run_at").toInstant());
  }
  private Receipt legacy(ResultSet rs) throws SQLException {
    return new Receipt(rs.getString("id"),rs.getString("display_name"),rs.getBigDecimal("amount")==null?null:rs.getBigDecimal("amount").toPlainString(),rs.getString("currency_code"),rs.getString("due_at"),
        rs.getString("status"),rs.getObject("source_account_id")==null?null:Long.toString(rs.getLong("source_account_id")),rs.getString("transaction_reference"),false,
        rs.getString("source_name"),rs.getString("source_masked"),null,rs.getString("recipient_name"),rs.getString("destination_masked"),time(rs,"created_at"),null,null,null,
        "Historical schedule only. This record is not authorized for automatic execution.",ZONE.getId());
  }
  private Stored expire(Stored stored) {
    if("READY".equals(stored.receipt().status()) && !clock.instant().isBefore(Instant.parse(stored.receipt().expiresAt()))) {
      terminal(stored,"EXPIRED","The review expired. Create a new schedule review."); return require(stored.receipt().id(),stored.owner(),false);
    }
    return stored;
  }
  private Receipt visible(Receipt r) {
    return visible(r,clock.instant());
  }
  private Receipt visible(Receipt r,Instant snapshot) {
    if(!"READY".equals(r.status()) || snapshot.isBefore(Instant.parse(r.expiresAt()))) return r;
    return new Receipt(r.id(),r.payee(),r.amount(),r.currencyCode(),r.dueAt(),"EXPIRED",r.accountId(),r.reference(),true,r.sourceName(),r.sourceMasked(),r.payeeId(),r.recipientName(),r.destinationMasked(),
        r.createdAt(),r.expiresAt(),r.authorizedAt(),r.completedAt(),"The review expired. Create a new schedule review.",r.timezone());
  }
  private Receipt terminal(Stored stored,String status,String reason) {
    requireOne(db.update("UPDATE authorized_scheduled_payments SET status=?,failure_reason=?,updated_at=? WHERE id=? AND status IN ('READY','SCHEDULED')",status,reason,Timestamp.from(clock.instant()),stored.receipt().id()));
    return require(stored.receipt().id(),stored.owner(),false).receipt();
  }
  private LocalDate today() { return clock.instant().atZone(ZONE).toLocalDate(); }
  private LocalDate futureDate(String raw) {
    try { LocalDate date=LocalDate.parse(raw); if(!date.isAfter(today()) || date.isAfter(today().plusDays(365))) throw new DateTimeException("range"); return date; }
    catch(DateTimeException exception) { throw new InvalidRequestException("Choose a future date within the next 365 days, in Asia/Kolkata time."); }
  }
  private static void validate(Request request) {
    if(request==null || request.requestKey()==null || request.sourceAccountId()==null || !request.sourceAccountId().matches("[0-9]{1,19}")
        || request.payeeId()==null || !request.payeeId().matches("[A-Za-z0-9_-]{1,40}") || request.dueAt()==null || !request.dueAt().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
      throw new InvalidRequestException("Choose your account, a saved payee and a future payment date.");
    try { if(Long.parseLong(request.sourceAccountId())<=0) throw new NumberFormatException(); }
    catch(NumberFormatException exception) { throw new InvalidRequestException("Choose a valid source account."); }
    if(request.amount()==null || request.amount().scale()>2 || request.amount().signum()<=0 || request.amount().compareTo(MAX)>0)
      throw new InvalidRequestException("Enter a positive amount with at most two decimal places and thirteen integer digits.");
  }
  private static void validateId(String id) { if(id==null || !id.matches("[A-Za-z0-9_-]{1,80}")) throw new InvalidRequestException("Choose a valid schedule reference."); }
  private void requireEnabled() { if(!enabled) throw new InvalidRequestException("Scheduled payment execution is unavailable. Try again after the bank enables it."); }
  private static String fingerprint(Request r) { return hash(r.sourceAccountId()+"|"+r.payeeId()+"|"+r.amount().setScale(2).toPlainString()+"|"+r.dueAt()); }
  private static String hash(String value) { try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException(exception);} }
  private static String mask(Account account) { String number=account.getAccountNumber();return "•••• "+number.substring(Math.max(0,number.length()-4)); }
  private static String time(ResultSet rs,String column) throws SQLException {Timestamp timestamp=rs.getTimestamp(column);return timestamp==null?null:timestamp.toInstant().toString();}
  private static void requireOne(int changed) {if(changed!=1) throw new IllegalStateException("Scheduled payment update did not match its locked state.");}
}
