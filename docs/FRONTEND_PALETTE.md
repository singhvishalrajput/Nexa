# Frontend palette and sidebar audit

Both `bank-sidebar` and `messenger-history` use the shared `SidebarNavigation` components and `sidebar.css`, including their mobile navigation surfaces. See `SIDEBAR_NAVIGATION.md` for the unified navigation design and verification.

The inconsistency came from the cascade: `banking.css` supplied an older green/tan palette, while `usability.css` repeatedly replaced it with teal, amber actions, then a navy sidebar and blue gradient hero. A later generic product rule also gave payment cards a white surface while retaining inverse text. Auth and profile action hover rules could assign identical foreground/background colors.

The frontend cleanup consolidated the conventional banking refinements from `usability.css` into `banking.css`; the separate override file is no longer loaded.

## Ownership

- `tokens.css` owns all literal colors, overlays, and shadows. Restrained teal actions, neutral white surfaces, warm neutral page backgrounds, and a solid deep teal inverse surface replace the competing palettes.
- `sidebar.css` owns the shared light sidebar layout, 256px rail width, borders, spacing, icons and navigation states.
- `controls.css` owns shared primary/secondary action states, disabled colors, focus indicators, and navigation interaction states across banking and conversation components. Old action color declarations were removed from component styles.
- The generic product surface rule excludes payment cards. Inverse labels use explicit foreground tokens instead of opacity. Cards no longer alternate between incompatible palettes.

Use `nexa-border` for decorative separators; use `nexa-border-control` for boundaries required to identify fields. Use `nexa-text`, `nexa-text-secondary`, and `nexa-text-muted` for hierarchy; Consumers now use `nexa-text-muted` directly; the obsolete `nexa-text-soft` alias was removed. Pair primary actions with `nexa-on-action`, and inverse surfaces with `nexa-on-action` or `nexa-on-action-soft`. Success, warning, and error each have their own foreground/background pair.

## Verification

`tests/palette.test.cjs` checks WCAG relative luminance: normal text at least 4.5:1, control boundaries and focus at least 3:1. It covers light surfaces, action hover/pressed states, semantic statuses, selected navigation, disabled labels, and inverse text. A regression check rejects component-specific literal colors and undefined shared tokens.

Local fixture browser checks covered desktop conversation, accounts, overview, cards, and transfer forms; mobile overview, navigation dialog, and sign-in; and keyboard focus on form controls. Selected navigation uses a persistent edge marker and weight; auth tabs use an underline; transaction statuses retain explicit labels and direction text. Focus is a 3px outline, with an inverse variant on dark panels and system colors in forced-colors mode.

All 61 frontend tests, lint, and TypeScript checks pass. The release build passes. These checks target WCAG AA contrast; they are not a full WCAG 2.2 conformance audit. The preview uses fixture accounts and does not move real funds.
