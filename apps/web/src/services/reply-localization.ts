import { getLocale, t, type Locale } from "./locale";

// Translate only known application copy, never persisted messages or payment data.
// Exact matches preserve warnings and qualifications instead of replacing them with
// a generic summary based on the response type.
const hindiReplies: Record<string, string> = {
  "Here are your available balances.": "ये आपके खातों में उपलब्ध बैलेंस हैं।",
  "You do not have a matching Nexa account yet.": "अभी आपका कोई नेक्सा खाता इस अनुरोध से मेल नहीं खाता।",
  "You do not have a Nexa account yet.": "अभी आपका कोई नेक्सा खाता नहीं है।",
  "Historical balances are not available. Please ask for your current balance.": "पुराने बैलेंस की जानकारी उपलब्ध नहीं है। कृपया अपना वर्तमान बैलेंस पूछें।",
  "Here are your latest transactions.": "ये आपके हाल के लेन-देन हैं।",
  "Here are the transaction details.": "यह लेन-देन का विवरण है।",
  "Here are your mandates.": "ये आपके ऑटो डेबिट निर्देश हैं।",
  "Here are the mandate details.": "यह ऑटो डेबिट निर्देश का विवरण है।",
  "Here are your bills and their payment statuses.": "ये आपके बिल और उनके भुगतान की स्थिति हैं।",
  "Here are recorded bills due in the requested period (from the first 100 bills).": "पहले 100 दर्ज बिलों में से, ये बिल माँगी गई अवधि में देय हैं।",
  "Here are the bill details.": "यह बिल का विवरण है।",
  "Here are your credit cards.": "ये आपके क्रेडिट कार्ड हैं।",
  "Here are your cards.": "ये आपके कार्ड हैं।",
  "There are no cards to show.": "दिखाने के लिए कोई कार्ड नहीं है।",
  "Here are your beneficiaries.": "ये आपके सहेजे गए प्राप्तकर्ता हैं।",
  "Here are your scheduled payments.": "ये आपके निर्धारित भुगतान हैं।",
  "Here are your loans and EMIs.": "ये आपके ऋण और उनकी किस्तें हैं।",
  "Here are the loan details.": "यह ऋण का विवरण है।",
  "Here is the recorded transfer status.": "यह दर्ज किए गए ट्रांसफ़र की स्थिति है।",
  "Which item would you like to see? Choose one from the list.": "आप किसका विवरण देखना चाहते हैं? सूची में से एक चुनें।",
  "Which transaction would you like to see?": "आप किस लेन-देन का विवरण देखना चाहते हैं?",
  "What would you like me to help with?": "आपको किस विषय में मदद चाहिए?",
  "Would you like this month or last month?": "आप इस महीने की जानकारी चाहते हैं या पिछले महीने की?",
  "Which bill would you like to pay?": "आप किस बिल का भुगतान करना चाहते हैं?",
  "Which card would you like to pay?": "आप किस कार्ड का भुगतान करना चाहते हैं?",
  "Which direct debit would you like to cancel?": "आप कौन-सा ऑटो डेबिट रद्द करना चाहते हैं?",
  "Who would you like to pay?": "आप किसे भुगतान करना चाहते हैं?",
  "Which account should the money come from?": "पैसे किस खाते से भेजने हैं?",
  "Which account should receive the money?": "पैसे किस खाते में भेजने हैं?",
  "Which loan would you like to repay?": "आप किस ऋण का भुगतान करना चाहते हैं?",
  "Which card would you like to use?": "आप कौन-सा कार्ड इस्तेमाल करना चाहते हैं?",
  "Please check the payment details before confirming.": "पुष्टि करने से पहले भुगतान का विवरण जाँचें।",
  "Choose the payment details below before confirming.": "पुष्टि करने से पहले नीचे भुगतान का विवरण चुनें।",
  "The confirmation time has passed. Please review the payment again.": "पुष्टि का समय समाप्त हो गया है। कृपया भुगतान की दोबारा समीक्षा करें।",
  "The confirmation time has passed. Please start the payment again.": "पुष्टि का समय समाप्त हो गया है। कृपया भुगतान दोबारा शुरू करें।",
  "The payment details have changed.": "भुगतान का विवरण बदल गया है।",
  "I found more than one match. Which one do you mean?": "एक से अधिक मिलते-जुलते विकल्प मिले हैं। आप किसकी बात कर रहे हैं?",
  "I am not sure I understood. Try asking for balances, transactions, mandates or bills.": "आपका अनुरोध स्पष्ट नहीं है। बैलेंस, लेन-देन, ऑटो डेबिट या बिल के बारे में पूछें।",
  "Ask about balances, accounts, transactions, mandates, bills, cards, beneficiaries, scheduled payments or loans. Ask to transfer between your own accounts, or prepare a beneficiary or bill payment for review.": "बैलेंस, खातों, लेन-देन, ऑटो डेबिट, बिल, कार्ड, प्राप्तकर्ताओं, निर्धारित भुगतान या ऋण के बारे में पूछें। अपने खातों के बीच पैसे भेजने या समीक्षा के लिए प्राप्तकर्ता अथवा बिल का भुगतान तैयार करने को कहें।",
  "Please do not send passwords, PINs, verification codes or full card numbers. This message was not retained. Describe what you need without those details.": "कृपया पासवर्ड, पिन, सत्यापन कोड या पूरा कार्ड नंबर न भेजें। यह संदेश सहेजा नहीं गया है। इन जानकारियों के बिना अपनी ज़रूरत बताएँ।",
  "Hi! How can I help with your banking today?": "नमस्ते! आज बैंकिंग में आपकी क्या मदद कर सकता हूँ?"
};

const hindiAccountNames: Record<string, string> = {
  "Primary account": "मुख्य खाता",
  "Demo everyday account": "डेमो रोज़मर्रा का खाता"
};

/** Only built-in names have aliases; customer-defined names remain verbatim. */
export function accountDisplayName(name: string, locale: Locale = getLocale()): string {
  return locale === "hi-IN" && Object.prototype.hasOwnProperty.call(hindiAccountNames, name) ? hindiAccountNames[name] : name;
}

export function localizeReply(text: string, locale: Locale = getLocale()): string {
  if (locale !== "hi-IN") return text;
  const translated = Object.prototype.hasOwnProperty.call(hindiReplies, text.trim()) ? hindiReplies[text.trim()] : t(text, locale);
  if (translated !== text) return translated;
  // The only variable is a card name; keep it intact, including punctuation.
  const cardTransactions = text.match(/^Here are transactions for (.+)\.$/);
  return cardTransactions ? `${cardTransactions[1]} के लेन-देन ये हैं।` : text;
}

export function replyForSpeech(text: string, locale: Locale): string {
  const localized = localizeReply(text, locale);
  // Do not guess a translation or read unhandled English copy in a Hindi voice.
  // The original remains visible, especially for payment results and warnings.
  return locale === "hi-IN" && /[a-z]/i.test(localized) && !/[\u0900-\u097f]/.test(localized)
    ? "इस संदेश का हिन्दी अनुवाद उपलब्ध नहीं है। कृपया स्क्रीन पर मूल संदेश पढ़ें, या पूरा संदेश सुनने के लिए अंग्रेज़ी भाषा चुनें।"
    : localized;
}
