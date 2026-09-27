# Admin analytics

The admin home (`#/admin`) provides a read-only operational dashboard. The account directory is now at `#/admin/accounts`; existing individual account, account-application and loan-request links continue to work. `#/admin/analytics` also opens the dashboard. Customer pages retain their existing layout and navigation.

## Reading the dashboard

Current customer/account counts, account types and statuses, active deposit balances and approval queues describe the current database state. Changing the activity period does not turn these current values into historical balances.

Activity defaults to **Last 24 hours**, with hourly payment counts or values shown as a line with visible points. This is an exact rolling 24-hour window, so activity before midnight remains visible. The first and current hours may be partial; the chart and data table show their actual time bounds. Lines connect the observed hourly totals without inventing or smoothing activity. Hours with no activity are included as zero.

The last 7, 30 or 90 calendar days remain available as daily bar charts, including today and zero-activity days. All hour/day labels use Asia/Kolkata. Activity stops at the server check time, with no future hours included. Refresh reloads the server aggregates; the displayed check time is not a promise of continuous updates. Current snapshot totals stay independent of the selected activity period.

- Customers are registered `CUSTOMER` records with a user ID. Active customers additionally have `status='ACTIVE'`. Administrators and orphan legacy customer rows are excluded.
- Customer accounts belong to those customer records and have `account_category='CUSTOMER'`. Account totals include their active, blocked and closed accounts; system cash and clearing accounts are excluded.
- **Active deposit balances** sum active customer savings/current accounts separately for each currency. They exclude blocked or closed deposits, system balances, cards and loans. This is not a total-liabilities or available-bank-cash metric.
- New customers and accounts use their creation timestamps within the selected period.
- Posted payment volume counts successful `PAYMENT` records that have a `POSTED` journal and touch a customer account. A transfer between two customer accounts counts once. A journal with multiple accounting legs does not multiply its amount.
- Payment dates use completion time where present, otherwise creation time. Missing payment currency is derived from the linked account; inconsistent currency records are excluded. Different currencies are never combined into a money total.
- Bills, mandates, saved payees, reviews, audit records, simulated actions, sandbox external requests and system-to-system bookkeeping transfers do not count as posted customer payments. Legitimate retained accounting history is not deleted or rewritten by the dashboard.
- Application backlog comprises pending review, approved awaiting cash, cash received awaiting opening and refund pending. Drafts and requests awaiting customer corrections are not counted as the administrator's immediate backlog; the dashboard separately notes requests awaiting customer updates. Pending loans use `product_status='PENDING_APPROVAL'`.

The money chart shows posted transaction volume, not revenue, profit, net inflow or external-bank settlement. No unsupported growth estimates or forecasts are generated.

## API and rollout

`GET /api/v1/admin/analytics?days=1` selects the rolling 24-hour view. The endpoint also accepts 7, 30 or 90, and retains its API default of 30 when the parameter is omitted. It returns the server time, timezone, exact period bounds, current snapshot and activity totals. `period.granularity` is `HOUR` or `DAY`. Hourly data uses `activity.hourly` with UTC ISO `startAt`/`endAt` bounds; daily data keeps the existing `activity.daily` date format. The unused series is empty. Partial hours can produce 25 calendar-hour buckets for an exact 24-hour range; at an exact hour boundary there are 24. Money values are decimal strings. Only an authenticated active administrator can read it; no individual identity documents, customer contact details or full account numbers are returned.

There is no new migration, seed data or provider configuration for this feature. Restart/rebuild the backend and frontend after pulling the changes. The endpoint uses database aggregation rather than loading the entire account directory into the browser. Account and loan directories are fetched when their corresponding admin pages are opened.

Automated integration checks use isolated H2 databases. Browser visual checks use a separate, explicitly synthetic read-only fixture server; fixture data is never part of production analytics or the database. A production Oracle walkthrough must be performed against the intended configured schema.

## Hourly update verification — 27 September 2026

- All 13 analytics integration checks passed in an isolated API copy, including rolling cutoffs, India midnight, partial hours, empty intervals, authorization, currency separation and read-only behavior. The 269 API source/configuration inputs match the working source.
- All 397 frontend tests passed, along with TypeScript checking, lint and an isolated release build. Its 167 source/configuration/test inputs match the working source.
- Browser checks used the separate read-only visual fixture: default hourly selection, visible line and markers, payment count/value, date labels across midnight, partial-hour rows, daily-view switching and refresh. The inspected desktop viewport had no page or chart horizontal overflow.
- No live Oracle query, migration, customer transaction or balance update was performed for this change.

## Original dashboard verification — 26 September 2026

- Backend Maven `verify` completed successfully in an isolated source copy: 556 tests, zero failures or errors, one optional model smoke test skipped. All 255 source/configuration inputs compared for that copy matched the working source. The nine analytics integration tests cover authorization, date boundaries, posted-payment filtering, currency separation, decimal precision and read-only behavior.
- Frontend: 364 tests passed; TypeScript checking, lint and an isolated production release build passed. All 116 release-build inputs matched the working source. The build reported only the existing optional Sass compiler and Node deprecation warnings.
- Browser checks passed at desktop and mobile widths using synthetic data: all three periods, payment count/value selection, the daily table, refresh and account-directory navigation. No browser errors or page-wide horizontal overflow were observed.
- No live Oracle migration, customer balance change or provider request was performed during this verification.
