/* Synthetic, read-only administration data for visual checks.
 * NEXA_UI_FIXTURE_ROLE=ADMIN node tests/messenger-fixture.cjs
 * No request reaches a database; all administrative writes are rejected.
 */
const accounts = Array.from({length: 24}, (_, i) => ({
  id: i + 1, number: String(900000014291 + i), name: i === 0 ? 'Primary account' : i === 1 ? 'Everyday account' : i === 2 ? 'Personal loan' : `Savings account ${i + 1}`,
  type: i === 2 ? 'LOAN' : i === 1 ? 'CURRENT' : 'SAVINGS', category: 'CUSTOMER', status: 'ACTIVE',
  currency: 'INR', balance: i === 2 ? '250000' : '95280', version: 1,
  customerName: 'Sample Customer', customerEmail: 'sample.customer@example.com'
}));
const requests = [{
  ID: 3, PRODUCT_ID: 'fixture-loan', ACCOUNT_NAME: 'Personal loan', FULL_NAME: 'Sample Customer',
  EMAIL: 'sample.customer@example.com', PRINCIPAL_AMOUNT: '250000', INTEREST_RATE: '12', TENURE_MONTHS: 24,
  PERIODIC_PAYMENT: '11768.37', LOAN_PURPOSE: 'Home improvements', CREATED_AT: '2026-09-23T10:00:00Z', FUNDING_ACCOUNT_ID: 1
}];
const requiredMonths = ['2026-06', '2026-07', '2026-08'];
function analytics(days) {
  const now = new Date();
  const indiaOffset = 330 * 60 * 1000;
  const dayMs = 24 * 60 * 60 * 1000;
  const endDate = new Date(now.getTime() + indiaOffset).toISOString().slice(0, 10);
  const today = Date.parse(`${endDate}T00:00:00Z`);
  const start = today - (days - 1) * dayMs;
  const money = amount => [{currencyCode: 'INR', amount: amount.toFixed(2)}];
  // Customer registrations and account openings are independent events. Eighteen customers
  // include applicants without an account; the account directory represents 24 accounts.
  const customerAges = Array.from({length: 18}, (_, i) => 2 + i * 5);
  const accountAges = accounts.map((_, i) => 2 + i * 3);
  const daily = Array.from({length: days === 1 ? 0 : days}, (_, i) => {
    const age = days - 1 - i;
    const postedPayments = age % 9 === 6 ? 0 : 4 + (age * 7 + 3) % 19;
    const paymentAmount = postedPayments * (1475 + (age * 127) % 3600);
    return {
      date: new Date(start + i * dayMs).toISOString().slice(0, 10),
      postedPayments, paymentAmounts: money(paymentAmount),
      newCustomers: customerAges.filter(value => value === age).length,
      newAccounts: accountAges.filter(value => value === age).length
    };
  });
  const hourMs = 60 * 60 * 1000;
  const from = days === 1 ? now.getTime() - dayMs : start - indiaOffset;
  const hourly = [];
  if (days === 1) {
    const firstHour = Math.floor((from + indiaOffset) / hourMs) * hourMs - indiaOffset;
    for (let hour = firstHour; hour < now.getTime(); hour += hourMs) {
      const index = hourly.length;
      const postedPayments = [0, 0, 0, 1, 0, 0, 2, 0, 3, 5, 2, 0, 1, 4, 3, 0, 7, 5, 2, 0, 3, 6, 2, 1, 1][index] || 0;
      hourly.push({startAt: new Date(Math.max(hour, from)).toISOString(),
        endAt: new Date(Math.min(hour + hourMs, now.getTime())).toISOString(),
        postedPayments, paymentAmounts: money(postedPayments * (475 + index * 127)),
        newCustomers: index === 8 ? 1 : 0, newAccounts: index === 9 ? 1 : 0});
    }
  }
  const series = days === 1 ? hourly : daily;
  const accountCounts = (keys, property) => keys.map(key => ({key, count: accounts.filter(a => a[property] === key).length}));
  const applicationCounts = {
    DRAFT: 1, PENDING_REVIEW: 2, CHANGES_REQUESTED: 1, APPROVED_AWAITING_CASH: 2,
    CASH_RECEIVED: 1, OPENED: 3, REJECTED: 2, CANCELLED: 1, REFUND_PENDING: 2, REFUNDED: 1
  };
  const sum = property => series.reduce((total, bucket) => total + bucket[property], 0);
  return {
    generatedAt: now.toISOString(), timezone: 'Asia/Kolkata',
    period: {
      days, granularity: days === 1 ? 'HOUR' : 'DAY',
      startDate: new Date(from + indiaOffset).toISOString().slice(0, 10), endDate,
      startAt: new Date(from).toISOString(), endAt: now.toISOString()
    },
    snapshot: {
      customers: 18, activeCustomers: 16, customerAccounts: accounts.length,
      activeCustomerAccounts: accounts.filter(a => a.status === 'ACTIVE').length,
      activeDepositBalances: money(accounts.filter(a => a.status === 'ACTIVE' && ['SAVINGS', 'CURRENT'].includes(a.type))
        .reduce((total, account) => total + Number(account.balance), 0)),
      accountTypes: accountCounts(['SAVINGS', 'CURRENT', 'LOAN', 'CARD', 'CASH', 'CLEARING'], 'type'),
      accountStatuses: accountCounts(['ACTIVE', 'BLOCKED', 'CLOSED'], 'status'),
      applicationStatuses: Object.entries(applicationCounts).map(([key, count]) => ({key, count})),
      applicationBacklog: ['PENDING_REVIEW', 'APPROVED_AWAITING_CASH', 'CASH_RECEIVED', 'REFUND_PENDING']
        .reduce((total, key) => total + applicationCounts[key], 0),
      pendingLoans: requests.length
    },
    activity: {
      newCustomers: sum('newCustomers'), newAccounts: sum('newAccounts'), postedPayments: sum('postedPayments'),
      paymentAmounts: money(series.reduce((total, bucket) => total + Number(bucket.paymentAmounts[0].amount), 0)), daily, hourly
    }
  };
}
module.exports = (req, url, reply) => {
  const route = url.pathname.replace('/api/v1', '');
  if (!route.startsWith('/admin/') && !route.startsWith('/loans/fixture-loan/salary-slips')) return false;
  if (req.method !== 'GET') { reply({message: 'Visual fixture is read-only.'}, 405); return true; }
  if (route === '/admin/analytics') {
    const days = Number(url.searchParams.get('days') || 30);
    if (![1, 7, 30, 90].includes(days)) reply({message: 'Choose the last 24 hours, or 7, 30 or 90 days.'}, 400);
    else reply(analytics(days));
  }
  else if (route === '/admin/accounts') reply(accounts);
  else if (route === '/admin/loans') reply(requests);
  else if (route === '/loans/fixture-loan/salary-slips') reply({requiredMonths, documents: requiredMonths.map((month, i) => ({
    id: `fixture-slip-${i}`, month, fileName: i === 0 ? 'sample_salary_statement_with_a_long_document_name_june_2026.pdf' : `sample_salary_${month}.pdf`,
    mediaType: 'application/pdf', size: 120000, uploadedAt: '2026-09-23T10:00:00Z', verifiedBy: null, verifiedAt: null
  }))});
  else {
    const match = route.match(/^\/admin\/accounts\/(\d+)(?:\/(transactions|audit))?$/);
    const account = match && accounts.find(a => a.id === Number(match[1]));
    if (!account) reply({message: 'No fixture found.'}, 404);
    else if (match[2] === 'transactions') reply({total: 2, items: [
      {ID:'TX-FIXTURE-001', CREATED_AT:'2026-09-23T10:00:00Z', OPERATION:'TRANSFER', SOURCE_ACCOUNT_ID:'1', DESTINATION_ACCOUNT_ID:'2', STATUS:'COMPLETED', AMOUNT:'1000'},
      {ID:'TX-FIXTURE-002', CREATED_AT:'2026-09-22T10:00:00Z', TRANSACTION_TYPE:'CREDIT', DESTINATION_ACCOUNT_ID:'1', STATUS:'COMPLETED', AMOUNT:'82400'}
    ]});
    else if (match[2] === 'audit') reply(account.id === 2 ? [] : [{
      ID:'AUDIT-FIXTURE-1', OPERATION:'UPDATE_ACCOUNT', ACTOR:'fixture@example.com', AUDIT_REASON:'Corrected the account display name after customer verification.',
      BEFORE_NAME:'Savings', BEFORE_STATUS:'ACTIVE', AFTER_NAME:'Primary account', AFTER_STATUS:'ACTIVE', CREATED_AT:'2026-09-23T10:00:00Z'
    }]);
    else reply({account, createdAt:'2026-09-01T10:00:00Z', updatedAt:'2026-09-23T10:00:00Z',
      terms: account.type === 'LOAN' ? {PRODUCT_STATUS:'REQUESTED', PRINCIPAL_AMOUNT:'250000', INTEREST_RATE:'12', PERIODIC_PAYMENT:'11768.37', FUNDING_ACCOUNT_ID:'1'} : {},
      relatedAccounts: accounts.filter(a => a.id !== account.id).slice(0, 3),
      mandates: [{ID:'mnd-fixture-1', DISPLAY_NAME:'Sample Utilities', STATUS:'ACTIVE', SOURCE_ACCOUNT_ID:'1', SOURCE_NAME:'Primary account', AMOUNT:'1200', CURRENCY_CODE:'INR', EFFECTIVE_DATE:'2026-09-01'}]
    });
  }
  return true;
};
