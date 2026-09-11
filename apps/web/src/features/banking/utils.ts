export function validAmount(value: string): boolean {
    return /^(?:0|[1-9]\d{0,12})(?:\.\d{1,2})?$/.test(value) && Number(value) > 0;
}
export function initials(name: string): string { return name.trim().split(/\s+/).slice(0, 2).map(p => p[0]).join("").toUpperCase(); }
export const routes = ["overview", "accounts", "transactions", "payments", "beneficiaries", "cards", "bills", "mandates", "scheduled-payments", "loans", "settings", "security", "assistant", "operations"] as const;
export type Route = typeof routes[number];
export function parseRoute(hash: string): {
    page: Route | "login" | "register" | "not-found";
    id?: string;
    account?: string;
} {
    const [pathname, query] = hash.replace(/^#\/?/, "").split("?");
    const parts = pathname.split("/");
    const page = parts[0] || "overview";
    if (![...routes, "login", "register"].includes(page))
        return { page: "not-found" };
    try {
        return { page: page as Route, id: parts[1] ? decodeURIComponent(parts[1]) : undefined, account: new URLSearchParams(query || "").get("account") || undefined };
    }
    catch {
        return { page: "not-found" };
    }
}
export function go(page: string, id?: string) { window.location.hash = "/" + page + (id ? "/" + encodeURIComponent(id) : ""); }
export function sumMoney(values: Array<number | string>): string {
    let total = BigInt(0);
    for (const value of values) {
        const match = String(value).match(/^(-?)(\d+)(?:\.(\d+))?$/);
        if (!match)
            continue;
        const cents = BigInt(match[2]) * BigInt(100) + BigInt((match[3] || "").padEnd(2, "0").slice(0, 2));
        total += match[1] ? -cents : cents;
    }
    const negative = total < BigInt(0);
    const abs = negative ? -total : total;
    return (negative ? "-" : "") + String(abs / BigInt(100)) + "." + String(abs % BigInt(100)).padStart(2, "0");
}
