# Conversational banking

Conversation handling now resolves an active payment before routing a new intent. English, Hindi and common Hinglish phrases share normalization for intent recognition and matching bill, payee and account names. The same handling applies to reviewed voice transcripts.

## Example

With an Electricity bill of INR 100 and more than one eligible source account:

| User | Assistant |
| --- | --- |
| pay electricity bill | For Electricity, which account should the money come from? |
| bill pay karo | For Electricity, which account should the money come from? |
| Everyday se | Pay INR 100 to Electricity from Everyday? |
| haan kar do | Please check the details and use Confirm payment below. |
| Selects Confirm payment | Your payment of INR 100 to Electricity has been accepted. |

The assistant keeps the selected bill and amount. If only one eligible choice remains, an affirmative follow-up selects it. An ambiguous choice produces a specific question and the matching options.

## Behavior

- Account, recipient and amount are persisted in the existing conversation workflow. A balance or history question can interrupt without erasing the payment. “Show it” refers to the latest displayed information; payment follow-ups resume the pending payment.
- Amount, account and recipient corrections retain the other details. Changing a reviewed payment cancels its old provider review and issues a new action ID. Old confirmation buttons cannot authorize the revised payment.
- The current action is identified through the latest workflow-bearing conversation turn, whose sequence is monotonic. Equal timestamps and random UUID ordering cannot select an earlier action after a correction or a switch.
- “Yes”, “do it”, “pay karo”, “haan” and “confirm” advance missing-detail collection or bring the final confirmation back into view. Only the action-bound confirmation endpoint can execute a financial capability. Retries return the existing receipt.
- “Cancel”, “nahi cancel karo” and “नहीं रद्द करो” cancel the pending action. A completed action is not posted again by a repeated acknowledgement. Negation, conditions and scheduled instructions do not silently become immediate payments.
- Bill payments, transfers to saved payees, own-account transfers, card payments, card controls and mandate cancellations share the same conversation lifecycle.
- Read follow-ups resolve against owned structured banking results and query fresh data. Selected accounts and transaction date/direction filters carry across subsequent follow-ups through the stored response metadata. Context never crosses conversation ownership boundaries.
- A model-recognized payment intention enters the same workflow. The model cannot confirm or post a payment. Unsupported or incomplete requests receive a banking question instead of workflow instructions or raw reference requirements.

## Presentation and provider behavior

Chat renders the banking response directly. Internal states such as `COLLECTING` and `UNAVAILABLE` control interaction availability but are not rendered as status labels. Confirmation buttons name the operation; choice messages use their displayed labels.

The existing provider implementations remain: own-account transfers post to the application ledger; external-provider capabilities return an acceptance receipt through the isolated provider adapter. Completion wording reports the adapter's acceptance rather than claiming external settlement or changing those integration boundaries.

## Verification

`ChatFirstIntegrationTest` exercises the full authenticated HTTP conversation flow with H2, including English/Hindi/voice follow-ups, billing and card journeys, source/recipient/amount corrections, expiry, cancellation, confirmation replay, stale confirmations, cross-user access and independent conversations. `BankingLanguageTest` covers normalization and negation. Frontend tests cover response wording, operation-specific confirmation labels and hidden internal states.

Run `mvn verify` in `apps/api`, and `npm run lint`, `npm run typecheck`, `npm test`, and `npm run build` in `apps/web`. No new database migration or external model is required for the contextual flows.
