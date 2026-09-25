import type { BankingContent } from "./banking-content";

// Curated prompts for implemented chat operations only. Do not advertise filters
// here until the conversational query path supports them end to end.
export function getFollowUpSuggestions(type?: BankingContent["type"]): readonly string[] {
  return type === "ACCOUNTS"
    ? ["Show my latest transactions", "Move money between my accounts"]
    : ["What’s my balance?"];
}
