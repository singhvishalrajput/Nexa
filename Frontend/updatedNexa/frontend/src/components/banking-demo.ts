export type Transaction = {
  id: string;
  name: string;
  amount: number;
  category: string;
  date: string;
};
export type Account = {
  balance: number;
  travel: number;
  transactions: Transaction[];
};
export type Proposal = {
  amount: number;
  recipient: string;
  kind: "payment" | "saving";
  status: "pending" | "confirmed" | "cancelled" | "failed";
};
export type Card = {
  type: "balance" | "spending" | "goals" | "activity";
  amount?: number;
  rows?: { label: string; value: number }[];
};
export type Reply = { text: string; card?: Card; proposal?: Proposal };
export const money = (amount: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(amount);
export const initialAccount = (): Account => ({
  balance: 42850,
  travel: 32000,
  transactions: [
    {
      id: "seed-1",
      name: "Home & bills",
      amount: 4800,
      category: "Home & bills",
      date: "Sample month",
    },
    {
      id: "seed-2",
      name: "Shopping",
      amount: 3250,
      category: "Shopping",
      date: "Sample month",
    },
    {
      id: "seed-3",
      name: "Food & coffee",
      amount: 2400,
      category: "Food & coffee",
      date: "Sample month",
    },
    {
      id: "seed-4",
      name: "Travel",
      amount: 2000,
      category: "Travel",
      date: "Sample month",
    },
  ],
});
export function respond(input: string, account: Account): Reply {
  const q = input.trim().toLowerCase().replace(/’/g, "'");
  if (
    /\b(new (?:here|to nexa)|get started|getting started|open (?:an? |my )?account|create (?:an? |my )?account|sign up)\b/.test(
      q,
    ) ||
    /\b(?:don't|do not|don’t) have (?:an? )?account\b/.test(q)
  )
    return {
      text: "Welcome! You can explore Nexa without an account. Ask ‘How does Nexa work?’ to learn about conversational banking. This preview doesn’t open or connect real bank accounts yet.",
    };
  if (
    /\b(how does nexa work|what (?:can nexa do|is nexa)|about nexa)\b/.test(q)
  )
    return {
      text: "Nexa lets you use everyday words to ask banking questions and review actions before confirming them. You can type or use Voice. This preview can demonstrate balances, spending, savings, and transfers using sample data; you don’t need to connect an account to explore.",
    };
  if (/\b(cancel|never mind|nevermind|don't|do not)\b/.test(q))
    return {
      text: "Nothing has been moved. Use Cancel on a pending transfer to dismiss it, or ask me something else.",
    };
  if (
    /\b(send|pay|transfer|move|add|put|deposit)\b/.test(q) ||
    /\bsave\b.*\d/.test(q)
  ) {
    if (/\bfrom\s+(?:my\s+)?(?:travel|savings)|\bto\b.*(?:\band\b|&)/.test(q))
      return {
        text: "Please request one transfer at a time, from your everyday account to one contact or your travel fund.",
      };
    if (/[$€£]|\b(usd|dollars?|euros?)\b/.test(q))
      return { text: "This demo uses Indian rupees. Try ‘Send ₹500 to Alex’." };
    if (/\b(tomorrow|every|weekly|monthly|schedule|next|yesterday)\b/.test(q))
      return {
        text: "Scheduled transfers aren’t available in this demo. I can prepare a one-time transfer for you to review.",
      };
    const amounts = [
      ...q.matchAll(/-?\d[\d,]*(?:\.\d+)?(?:\s*(?:k\b|thousand\b))?/g),
    ];
    if (amounts.length !== 1)
      return {
        text: "How much would you like to move? Use one amount, like ‘Send ₹500 to Alex’.",
      };
    const raw = amounts[0][0].replace(/,/g, "");
    const amount =
      Number(raw.replace(/\s*(k|thousand)$/, "")) *
      (/k|thousand/.test(raw) ? 1000 : 1);
    if (
      !Number.isFinite(amount) ||
      amount <= 0 ||
      Math.abs(amount * 100 - Math.round(amount * 100)) > 0.00001
    )
      return {
        text: "Please enter a positive amount with no more than two decimal places.",
      };
    if (amount > account.balance)
      return {
        text: `That’s more than your available demo balance of ${money(account.balance)}. Try a smaller amount.`,
      };
    const saving =
      /\b(?:to|into|in)\s+(?:my\s+)?(?:travel fund|japan|savings|saving fund)\b/.test(
        q,
      );
    const recipient = q.match(/\bto\s+(alex|sam|priya)\b/);
    if (!saving && !recipient)
      return {
        text: "Who should receive it? Demo contacts are Alex, Sam, and Priya. You can also move money to your travel fund.",
      };
    const name = saving
      ? "Travel fund"
      : recipient![1][0].toUpperCase() + recipient![1].slice(1);
    return {
      text: "Here’s the transfer preview. Check the details before you confirm.",
      proposal: {
        amount,
        recipient: name,
        kind: saving ? "saving" : "payment",
        status: "pending",
      },
    };
  }
  if (
    /\b(balance|available|how much.*have)\b/.test(q) &&
    !/\b(travel|japan|saving|savings|fund)\b/.test(q)
  )
    return {
      text: "Here’s what’s available in your everyday demo account.",
      card: { type: "balance", amount: account.balance },
    };
  if (/\b(spend|spending|spent|expenses|money go|breakdown)\b/.test(q)) {
    const categories: Record<string, number> = {};
    account.transactions
      .filter((t) => t.category !== "Savings")
      .forEach(
        (t) =>
          (categories[t.category] = (categories[t.category] || 0) + t.amount),
      );
    const rows = Object.entries(categories)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
    return {
      text: "Here’s the spending breakdown for this demo month. Transfers to your own savings are excluded.",
      card: {
        type: "spending",
        amount: rows.reduce((sum, r) => sum + r.value, 0),
        rows,
      },
    };
  }
  if (/\b(goal|goals|travel|japan|saving|savings|fund)\b/.test(q))
    return {
      text: `You’re ${Math.round((account.travel / 50000) * 100)}% of the way to Japan. ${account.travel >= 50000 ? "You’ve reached your goal!" : `${money(50000 - account.travel)} to go.`}`,
      card: { type: "goals", amount: account.travel },
    };
  if (/\b(transactions?|activity|history|recent|payments?)\b/.test(q))
    return {
      text: "Your recent demo activity, including confirmed transfers.",
      card: {
        type: "activity",
        rows: account.transactions
          .slice(0, 8)
          .map((t) => ({ label: t.name, value: t.amount })),
      },
    };
  if (/^(hi|hello|hey|thanks|thank you)[!.\s]*$/.test(q))
    return {
      text: "Hi! I’m Nexa. You don’t need an account to explore. Ask ‘How does Nexa work?’ or tell me what you’d like help with.",
    };
  return {
    text: "I can help with balances, spending, savings goals, and demo transfers. Try ‘What’s my balance?’, ‘Show my spending’, or ‘Send ₹500 to Alex’.",
  };
}
export function approve(
  account: Account,
  proposal: Proposal,
  id: string,
): Account | null {
  if (
    proposal.status !== "pending" ||
    !Number.isFinite(proposal.amount) ||
    proposal.amount <= 0 ||
    proposal.amount > account.balance ||
    account.transactions.some((t) => t.id === id)
  )
    return null;
  const amount = Math.round(proposal.amount * 100) / 100;
  return {
    balance: Math.round((account.balance - amount) * 100) / 100,
    travel:
      proposal.kind === "saving"
        ? Math.round((account.travel + amount) * 100) / 100
        : account.travel,
    transactions: [
      {
        id,
        name:
          proposal.kind === "saving"
            ? "To travel fund"
            : `To ${proposal.recipient}`,
        amount,
        category: proposal.kind === "saving" ? "Savings" : "Payments",
        date: "Today",
      },
      ...account.transactions,
    ],
  };
}
