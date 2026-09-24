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
module.exports = (req, url, reply) => {
  const route = url.pathname.replace('/api/v1', '');
  if (!route.startsWith('/admin/') && !route.startsWith('/loans/fixture-loan/salary-slips')) return false;
  if (req.method !== 'GET') { reply({message: 'Visual fixture is read-only.'}, 405); return true; }
  if (route === '/admin/accounts') reply(accounts);
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
