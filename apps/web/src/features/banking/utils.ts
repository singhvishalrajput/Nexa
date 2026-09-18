import { moneyInMinorUnits } from "../../services/banking-content";
export function validAmount(value: string): boolean {
    return /^(?:0|[1-9]\d{0,12})(?:\.\d{1,2})?$/.test(value) && Number(value) > 0;
}
export function initials(name: string): string { return name.trim().split(/\s+/).slice(0, 2).map(p => p[0]).join("").toUpperCase(); }
export const routes = ["send-money", "overview", "accounts", "transactions", "payments", "beneficiaries", "cards", "bills", "mandates", "scheduled-payments", "loans", "settings", "security", "assistant", "operations"] as const;
export type Route = typeof routes[number];
export function parseRoute(hash: string): {
    page: Route | "admin" | "login" | "register" | "not-found";
    id?: string;
    account?: string;
    section?: string;
} {
    const [pathname, query] = hash.replace(/^#\/?/, "").split("?");
    const parts = pathname.split("/");
    const page = parts[0] || "assistant";
    if (pathname === "admin/loans") return {page:"admin",section:"loans"};
    if (page === "admin" && parts.length > 1) {
        if (parts[1] !== "accounts" || !/^[1-9]\d*$/.test(parts[2] || "") || parts.length > 4 ||
            (parts[3] && !["overview", "transactions", "related", "audit"].includes(parts[3]))) return { page: "not-found" };
        return { page: "admin", id: parts[2], section: parts[3] || "overview" };
    }
    if (![...routes, "admin", "login", "register"].includes(page))
        return { page: "not-found" };
    const detailPages = ["accounts", "transactions", "cards", "bills", "beneficiaries", "mandates", "loans", "scheduled-payments"];
    if (parts.length > 2 || (parts[1] && !detailPages.includes(page))) return { page: "not-found" };
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
        const cents = moneyInMinorUnits(value);
        if (cents === null) return "Amount unavailable";
        total += cents;
    }
    const negative = total < BigInt(0);
    const abs = negative ? -total : total;
    return (negative ? "-" : "") + String(abs / BigInt(100)) + "." + String(abs % BigInt(100)).padStart(2, "0");
}
