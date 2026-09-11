# Messenger UI verification

Run `npm run build`, then `node tests/messenger-fixture.cjs` and open
`http://localhost:8123`. The fixture server serves the actual built application,
with isolated fake API responses and simulated speech. It binds only to loopback;
it is not imported by application source or included as a production fallback.
All balances and authentication in this fixture are test data.

Useful fixture messages:

- Any message: short multiline balance response.
- `test long`: long Hindi/English response with line breaks and list markers.
- `test slow`: response delayed for 12 seconds, to inspect thinking and scroll behavior.
- `test failure`: first attempt fails with a technical detail; retry succeeds with the same client ID.
- Microphone: simulated final speech in the selected language. Stop, review, send or cancel; no real microphone permission is needed.

Checks performed during implementation:

- Empty conversation, short replies and consecutive exchanges.
- Hindi and mixed English/Hindi text; multiline rendering.
- 1,220-character input grows to a capped 144px height and scrolls internally.
- Sending/thinking, contextual failure, and successful retry without duplicate turns.
- Hindi voice capture, duration, stop/review, send, and the saved meaning label.
- Cancelling simulated voice leaves the message count unchanged.
- Desktop Shift+Enter preserves a newline; Enter sends. Closing history with Escape restores focus to its trigger.
- Mobile 390×844 and reduced-height 390×480 composer layouts; desktop layout.
- Scrolling up during a delayed response preserves the reading position; “New messages” returns to a zero bottom gap.
- Final TypeScript check and Oracle JET build pass. Browser console reports no errors in the fixture run.

Production integration continues to use the authenticated conversations API.
The live local API requires the conversation migration/deployment from the previous
change. These fixture checks do not validate an Oracle deployment or actual speech
recognition accuracy. Real-device keyboard and microphone testing remains useful.

Streaming, file upload, payment confirmation/execution, and transaction success/failure
are unavailable in the current backend. No simulated payment controls were added to
the production UI. The backend's current language interpreter is English-only;
Hindi display and speech capture do not imply Hindi banking understanding.

There are no configured lint, formatter or frontend unit-test scripts in package.json.
TypeScript checking and the Oracle JET build are the available project checks.
