# Shared banking sidebar

`#messenger-history` and `.bank-sidebar` now use `SidebarBrand`, `SidebarNavigation`, `SidebarFooter`, and the same `sidebar.css` contract. `BankingIcon` is a shared component rather than an icon set owned by conversation tools.

- Neutral surface, 256px desktop width, 48px navigation targets, 20px line icons, shared typography and spacing.
- Home, Accounts, Payments, Cards and Transactions are the primary destinations. Always-visible secondary navigation includes Overview, Send money, Payees, Bills, Direct debits, Scheduled payments, Loans, and role-gated banking operations. There is no “More banking” collapse. The customer sidebar brand links to Overview.
- Conversation history, Insights and Support remain contextual conversation features. Profile/settings, security/session, and separated sign-out use the same footer in both surfaces. No account balances, full identifiers or authentication information are added to navigation.
- Active links expose `aria-current="page"`, a muted background, heavier text and a visible edge marker. The selected state remains recognizable on hover and in forced colors.
- Below 900px, both workspaces use their existing modal navigation flows. There is no ambiguous icon-only collapsed mode. Existing native dialog behavior, conversation focus containment, draft guards and session handling remain; the conversation focus loop now includes disclosure summaries and excludes hidden controls.
- Removed the separate conversation branding/navigation/footer styles, banking sidebar groups, Unicode sidebar icons, extra Ask Nexa promotion, conflicting active-state rules and two-column mobile menu. Bottom navigation reuses the primary destination metadata and SVG icons.

Verification: 65 tests pass, lint reports zero errors across 35 source files, type-check and optimized release build pass. Navigation regression tests cover complete route reachability without duplicate destinations, administrator visibility, current-page semantics, persistent secondary navigation and Hindi labels. Existing draft, payment, authentication and recovery tests still pass.

Local fixture browser checks compared both desktop rails: identical 256px width, font, padding, 48px links and 20px icons. English and Hindi navigation, secondary destinations, mobile banking dialog, Escape/focus restoration, and cancelled navigation preserving a conversation draft were exercised. Final conversation drawer checks at 320px found no horizontal overflow and kept all 20 sampled Tab transitions inside the dialog, including its disclosure. A 720×500 CSS viewport checked the reflow equivalent of halving a 1440×1000 viewport; the browser automation's zoom shortcut did not change actual page zoom, so literal 200% browser zoom remains a manual check. No browser console errors were observed. This is not a full screen-reader certification.

No backend, API, dependency, or financial execution changes were made. The release build retains existing optional-Sass and Node deprecation notices.
