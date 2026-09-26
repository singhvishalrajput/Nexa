import {useEffect, useRef, useState} from "preact/hooks";
import {CustomerProfile} from "../../services/auth";
import {BankAccount} from "../../services/banking";
import {formatDate, formatMoney} from "../../services/banking-content";
import {t} from "../../services/locale";
import {AccountApplication, IdentityType, IDENTITY_TYPES, applicationApi, applicationStatusLabel,
    identityInputHelp, identityLabel, normalizeIdentityNumber, normalizeOpeningAmount, validateDOB} from "../../services/account-applications";
import {useApplicationAction} from "../../hooks/useApplicationAction";
import {confirmNavigation, useNavigationGuard} from "../../hooks/useNavigationGuard";
import {ApplicationRecord, AvailabilityNotice} from "./AccountApplicationShared";
import {Detail, PageHeading, Panel, State, useLoad} from "./ui";

const CANCELLABLE = new Set(["DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH"]);
const FINISHED = new Set(["REJECTED", "CANCELLED", "REFUNDED"]);

/** Identifiers remain transient form state only. The bank returns masked values after saving. */
export function CustomerAccountApplications({token, profile, accounts, onAccountsChanged, embedded = false}: {
    token: string; profile: CustomerProfile; accounts: BankAccount[]; onAccountsChanged: () => void; embedded?: boolean;
}) {
    const requirements = useLoad(() => applicationApi.requirements(token), [token, profile.id]);
    const mine = useLoad(() => applicationApi.listMine(token), [token, profile.id]);
    const [selection, setSelection] = useState<{id: string; owner: string; record?: AccountApplication}>({id: "", owner: profile.id});
    const selectedId = selection.owner === profile.id ? selection.id : "";
    const selected = useLoad(() => selectedId ? applicationApi.getMine(token, selectedId) : Promise.resolve(null), [token, profile.id, selectedId]);
    const [birth, setBirth] = useState(""), [amount, setAmount] = useState("1000.00");
    const [acceptedConsent, setAcceptedConsent] = useState(""), [attempted, setAttempted] = useState(false);
    const [identityType, setIdentityType] = useState<IdentityType>("AADHAAR"), [identityNumber, setIdentityNumber] = useState("");
    const [editingIdentity, setEditingIdentity] = useState(false), [cancelConfirmed, setCancelConfirmed] = useState(false);
    const [notice, setNotice] = useState(""), [opening, setOpening] = useState(false);
    const openingTitle = useRef<HTMLHeadingElement>(null);
    const refreshedAccounts = useRef({owner: profile.id, applications: new Set<string>()});
    const formOwner = useRef(profile.id);
    function clearUnsent() {
        setBirth(""); setAmount("1000.00"); setAcceptedConsent(""); setAttempted(false);
        setIdentityNumber(""); setIdentityType("AADHAAR"); setEditingIdentity(false); setCancelConfirmed(false);
    }
    const action = useApplicationAction(result => {
        setSelection({id: result.id, owner: profile.id, record: result}); clearUnsent(); setOpening(false);
        setNotice(result.status === "DRAFT" ? "Your details are saved. Select Submit for review, then bring the original identity document to the bank. No account is open yet."
            : "The bank has recorded your request. Its current status is shown below.");
        mine.reload(); requirements.reload();
    }, label => !requirements.loading && !requirements.error && requirements.data?.applicationsAvailable === true
        && (label === "Cancel application" || requirements.data?.identityDetailsAvailable === true));
    const locked = action.busy || action.uncertain;
    const record = selection.owner === profile.id ? selection.record : undefined;
    const application = selected.data && (!record || selected.data.version >= record.version) ? selected.data : record;
    const policy = requirements.data, consent = !!policy && acceptedConsent === policy.consentVersion;
    const writesReady = policy?.applicationsAvailable === true && !requirements.loading && !requirements.error;
    const identityReady = writesReady && policy?.identityDetailsAvailable === true;
    const summaries = mine.data || [];
    const list = application && !summaries.some(item => item.id === application.id) ? [application, ...summaries]
        : summaries.map(item => application?.id === item.id && application.version > item.version ? application : item);
    const hasSavings = accounts.some(account => account.accountType === "SAVINGS");
    const hasActiveApplication = list.some(item => !FINISHED.has(item.status));
    const canStart = identityReady && !mine.loading && !mine.error && !hasSavings && !hasActiveApplication && policy?.allowedAccountTypes.includes("SAVINGS") === true;
    const mutable = !!application && ["DRAFT", "CHANGES_REQUESTED"].includes(application.status);
    const canCancel = !!application && CANCELLABLE.has(application.status) && application.receipts.length === 0;
    const identityError = attempted && !normalizeIdentityNumber(identityType, identityNumber) ? "Check the identifier format. Aadhaar requires only its last four digits; never enter the full number." : "";
    const birthError = attempted && policy ? validateDOB(birth, policy) : "";
    const amountError = attempted && !normalizeOpeningAmount(amount, policy) ? "Enter ₹1,000 to ₹1,00,00,000 with at most two decimal places." : "";
    const hasUnsentInput = Boolean(birth || amount !== "1000.00" || acceptedConsent || identityNumber || editingIdentity || (canCancel && cancelConfirmed));
    useNavigationGuard(hasUnsentInput, locked);
    const openedApplications = list.filter(item => item.status === "OPENED" && item.accountId != null);
    const openedReferences = openedApplications.map(item => `${item.id}:${item.accountId}`).join("|");
    useEffect(() => { if (opening) openingTitle.current?.focus(); }, [opening]);
    useEffect(() => {
        if (formOwner.current !== profile.id) { formOwner.current = profile.id; clearUnsent(); setOpening(false); setNotice(""); }
    }, [profile.id]);
    useEffect(() => {
        if (refreshedAccounts.current.owner !== profile.id) refreshedAccounts.current = {owner: profile.id, applications: new Set<string>()};
        if (mine.loading || mine.error || locked || hasUnsentInput) return;
        const missing = openedApplications.filter(item => !accounts.some(account => String(account.id) === String(item.accountId)) && !refreshedAccounts.current.applications.has(item.id));
        if (!missing.length) return;
        missing.forEach(item => refreshedAccounts.current.applications.add(item.id)); onAccountsChanged();
    }, [profile.id, token, openedReferences, mine.loading, mine.error, locked, hasUnsentInput, accounts, onAccountsChanged]);
    // A readiness refresh must not discard an unsent value or an uncertain retry.
    useEffect(() => {
        if (!requirements.loading && !identityReady && !locked) { setIdentityNumber(""); setEditingIdentity(false); }
    }, [requirements.loading, identityReady, locked]);
    function select(id: string) {
        if (locked || !confirmNavigation()) return;
        clearUnsent(); setOpening(false); action.clearError(); setNotice(""); setSelection({id, owner: profile.id});
    }
    function refresh() {
        if (locked) return;
        action.clearError(); setNotice(""); mine.reload(); requirements.reload(); setSelection({id: selectedId, owner: profile.id}); selected.reload();
    }
    async function create(event: Event) {
        event.preventDefault(); if (locked || !canStart || !policy) return;
        setAttempted(true); setNotice("");
        const openingAmount = normalizeOpeningAmount(amount, policy), value = normalizeIdentityNumber(identityType, identityNumber);
        if (validateDOB(birth, policy) || !openingAmount || !consent || !value || !policy.identityTypes.includes(identityType)) return;
        const payload = {accountType: "SAVINGS" as const, currencyCode: "INR" as const, dateOfBirth: birth, openingAmount,
            consentVersion: policy.consentVersion, consentAccepted: true as const, identityType, identityNumber: value};
        await action.run("Create application", requestKey => applicationApi.create(token, {...payload, requestKey}));
    }
    async function updateIdentity(event: Event) {
        event.preventDefault(); if (locked || !identityReady || !mutable || !application || !policy) return;
        setAttempted(true); const value = normalizeIdentityNumber(identityType, identityNumber);
        if (!value || !policy.identityTypes.includes(identityType)) return;
        const id = application.id, expectedVersion = application.version, kind = identityType;
        await action.run("Update identity details", requestKey => applicationApi.updateIdentity(token, id, {requestKey, expectedVersion, identityType: kind, identityNumber: value}));
    }
    async function submit() {
        if (locked || !identityReady || !mutable || !application || editingIdentity || identityNumber) return;
        const id = application.id, expectedVersion = application.version;
        await action.run("Submit for review", requestKey => applicationApi.submit(token, id, {requestKey, expectedVersion}));
    }
    async function cancel() {
        if (locked || !writesReady || !canCancel || !application || !cancelConfirmed || editingIdentity || identityNumber) return;
        const id = application.id, expectedVersion = application.version;
        await action.run("Cancel application", requestKey => applicationApi.cancel(token, id, {requestKey, expectedVersion}));
    }
    function identityFields() {
        return <><label for="application-identity-type">{t("Identity document")}</label>
            <select id="application-identity-type" value={identityType} onChange={event => { setIdentityType(event.currentTarget.value as IdentityType); setIdentityNumber(""); setAttempted(false); }}>{(policy?.identityTypes || IDENTITY_TYPES).map(kind => <option key={kind} value={kind}>{t(identityLabel(kind))}</option>)}</select>
            <label for="application-identity-number">{t(identityLabel(identityType))}</label>
            <input id="application-identity-number" type="text" inputMode={identityType === "AADHAAR" ? "numeric" : "text"} autoComplete="off" spellcheck={false}
                maxLength={40} value={identityNumber} required aria-describedby="application-identity-help application-identity-error" aria-invalid={!!identityError} onInput={event => {
                    const value = event.currentTarget.value;
                    // Never truncate a pasted full Aadhaar into an apparently valid last-four value.
                    if (identityType === "AADHAAR" && value.trim().length > 4) { event.currentTarget.value = ""; setIdentityNumber(""); setAttempted(true); }
                    else setIdentityNumber(value);
                }}/>
            <small id="application-identity-help">{t(identityInputHelp(identityType))} {t("Bring the original to the bank. Format checks are not government verification. No file upload is required.")}</small>
            {identityError && <p id="application-identity-error" class="bank-error" role="alert">{t(identityError)}</p>}</>;
    }
    return <div class="application-page">
        {embedded ? <header class="application-section-heading"><div><h2>{t("Account opening")}</h2><p>{t("Apply, attend the original-document review and follow funding in Accounts.")}</p></div><button class="bank-button secondary" type="button" onClick={refresh} disabled={locked}>{t("Refresh status")}</button></header>
            : <PageHeading title="Account applications" description="Request a savings account, complete in-person identity review and follow the opening deposit." action={<button class="bank-button secondary" type="button" onClick={refresh} disabled={locked}>{t("Refresh status")}</button>}/>}
        <State loading={requirements.loading} error={requirements.error} retry={requirements.reload}/>
        <div class="application-feedback" aria-live="polite" aria-atomic="true">{action.busy && <p role="status">{t(action.label)} — {t("Waiting for the bank. Do not submit again.")}</p>}{notice && <p role="status">{t(notice)}</p>}
            {action.error && <div class="bank-error" role="alert"><p>{t(action.error)}</p>{action.uncertain ? <button class="bank-button" type="button" disabled={action.busy || !writesReady || (action.label !== "Cancel application" && !identityReady)} onClick={() => void action.retry()}>{t("Retry the same request")}</button> : <button class="bank-button secondary" type="button" disabled={action.busy} onClick={refresh}>{t("Refresh status")}</button>}</div>}
        </div>
        {!hasSavings && !hasActiveApplication && <Panel title="Open account" className="application-create-panel" action={!opening && !mine.loading && !mine.error && (!policy || policy.allowedAccountTypes.includes("SAVINGS")) ? <button class="bank-button" type="button" disabled={locked} aria-expanded={false} aria-controls="opening-account-form" onClick={() => setOpening(true)}>{t("Open account")}</button> : undefined}>
            <p>{t("Your login profile is registered. No bank account is open yet.")}</p>
            <ol class="application-opening-steps" aria-label={t("Account opening steps")}>
                <li><strong>{t("Your request")}</strong><span>{t("Save identity details and submit for review.")}</span></li><li><strong>{t("In-person review")}</strong><span>{t("An administrator checks your original identity document.")}</span></li>
                <li><strong>{t("Deposit confirmed")}</strong><span>{t("An authorised cashier records the actual opening cash.")}</span></li><li><strong>{t("Account ready")}</strong><span>{t("The bank creates the funded account after confirming the deposit.")}</span></li>
            </ol>
            {!identityReady && <p role="status">{t("Account opening is temporarily unavailable. Do not enter identity details or hand over cash until the relevant service is ready.")}</p>}
            {mine.loading || mine.error ? <p>{t("Existing applications must be checked before starting another.")}</p> : policy && !policy.allowedAccountTypes.includes("SAVINGS") ? <p role="status">{t("A savings application is unavailable for this profile. Contact the bank.")}</p> : opening && <form id="opening-account-form" class="bank-form application-create-form" noValidate onSubmit={create}>
                <h3 ref={openingTitle} tabIndex={-1}>{t("Your account details")}</h3><p>{t("Your profile supplies the account name and contact details. Correct outdated information in Profile before applying.")}</p>
                <dl class="application-profile"><Detail label="Full name">{profile.fullName}</Detail><Detail label="Email">{profile.email}</Detail><Detail label="Phone number">{profile.phoneNumber || t("Not provided")}</Detail></dl>
                <fieldset disabled={locked || !canStart}><legend>{t("Application details")}</legend>
                    <label for="application-account-type">{t("Account type")}</label><select id="application-account-type" value="SAVINGS"><option value="SAVINGS">{t("Savings · INR")}</option><option value="CURRENT" disabled>{t("Current — organisation required")}</option></select><small>{t("One savings account per customer, including closed accounts. Organisation onboarding is not available yet.")}</small>
                    <label for="application-birth">{t("Date of birth")}</label><input id="application-birth" type="date" value={birth} min="0001-01-01" max={policy?.latestDateOfBirth} required aria-invalid={!!birthError} onInput={event => setBirth(event.currentTarget.value)}/><small>{t("You must be at least 18 on the bank's business date.")} {policy && formatDate(policy.businessDate)}</small>{birthError && <p role="alert" class="bank-error">{t(birthError)}</p>}
                    <label for="application-opening-amount">{t("Opening cash deposit (INR)")}</label><input id="application-opening-amount" type="text" inputMode="decimal" autoComplete="off" maxLength={11} value={amount} required aria-invalid={!!amountError} onInput={event => setAmount(event.currentTarget.value)}/><small>{t("Minimum ₹1,000; maximum ₹1,00,00,000 (1 crore). You may increase, not decrease below the minimum. The amount is fixed after saving.")}</small>{amountError && <p role="alert" class="bank-error">{t(amountError)}</p>}
                    {identityFields()}<p>{t("PAN and passport details are encrypted by the bank. Only Aadhaar's last four digits are retained; do not provide its full number. Identifiers are shown masked after saving.")}</p>
                    <p>{t("The opening deposit is your actual money, not an automatic credit. After approval, the cashier must receive and record the exact amount before the account opens.")}</p>{!policy?.cashReceiptAvailable && <p role="status">{t("Cash receipt recording is unavailable. Do not hand over money until the bank confirms its cashier process is ready.")}</p>}
                    {policy && <><div class="application-consent-notice"><strong>{t("In-person identity review consent")}</strong><p id="application-consent-notice">{policy.consentNotice}</p><small>{t("Consent version")}: {policy.consentVersion}</small></div><label class="application-consent"><input id="application-consent" type="checkbox" checked={consent} required aria-describedby="application-consent-notice" onChange={event => setAcceptedConsent(event.currentTarget.checked ? policy.consentVersion : "")}/><span>{t("I have read and accept this in-person identity-review consent.")}</span></label></>}
                    {attempted && !consent && <p role="alert" class="bank-error">{t("Read and accept the current consent before continuing.")}</p>}<button class="bank-button" disabled={locked || !canStart}>{t("Save application details")}</button>
                </fieldset>
            </form>}
        </Panel>}
        {(mine.loading || mine.error || list.length > 0) && <Panel title="Your applications" className="application-list-panel"><State loading={mine.loading} error={mine.error} retry={mine.reload} empty={!mine.loading && !mine.error && !list.length ? "No account applications yet" : undefined}>
            <div class="application-list">{list.map(item => <button type="button" class={"application-list-item" + (selectedId === item.id ? " selected" : "")} key={item.id} aria-pressed={selectedId === item.id} disabled={locked} onClick={() => select(item.id)}><strong>{t("Savings account application")}</strong><span>{t(applicationStatusLabel(item.status))}</span><span>{formatMoney(item.openingAmount, item.currencyCode)} · {formatDate(item.createdAt, true)}</span><small>{t("Application reference")}: {item.id}</small></button>)}</div>{list.length >= 100 && <p>{t("The latest 100 applications are shown. Contact the bank for older records.")}</p>}
        </State></Panel>}
        {selectedId && <section class="application-selected" aria-label={t("Selected application")}>
            {!application && <State loading={selected.loading} error={selected.error} retry={selected.reload}/>}{application && <>
                <ApplicationRecord application={application} token={token}/>{application.status === "OPENED" && application.accountId && <p><a class="bank-button" href={"#/accounts/" + encodeURIComponent(String(application.accountId))}>{t("View your opened account")}</a></p>}
                {application.status === "PENDING_REVIEW" && <p role="status">{t("Bring the original selected identity document to the bank for in-person administrator review. No file upload or government verification takes place here.")}</p>}
                {application.status === "APPROVED_AWAITING_CASH" && application.receipts.length === 0 && <p role="status">{t(!requirements.loading && !requirements.error && policy?.cashReceiptAvailable ? "Your in-person review is approved. Confirm the cashier process is ready before arranging the exact opening amount. No receipt is recorded yet." : "Your review is approved, but cash receipt recording is not confirmed as available. Do not hand over cash. Check readiness or contact the bank.")}</p>}
                {application.status === "CASH_RECEIVED" && <p role="status">{t("The bank recorded your cash receipt. Your account is not open until the administrator completes opening.")}</p>}{["REFUND_PENDING", "REFUNDED"].includes(application.status) && <p>{t("Follow the recorded refund status. This page cannot physically return cash; contact the bank.")}</p>}
                {mutable && <Panel title={application.status === "CHANGES_REQUESTED" ? "Respond to the review" : "Review your saved details"} className="application-identity-panel">
                    <p>{t("Check the masked identifier above. Correct it before submitting if needed; bring the matching original for in-person review. Date of birth and opening amount are fixed after saving.")}</p>
                    {!editingIdentity ? <button class="bank-button secondary" type="button" disabled={locked || !identityReady} onClick={() => { setEditingIdentity(true); setIdentityType(application.identityType); setIdentityNumber(""); setAttempted(false); }}>{t("Change identity details")}</button> : <form class="bank-form application-identity-form" noValidate onSubmit={updateIdentity}><fieldset disabled={locked || !identityReady}><legend>{t("Replace saved identity details")}</legend>{identityFields()}<button class="bank-button" disabled={locked || !identityReady}>{t("Save identity details")}</button><button class="bank-button secondary" type="button" disabled={locked} onClick={() => { setIdentityNumber(""); setEditingIdentity(false); setAttempted(false); }}>{t("Discard identity change")}</button></fieldset></form>}
                    {(editingIdentity || identityNumber) && <p>{t("Save or discard your identity change before submitting or cancelling.")}</p>}<p>{t("Submitting requests manual in-person review. It does not create an account or confirm payment.")}</p><button class="bank-button" type="button" disabled={locked || !identityReady || editingIdentity || !!identityNumber} onClick={() => void submit()}>{t(application.status === "CHANGES_REQUESTED" ? "Resubmit for review" : "Submit for review")}</button>
                </Panel>}
                {canCancel && <Panel title="Cancel this application" className="application-cancel-panel"><p>{t("No cash has been recorded for this application. Cancellation stops the request; retained records follow the bank's retention policy.")}</p><label class="application-consent"><input type="checkbox" checked={cancelConfirmed} disabled={locked || !writesReady || editingIdentity || !!identityNumber} onChange={event => setCancelConfirmed(event.currentTarget.checked)}/><span>{t("I want to cancel this account application.")}</span></label><button class="bank-button secondary" type="button" disabled={locked || !writesReady || !cancelConfirmed || editingIdentity || !!identityNumber} onClick={() => void cancel()}>{t("Cancel application")}</button></Panel>}
            </>}
        </section>}
        {hasSavings ? <p role="status">{t("You already have a savings account. A second savings account cannot be opened, including after an earlier account is closed.")}</p> : hasActiveApplication && <p role="status">{t("An existing application is active. Select it to continue; a second active application cannot be created.")}</p>}
        <details class="application-service-details"><summary>{t("Account-opening service readiness")}</summary><AvailabilityNotice requirements={requirements.loading || requirements.error ? undefined : policy} onRefresh={requirements.reload} refreshing={requirements.loading}/></details><p class="application-help">{t("Contact the bank's authorised service desk for help. Never put identity numbers or document copies in a support message.")}</p>
    </div>;
}
