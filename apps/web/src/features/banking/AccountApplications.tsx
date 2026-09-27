import {useEffect, useRef, useState} from "preact/hooks";
import {CustomerProfile} from "../../services/auth";
import {BankAccount} from "../../services/banking";
import {formatDate, formatMoney} from "../../services/banking-content";
import {t} from "../../services/locale";
import {AccountApplication, IdentityType, IDENTITY_TYPES, applicationApi, applicationStatusLabel,
    identityInputHelp, identityLabel, normalizeIdentityNumber, normalizeOpeningAmount, normalizeApplicationPhone, validateDOB} from "../../services/account-applications";
import {useApplicationAction} from "../../hooks/useApplicationAction";
import {confirmNavigation, useNavigationGuard} from "../../hooks/useNavigationGuard";
import {ApplicationRecord} from "./AccountApplicationShared";
import {Detail, PageHeading, Panel, State, useLoad} from "./ui";

const CANCELLABLE = new Set(["DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH"]);
const FINISHED = new Set(["REJECTED", "CANCELLED", "REFUNDED"]);

/** Identifiers remain transient form state only. The bank returns masked values after saving. */
export function CustomerAccountApplications({token, profile, accounts, onAccountsChanged, embedded = false, initiallyOpen = false, disabled = false, onSubmissionLocked}: {
    token: string; profile: CustomerProfile; accounts: BankAccount[]; onAccountsChanged: () => void; embedded?: boolean;
    initiallyOpen?: boolean; disabled?: boolean; onSubmissionLocked?: (locked: boolean) => void;
}) {
    const requirements = useLoad(() => applicationApi.requirements(token), [token, profile.id]);
    const mine = useLoad(() => applicationApi.listMine(token), [token, profile.id]);
    const [selection, setSelection] = useState<{id: string; owner: string; record?: AccountApplication}>({id: "", owner: profile.id});
    const selectedId = selection.owner === profile.id ? selection.id : "";
    const selected = useLoad(() => selectedId ? applicationApi.getMine(token, selectedId) : Promise.resolve(null), [token, profile.id, selectedId]);
    const [birth, setBirth] = useState(""), [amount, setAmount] = useState("1000.00");
    const [phone, setPhone] = useState(profile.phoneNumber || "");
    const [replaceIdentity, setReplaceIdentity] = useState(false);
    const [acceptedConsent, setAcceptedConsent] = useState(""), [attempted, setAttempted] = useState(false);
    const [identityType, setIdentityType] = useState<IdentityType>("AADHAAR"), [identityNumber, setIdentityNumber] = useState("");
    const [editingDetails, setEditingDetails] = useState(false), [cancelConfirmed, setCancelConfirmed] = useState(false);
    const [notice, setNotice] = useState(""), [opening, setOpening] = useState(false);
    const [validationAttempt, setValidationAttempt] = useState(0);
    const [errorLocation, setErrorLocation] = useState<"create" | "update" | null>(null);
    const [requestCycle, setRequestCycle] = useState(0);
    const openingTitle = useRef<HTMLHeadingElement>(null);
    const validationSummary = useRef<HTMLDivElement>(null);
    const saveError = useRef<HTMLDivElement>(null);
    const initialOpening = useRef({owner: profile.id, opened: false});
    const submissionCallback = useRef(onSubmissionLocked);
    submissionCallback.current = onSubmissionLocked;
    const refreshedAccounts = useRef({owner: profile.id, applications: new Set<string>()});
    const formOwner = useRef(profile.id);
    function clearUnsent() {
        setBirth(""); setAmount("1000.00"); setPhone(profile.phoneNumber || ""); setReplaceIdentity(false); setAcceptedConsent(""); setAttempted(false);
        setIdentityNumber(""); setIdentityType("AADHAAR"); setEditingDetails(false); setCancelConfirmed(false); setValidationAttempt(0);
    }
    const action = useApplicationAction(result => {
        setSelection({id: result.id, owner: profile.id, record: result}); clearUnsent(); setOpening(false);
        setNotice(result.status === "DRAFT" ? "Your details are saved. Select Submit for review, then bring the original identity document to the bank. No account is open yet."
            : "The bank has recorded your request. Its current status is shown below.");
        mine.reload(); requirements.reload();
    }, label => !requirements.loading && !requirements.error && requirements.data?.applicationsAvailable === true
        && (label === "Cancel application" || requirements.data?.identityDetailsAvailable === true));
    const locked = action.busy || action.uncertain || disabled;
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
    const createFormVisible = opening && !hasSavings && !hasActiveApplication && !mine.loading && !mine.error && (!policy || policy.allowedAccountTypes.includes("SAVINGS"));
    const saveErrorLocation = errorLocation === "create" && createFormVisible ? "create"
        : errorLocation === "update" && selectedId && mutable && editingDetails ? "update" : null;
    const canCancel = !!application && CANCELLABLE.has(application.status) && application.receipts.length === 0;
    const identityError = attempted && (!editingDetails || replaceIdentity) && !normalizeIdentityNumber(identityType, identityNumber) ? "Check the identifier format. Aadhaar requires only its last four digits; never enter the full number." : "";
    const birthError = attempted && policy ? validateDOB(birth, policy) : "";
    const amountError = attempted && !normalizeOpeningAmount(amount, policy) ? "Enter ₹1,000 to ₹1,00,00,000 with at most two decimal places." : "";
    const phoneError = attempted && !normalizeApplicationPhone(phone) ? "Enter a valid phone number with 10 to 15 digits, optionally starting with +." : "";
    const identityTypeError = attempted && (!editingDetails || replaceIdentity) && policy && !policy.identityTypes.includes(identityType) ? "Choose an available identity document." : "";
    const consentError = attempted && !editingDetails && !consent ? "Read and accept the current consent before continuing." : "";
    const validationErrors = [
        {field: "application-phone", label: "Phone number", message: phoneError},
        {field: "application-birth", label: "Date of birth", message: birthError},
        {field: "application-opening-amount", label: "Opening cash deposit (INR)", message: amountError},
        {field: "application-identity-type", label: "Identity document", message: identityTypeError},
        {field: "application-identity-number", label: identityLabel(identityType), message: identityError},
        {field: "application-consent", label: "In-person identity review consent", message: consentError}
    ].filter(error => error.message);
    const hasUnsentInput = Boolean(phone !== (profile.phoneNumber || "") || birth || amount !== "1000.00" || acceptedConsent || identityNumber || editingDetails || (canCancel && cancelConfirmed));
    useNavigationGuard(hasUnsentInput, locked);
    const openedApplications = list.filter(item => item.status === "OPENED" && item.accountId != null);
    const openedReferences = openedApplications.map(item => `${item.id}:${item.accountId}`).join("|");
    useEffect(() => { if (opening) openingTitle.current?.focus(); }, [opening]);
    useEffect(() => {
        if (initialOpening.current.owner !== profile.id) initialOpening.current = {owner: profile.id, opened: false};
        if (initiallyOpen && canStart && !locked && !initialOpening.current.opened) {
            initialOpening.current.opened = true; setOpening(true);
        }
    }, [initiallyOpen, canStart, locked, profile.id]);
    useEffect(() => {
        submissionCallback.current?.(action.busy || action.uncertain);
    }, [action.busy, action.uncertain, requestCycle]);
    useEffect(() => {
        if (!action.error || action.busy || !saveErrorLocation) return;
        saveError.current?.focus({preventScroll: true});
        saveError.current?.scrollIntoView({block: "center", behavior: "auto"});
    }, [action.error, action.busy, saveErrorLocation, requestCycle]);
    useEffect(() => {
        if (!validationAttempt) return;
        validationSummary.current?.focus({preventScroll: true});
        validationSummary.current?.scrollIntoView({block: "start", behavior: "auto"});
    }, [validationAttempt]);
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
        if (!requirements.loading && !identityReady && !locked) { setIdentityNumber(""); setReplaceIdentity(false); }
    }, [requirements.loading, identityReady, locked]);
    function select(id: string) {
        if (locked || !confirmNavigation()) return;
        clearUnsent(); setOpening(false); action.clearError(); setNotice(""); setSelection({id, owner: profile.id});
    }
    function refresh() {
        if (action.busy || disabled) return;
        requirements.reload();
        if (locked) return;
        mine.reload();
        if (editingDetails || (opening && hasUnsentInput)) return;
        action.clearError(); setNotice(""); setSelection({id: selectedId, owner: profile.id}); selected.reload();
    }
    async function runAction(location: "create" | "update" | null, label: string, send: (key: string) => Promise<AccountApplication>) {
        setErrorLocation(location);
        // Inform an embedding chat before a second action can replace this form or its retry closure.
        submissionCallback.current?.(true);
        try { await action.run(label, send); }
        finally { setRequestCycle(cycle => cycle + 1); }
    }
    async function create(event: Event) {
        event.preventDefault(); if (locked || !canStart || !policy) return;
        setAttempted(true); setNotice("");
        const openingAmount = normalizeOpeningAmount(amount, policy), value = normalizeIdentityNumber(identityType, identityNumber);
        if (validateDOB(birth, policy) || !openingAmount || !normalizeApplicationPhone(phone) || !consent || !value || !policy.identityTypes.includes(identityType)) {
            setValidationAttempt(attempt => attempt + 1); return;
        }
        const payload = {accountType: "SAVINGS" as const, currencyCode: "INR" as const, dateOfBirth: birth, openingAmount, phoneNumber: normalizeApplicationPhone(phone)!,
            consentVersion: policy.consentVersion, consentAccepted: true as const, identityType, identityNumber: value};
        await runAction("create", "Create application", requestKey => applicationApi.create(token, {...payload, requestKey}));
    }
    function beginEdit() {
        if (locked || !mutable || !application) return;
        action.clearError();
        setEditingDetails(true); setBirth(application.dateOfBirth); setAmount(application.openingAmount);
        setPhone(application.phoneNumber || profile.phoneNumber || ""); setIdentityType(application.identityType);
        setIdentityNumber(""); setReplaceIdentity(false); setAttempted(false); setValidationAttempt(0);
    }
    async function updateDetails(event: Event) {
        event.preventDefault(); if (locked || !identityReady || !mutable || !application || !policy) return;
        setAttempted(true);
        const openingAmount = normalizeOpeningAmount(amount, policy), phoneNumber = normalizeApplicationPhone(phone);
        const value = replaceIdentity ? normalizeIdentityNumber(identityType, identityNumber) : null;
        if (validateDOB(birth, policy) || !openingAmount || !phoneNumber || (replaceIdentity && (!value || !policy.identityTypes.includes(identityType)))) {
            setValidationAttempt(attempt => attempt + 1); return;
        }
        const id = application.id, payload = {expectedVersion: application.version, dateOfBirth: birth, openingAmount, phoneNumber,
            ...(replaceIdentity ? {identityType, identityNumber: value!} : {})};
        await runAction("update", "Update application details", requestKey => applicationApi.updateDetails(token, id, {...payload, requestKey}));
    }
    async function submit() {
        if (locked || !identityReady || !mutable || !application || editingDetails || identityNumber || !normalizeApplicationPhone(application.phoneNumber || "")) return;
        const id = application.id, expectedVersion = application.version;
        await runAction(null, "Submit for review", requestKey => applicationApi.submit(token, id, {requestKey, expectedVersion}));
    }
    async function cancel() {
        if (locked || !writesReady || !canCancel || !application || !cancelConfirmed || editingDetails || identityNumber) return;
        const id = application.id, expectedVersion = application.version;
        await runAction(null, "Cancel application", requestKey => applicationApi.cancel(token, id, {requestKey, expectedVersion}));
    }
    function actionError(atSave = false) {
        return action.error && <div id={atSave ? "application-save-error" : undefined} ref={atSave ? saveError : undefined} class="bank-error" role="alert" tabIndex={atSave ? -1 : undefined}>
            <p>{t(action.error)}</p>{action.uncertain
                ? <button class="bank-button" type="button" disabled={action.busy || disabled || !writesReady || (action.label !== "Cancel application" && !identityReady)} onClick={() => { if (!disabled) void action.retry(); }}>{t("Retry the same request")}</button>
                : <button class="bank-button secondary" type="button" disabled={action.busy || disabled} onClick={refresh}>{t("Refresh status")}</button>}
        </div>;
    }
    function requiredLabel() { return <span class="application-required">({t("Required")})</span>; }
    function focusInvalidField(id: string) {
        const field = document.getElementById(id);
        field?.focus({preventScroll: true});
        field?.scrollIntoView({block: "center", behavior: "auto"});
    }
    function formValidation() {
        return validationErrors.length > 0 && <div id="application-validation-summary" ref={validationSummary} class="application-validation-summary" role="alert" tabIndex={-1} aria-labelledby="application-validation-title">
            <strong id="application-validation-title">{t("Check your application details before saving.")}</strong>
            <p>{t("Your entries are still here. Select a field below to correct it.")}</p>
            <ul>{validationErrors.map(error => <li key={error.field}><button type="button" class="application-validation-link" onClick={() => focusInvalidField(error.field)}>{t(error.label)}: {t(error.message)}</button></li>)}</ul>
        </div>;
    }
    function applicationFields() {
        return <>
            <label for="application-phone">{t("Phone number")} {requiredLabel()}</label><input id="application-phone" type="tel" autoComplete="tel" maxLength={24} value={phone} required aria-invalid={!!phoneError} aria-describedby={"application-phone-help" + (phoneError ? " application-phone-error" : "")} onInput={event => setPhone(event.currentTarget.value)}/>
            <small id="application-phone-help">{t("Required for account opening. Enter 10 to 15 digits, with an optional country code starting with +.")}</small>{phoneError && <p id="application-phone-error" class="bank-error">{t(phoneError)}</p>}
            <label for="application-birth">{t("Date of birth")} {requiredLabel()}</label><input id="application-birth" type="date" value={birth} min="0001-01-01" max={policy?.latestDateOfBirth} required aria-invalid={!!birthError} aria-describedby={"application-birth-help" + (birthError ? " application-birth-error" : "")} onInput={event => setBirth(event.currentTarget.value)}/><small id="application-birth-help">{t("You must be at least 18 to open an account.")}</small>{birthError && <p id="application-birth-error" class="bank-error">{t(birthError)}</p>}
            <label for="application-opening-amount">{t("Opening cash deposit (INR)")} {requiredLabel()}</label><input id="application-opening-amount" type="text" inputMode="decimal" autoComplete="off" maxLength={11} value={amount} required aria-invalid={!!amountError} aria-describedby={"application-opening-amount-help" + (amountError ? " application-opening-amount-error" : "")} onInput={event => setAmount(event.currentTarget.value)}/><small id="application-opening-amount-help">{t("Minimum ₹1,000; maximum ₹1,00,00,000 (1 crore). You can change this amount before submitting or when corrections are requested.")}</small>{amountError && <p id="application-opening-amount-error" class="bank-error">{t(amountError)}</p>}
        </>;
    }
    function identityFields() {
        return <><label for="application-identity-type">{t("Identity document")} {requiredLabel()}</label>
            <select id="application-identity-type" value={identityType} required aria-invalid={!!identityTypeError} aria-describedby={identityTypeError ? "application-identity-type-error" : undefined} onChange={event => { setIdentityType(event.currentTarget.value as IdentityType); setIdentityNumber(""); setAttempted(false); }}>{(policy?.identityTypes || IDENTITY_TYPES).map(kind => <option key={kind} value={kind}>{t(identityLabel(kind))}</option>)}</select>
            {identityTypeError && <p id="application-identity-type-error" class="bank-error">{t(identityTypeError)}</p>}
            <label for="application-identity-number">{t(identityLabel(identityType))} {requiredLabel()}</label>
            <input id="application-identity-number" type="text" inputMode={identityType === "AADHAAR" ? "numeric" : "text"} autoComplete="off" spellcheck={false}
                maxLength={40} value={identityNumber} required aria-describedby={"application-identity-help" + (identityError ? " application-identity-error" : "")} aria-invalid={!!identityError} onInput={event => {
                    const value = event.currentTarget.value;
                    // Never truncate a pasted full Aadhaar into an apparently valid last-four value.
                    if (identityType === "AADHAAR" && value.trim().length > 4) { event.currentTarget.value = ""; setIdentityNumber(""); setAttempted(true); }
                    else setIdentityNumber(value);
                }}/>
            <small id="application-identity-help">{t(identityInputHelp(identityType))} {t("Bring the original to the bank. Format checks are not government verification. No file upload is required.")}</small>
            {identityError && <p id="application-identity-error" class="bank-error">{t(identityError)}</p>}</>;
    }
    return <div class="application-page">
        {embedded ? <header class="application-section-heading"><div><h2>{t("Account opening")}</h2><p>{t("Apply, attend the original-document review and follow funding in Accounts.")}</p></div><button class="bank-button secondary" type="button" onClick={refresh} disabled={action.busy || disabled || requirements.loading}>{t("Refresh status")}</button></header>
            : <PageHeading title="Account applications" description="Request a savings account, complete in-person identity review and follow the opening deposit." action={<button class="bank-button secondary" type="button" onClick={refresh} disabled={action.busy || disabled || requirements.loading}>{t("Refresh status")}</button>}/>}
        {(requirements.error || (mutable && !requirements.loading && !identityReady)) && <p class="bank-error" role="alert">{t("Account opening is temporarily unavailable. Refresh status to try again.")}</p>}
        <div class="application-feedback" aria-live="polite" aria-atomic="true">{action.busy && <p role="status">{t(action.label)} — {t("Waiting for the bank. Do not submit again.")}</p>}{notice && <p role="status">{t(notice)}</p>}
            {!saveErrorLocation && actionError()}
        </div>
        {!hasSavings && !hasActiveApplication && <Panel title="Open account" className="application-create-panel" action={!opening && !mine.loading && !mine.error && (!policy || policy.allowedAccountTypes.includes("SAVINGS")) ? <button class="bank-button" type="button" disabled={locked} aria-expanded={false} aria-controls="opening-account-form" onClick={() => setOpening(true)}>{t("Open account")}</button> : undefined}>
            <p>{t("Your login profile is registered. No bank account is open yet.")}</p>
            <ol class="application-opening-steps" aria-label={t("Account opening steps")}>
                <li><strong>{t("Your request")}</strong><span>{t("Save identity details and submit for review.")}</span></li><li><strong>{t("In-person review")}</strong><span>{t("An administrator checks your original identity document.")}</span></li>
                <li><strong>{t("Deposit confirmed")}</strong><span>{t("An authorised cashier records the actual opening cash.")}</span></li><li><strong>{t("Account ready")}</strong><span>{t("The bank creates the funded account after confirming the deposit.")}</span></li>
            </ol>
            {!identityReady && <p role="status">{t("Account opening is temporarily unavailable. Do not enter identity details or hand over cash until the relevant service is ready.")}</p>}
            {mine.loading || mine.error ? <p>{t("Existing applications must be checked before starting another.")}</p> : policy && !policy.allowedAccountTypes.includes("SAVINGS") ? <p role="status">{t("A savings application is unavailable for this profile. Contact the bank.")}</p> : opening && <form id="opening-account-form" class="bank-form application-create-form" noValidate onSubmit={create}>
                <h3 ref={openingTitle} tabIndex={-1}>{t("Your account details")}</h3><p>{t("Your profile supplies your name and email. Check your phone number and application details below.")}</p>
                <dl class="application-profile"><Detail label="Full name">{profile.fullName}</Detail><Detail label="Email">{profile.email}</Detail></dl>
                <fieldset disabled={locked || !canStart}><legend>{t("Application details")}</legend>
                    <label for="application-account-type">{t("Account type")}</label><select id="application-account-type" value="SAVINGS"><option value="SAVINGS">{t("Savings · INR")}</option><option value="CURRENT" disabled>{t("Current — organisation required")}</option></select><small>{t("One savings account per customer, including closed accounts. Organisation onboarding is not available yet.")}</small>
                    {applicationFields()}
                    {identityFields()}<p>{t("PAN and passport details are encrypted by the bank. Only Aadhaar's last four digits are retained; do not provide its full number. Identifiers are shown masked after saving.")}</p>
                    <p>{t("The opening deposit is your actual money, not an automatic credit. After approval, the cashier must receive and record the exact amount before the account opens.")}</p>{!policy?.cashReceiptAvailable && <p role="status">{t("Cash receipt recording is unavailable. Do not hand over money until the bank confirms its cashier process is ready.")}</p>}
                    {policy && <><div class="application-consent-notice"><strong>{t("In-person identity review consent")}</strong><p id="application-consent-notice">{policy.consentNotice}</p><small>{t("Consent version")}: {policy.consentVersion}</small></div><label class="application-consent"><input id="application-consent" type="checkbox" checked={consent} required aria-invalid={!!consentError} aria-describedby={"application-consent-notice" + (consentError ? " application-consent-error" : "")} onChange={event => setAcceptedConsent(event.currentTarget.checked ? policy.consentVersion : "")}/><span>{t("I have read and accept this in-person identity-review consent.")} {requiredLabel()}</span></label></>}
                    {consentError && <p id="application-consent-error" class="bank-error">{t(consentError)}</p>}{formValidation()}<button class="bank-button" disabled={locked || !canStart}>{t("Save application details")}</button>
                </fieldset>{saveErrorLocation === "create" && actionError(true)}
            </form>}
        </Panel>}
        {(mine.loading || mine.error || list.length > 0) && <Panel title="Your applications" className="application-list-panel"><State loading={mine.loading} error={mine.error} retry={mine.reload} empty={!mine.loading && !mine.error && !list.length ? "No account applications yet" : undefined}>
            <div class="application-list">{list.map(item => <button type="button" class={"application-list-item" + (selectedId === item.id ? " selected" : "")} key={item.id} aria-pressed={selectedId === item.id} disabled={locked} onClick={() => select(item.id)}><strong>{t("Savings account application")}</strong><span>{t(applicationStatusLabel(item.status))}</span><span>{formatMoney(item.openingAmount, item.currencyCode)} · {formatDate(item.createdAt, true)}</span><small>{t("Application reference")}: {item.id}</small></button>)}</div>{list.length >= 100 && <p>{t("The latest 100 applications are shown. Contact the bank for older records.")}</p>}
        </State></Panel>}
        {selectedId && <section class="application-selected" aria-label={t("Selected application")}>
            {!application && <State loading={selected.loading} error={selected.error} retry={selected.reload}/>}{application && <>
                <ApplicationRecord application={application} token={token}/>{application.status === "OPENED" && application.accountId && <p><a class="bank-button" href={"#/accounts/" + encodeURIComponent(String(application.accountId))}>{t("View your opened account")}</a></p>}
                {application.status === "PENDING_REVIEW" && <p role="status">{t("Bring the original selected identity document to the bank for in-person administrator review. No file upload or government verification takes place here.")}</p>}
                {application.status === "APPROVED_AWAITING_CASH" && application.receipts.length === 0 && <p role="status">{t(!requirements.loading && !requirements.error && policy?.cashReceiptAvailable ? "Your application is approved. Arrange the exact opening deposit with the bank." : "Your application is approved, but deposits are temporarily unavailable. Contact the bank before handing over cash.")}</p>}
                {application.status === "CASH_RECEIVED" && <p role="status">{t("Cash received. Your deposit receipt is shown above.")}</p>}{["REFUND_PENDING", "REFUNDED"].includes(application.status) && <p>{t("Follow the recorded refund status. This page cannot physically return cash; contact the bank.")}</p>}
                {mutable && <Panel title={application.status === "CHANGES_REQUESTED" ? "Respond to the review" : "Review your saved details"} className="application-identity-panel">
                    <p>{t("Review your phone number, date of birth, opening deposit and identity details before submitting.")}</p>{!normalizeApplicationPhone(application.phoneNumber || "") && <p class="bank-error" role="status">{t("Add a valid phone number using Review and update before submitting.")}</p>}
                    {!editingDetails ? <button class="bank-button secondary" type="button" disabled={locked || !identityReady} onClick={beginEdit}>{t("Review and update")}</button> : <form class="bank-form application-details-form" noValidate onSubmit={updateDetails}><fieldset disabled={locked || !identityReady}><legend>{t("Update application details")}</legend>
                        {applicationFields()}
                        <label class="application-check"><input id="application-replace-identity" type="checkbox" checked={replaceIdentity} onChange={event => { setReplaceIdentity(event.currentTarget.checked); setIdentityNumber(""); setIdentityType(application.identityType); setAttempted(false); }}/>{t("Change identity document details")}</label>
                        {replaceIdentity ? identityFields() : <p>{t("Saved identity")}: {t(identityLabel(application.identityType))} · {application.identityMasked}</p>}
                        {formValidation()}<button class="bank-button" disabled={locked || !identityReady}>{t("Save changes")}</button><button class="bank-button secondary" type="button" disabled={locked} onClick={() => { if (!locked) { clearUnsent(); action.clearError(); } }}>{t("Discard changes")}</button>
                    </fieldset>{saveErrorLocation === "update" && actionError(true)}</form>}
                    {editingDetails && <p>{t("Save or discard your changes before submitting or cancelling.")}</p>}<p>{t("Submit your application for review once the details are correct.")}</p><button class="bank-button" type="button" disabled={locked || !identityReady || editingDetails || !!identityNumber || !normalizeApplicationPhone(application.phoneNumber || "")} onClick={() => void submit()}>{t(application.status === "CHANGES_REQUESTED" ? "Resubmit for review" : "Submit for review")}</button>
                </Panel>}
                {canCancel && <Panel title="Cancel this application" className="application-cancel-panel"><p>{t("No cash has been recorded for this application. Cancellation stops the request; retained records follow the bank's retention policy.")}</p><label class="application-consent"><input type="checkbox" checked={cancelConfirmed} disabled={locked || !writesReady || editingDetails || !!identityNumber} onChange={event => setCancelConfirmed(event.currentTarget.checked)}/><span>{t("I want to cancel this account application.")}</span></label><button class="bank-button secondary" type="button" disabled={locked || !writesReady || !cancelConfirmed || editingDetails || !!identityNumber} onClick={() => void cancel()}>{t("Cancel application")}</button></Panel>}
            </>}
        </section>}
        {hasSavings ? <p role="status">{t("You already have a savings account. A second savings account cannot be opened, including after an earlier account is closed.")}</p> : hasActiveApplication && <p role="status">{t("An existing application is active. Select it to continue; a second active application cannot be created.")}</p>}
        <p class="application-help">{t("Contact the bank's authorised service desk for help. Never put identity numbers or document copies in a support message.")}</p>
    </div>;
}
