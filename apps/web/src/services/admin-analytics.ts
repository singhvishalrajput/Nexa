import { authenticatedRequest } from "./auth";

export type AnalyticsPeriod = 1 | 7 | 30 | 90;
export type AnalyticsAmount = { currencyCode: string; amount: string };
export type AnalyticsCount = { key: string; count: number };
export type AnalyticsDay = {
  date: string;
  postedPayments: number;
  paymentAmounts: AnalyticsAmount[];
  newCustomers: number;
  newAccounts: number;
};
export type AnalyticsHour = {
  startAt: string;
  endAt: string;
  postedPayments: number;
  paymentAmounts: AnalyticsAmount[];
  newCustomers: number;
  newAccounts: number;
};
export type AdminAnalyticsSnapshot = {
  generatedAt: string;
  timezone: string;
  period: { days: AnalyticsPeriod; granularity: "HOUR" | "DAY"; startDate: string; endDate: string; startAt: string; endAt: string };
  snapshot: {
    customers: number;
    activeCustomers: number;
    customerAccounts: number;
    activeCustomerAccounts: number;
    activeDepositBalances: AnalyticsAmount[];
    accountTypes: AnalyticsCount[];
    accountStatuses: AnalyticsCount[];
    applicationStatuses: AnalyticsCount[];
    applicationBacklog: number;
    pendingLoans: number;
  };
  activity: {
    newCustomers: number;
    newAccounts: number;
    postedPayments: number;
    paymentAmounts: AnalyticsAmount[];
    daily: AnalyticsDay[];
    hourly: AnalyticsHour[];
  };
};

export function getAdminAnalytics(token: string, days: AnalyticsPeriod): Promise<AdminAnalyticsSnapshot> {
  return authenticatedRequest<AdminAnalyticsSnapshot>(`/admin/analytics?days=${days}`, token);
}
