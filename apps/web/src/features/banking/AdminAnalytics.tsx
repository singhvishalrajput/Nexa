import { useState } from "preact/hooks";
import {
  AdminAnalyticsSnapshot, AnalyticsAmount, AnalyticsCount, AnalyticsDay, AnalyticsHour, AnalyticsPeriod, getAdminAnalytics
} from "../../services/admin-analytics";
import { formatMoney } from "../../services/banking-content";
import { PageHeading, Panel, State, useLoad } from "./ui";

const periods: AnalyticsPeriod[] = [1, 7, 30, 90];
const backlogLabels: Record<string, string> = {
  PENDING_REVIEW: "Awaiting review", APPROVED_AWAITING_CASH: "Awaiting opening cash",
  CASH_RECEIVED: "Ready for account opening", REFUND_PENDING: "Cash return pending"
};
const countFormat = new Intl.NumberFormat("en-IN");
const dayFormat = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", timeZone: "UTC" });
const dateLabel = (date: string) => dayFormat.format(new Date(date + "T00:00:00Z"));
const labelFor = (key: string) => key.toLowerCase().replace(/(^|_)([a-z])/g, (_match, space: string, letter: string) => (space ? " " : "") + letter.toUpperCase());
const amountFor = (amounts: AnalyticsAmount[], currency: string) => amounts.find(item => item.currencyCode === currency)?.amount || "0.00";
const timestampLabel = (timestamp: string, timezone: string) => new Intl.DateTimeFormat("en-IN", {
  day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23", timeZone: timezone
}).format(new Date(timestamp));
const partialHour = (hour: AnalyticsHour) => Date.parse(hour.endAt) - Date.parse(hour.startAt) < 60 * 60 * 1000;
const intervalLabel = (hour: AnalyticsHour, timezone: string) => `${timestampLabel(hour.startAt, timezone)} – ${timestampLabel(hour.endAt, timezone)} · ${timezone}${partialHour(hour) ? " · Partial hour" : ""}`;

export function AdminAnalytics({ token }: { token: string }) {
  const [days, setDays] = useState<AnalyticsPeriod>(1);
  const result = useLoad(() => getAdminAnalytics(token, days), [token, days]);
  const analytics = result.data;
  return <div class="admin-analytics">
    <PageHeading title="Bank overview" description="Customer accounts, payment activity and the work awaiting your team."
      action={<button class="bank-button secondary" disabled={result.loading} onClick={result.reload}>{result.loading ? "Refreshing…" : "Refresh analytics"}</button>}/>
    <div class="analytics-toolbar">
      <div class="analytics-period" role="group" aria-label="Activity period">
        {periods.map(period => <button key={period} class="bank-button secondary" aria-pressed={days === period} onClick={() => setDays(period)}>{period === 1 ? "Last 24 hours" : `Last ${period} days`}</button>)}
      </div>
      <p class="analytics-refresh" role="status">{result.loading ? "Loading the latest figures…" : analytics
        ? <>Updated <time dateTime={analytics.generatedAt}>{new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short", timeZone: analytics.timezone }).format(new Date(analytics.generatedAt))}</time> · {analytics.timezone}</>
        : "Analytics have not loaded."}</p>
    </div>
    <State loading={result.loading} error={result.error} retry={result.reload}>
      {analytics && <AnalyticsOverview analytics={analytics}/>}
    </State>
  </div>;
}

function AnalyticsOverview({ analytics }: { analytics: AdminAnalyticsSnapshot }) {
  const { snapshot, activity, period } = analytics;
  const backlog = Object.entries(backlogLabels).map(([key, label]) => ({ key, label, count: snapshot.applicationStatuses.find(item => item.key === key)?.count || 0 }));
  const waitingForCustomer = snapshot.applicationStatuses.find(item => item.key === "CHANGES_REQUESTED")?.count || 0;
  return <>
    <div class="analytics-section-heading"><h2>Current snapshot</h2><p>Current totals, independent of the activity period.</p></div>
    <div class="analytics-kpis">
      <Metric label="Customers" value={snapshot.customers} detail={`${countFormat.format(snapshot.activeCustomers)} active customers`}/>
      <Metric label="Customer accounts" value={snapshot.customerAccounts} detail={`${countFormat.format(snapshot.activeCustomerAccounts)} active accounts`}/>
      <Metric label="Account applications" value={snapshot.applicationBacklog} detail="Awaiting a staff action" href="#/admin/applications"/>
      <Metric label="Loan requests" value={snapshot.pendingLoans} detail="Awaiting approval" href="#/admin/loans"/>
    </div>
    <Panel title="Active deposit balances" className="analytics-deposits">
      <p class="analytics-description">Current balances in active savings and current accounts. Each currency is shown separately.</p>
      {snapshot.activeDepositBalances.length ? <dl class="analytics-currency-balances">{snapshot.activeDepositBalances.map(item => <div key={item.currencyCode}><dt>{item.currencyCode}</dt><dd>{formatMoney(item.amount, item.currencyCode)}</dd></div>)}</dl>
        : <p class="analytics-empty">No active customer deposit accounts yet.</p>}
    </Panel>
    <div class="analytics-section-heading"><h2>{period.granularity === "HOUR" ? "Activity in the last 24 hours" : `Activity over ${period.days} days`}</h2><p>{period.granularity === "HOUR"
      ? <>{timestampLabel(period.startAt, analytics.timezone)} – {timestampLabel(period.endAt, analytics.timezone)} · {analytics.timezone}. Ends at this server check.</>
      : <>{dateLabel(period.startDate)} – {dateLabel(period.endDate)} · {analytics.timezone}. Today is still in progress.</>}</p></div>
    <div class="analytics-activity-kpis">
      <Metric label="Posted payments" value={activity.postedPayments} detail="Counted once per payment"/>
      <Metric label="New customers" value={activity.newCustomers} detail="Registered during this period"/>
      <Metric label="New customer accounts" value={activity.newAccounts} detail="Created during this period"/>
    </div>
    <PaymentActivity activity={activity} period={period} timezone={analytics.timezone}/>
    <div class="analytics-section-heading"><h2>Work awaiting your team</h2><p>Current queues across all dates.</p></div>
    <div class="analytics-columns">
      <Panel title="Account applications" action={<a href="#/admin/applications">Open applications →</a>}>
        <ul class="analytics-queue-list">{backlog.map(item => <li key={item.key}><span>{item.label}</span><strong>{countFormat.format(item.count)}</strong></li>)}</ul>
        <p class="analytics-footnote">{snapshot.applicationBacklog ? `${countFormat.format(snapshot.applicationBacklog)} applications need a staff action.` : "No applications are waiting for staff action."}
          {waitingForCustomer > 0 && <> Another {countFormat.format(waitingForCustomer)} await customer updates.</>}</p>
      </Panel>
      <Panel title="Loan approvals" action={<a href="#/admin/loans">Open loan requests →</a>}>
        <div class="analytics-loans"><strong>{countFormat.format(snapshot.pendingLoans)}</strong><span>{snapshot.pendingLoans === 1 ? "loan request awaiting approval" : "loan requests awaiting approval"}</span>
          <p>{snapshot.pendingLoans ? "Review the application and supporting documents before making a decision." : "The loan approval queue is clear."}</p></div>
      </Panel>
    </div>
    <div class="analytics-section-heading"><h2>Customer account mix</h2><p>Current customer accounts, including deposit, loan and card accounts.</p><a href="#/admin/accounts">Open account directory →</a></div>
    <div class="analytics-columns">
      <CountDistribution title="By account type" items={snapshot.accountTypes}/>
      <CountDistribution title="By account status" items={snapshot.accountStatuses}/>
    </div>
  </>;
}

function Metric({ label, value, detail, href }: { label: string; value: number; detail: string; href?: string }) {
  return <article class="analytics-metric"><h3>{label}</h3><strong>{countFormat.format(value)}</strong><p>{detail}</p>{href && <a href={href} aria-label={`View ${label.toLowerCase()} queue`}>View queue <span aria-hidden="true">→</span></a>}</article>;
}

function CountDistribution({ title, items }: { title: string; items: AnalyticsCount[] }) {
  const total = items.reduce((sum, item) => sum + item.count, 0);
  return <Panel title={title}>
    {!total ? <p class="analytics-empty">No customer accounts yet.</p> : <ul class="analytics-distribution">{items.filter(item => item.count > 0).map(item => <li key={item.key}>
      <div><span>{labelFor(item.key)}</span><strong>{countFormat.format(item.count)}</strong></div>
      <div class="analytics-bar-track" aria-hidden="true"><span style={{ width: `${item.count / total * 100}%` }}/></div>
    </li>)}</ul>}
  </Panel>;
}

function PaymentActivity({ activity, period, timezone }: { activity: AdminAnalyticsSnapshot["activity"]; period: AdminAnalyticsSnapshot["period"]; timezone: string }) {
  const [metric, setMetric] = useState<"count" | "amount">("count");
  const [selectedCurrency, setCurrency] = useState("INR");
  const [showTable, setShowTable] = useState(false);
  const currencies = activity.paymentAmounts.map(item => item.currencyCode);
  const currency = currencies.includes(selectedCurrency) ? selectedCurrency : currencies[0] || "INR";
  const isAmount = metric === "amount";
  const isHourly = period.granularity === "HOUR";
  const intervalName = isHourly ? "hourly" : "daily";
  const description = isAmount ? `Posted payment value in ${currency}` : "Number of posted payments";
  return <Panel title={isHourly ? "Hourly posted payments" : "Daily posted payments"} className="analytics-payments">
    <div class="analytics-chart-toolbar"><div class="analytics-chart-controls">
      <label>Show<select value={metric} onChange={event => setMetric(event.currentTarget.value as "count" | "amount")}><option value="count">Payment count</option><option value="amount">Payment value</option></select></label>
      {isAmount && currencies.length > 0 && <label>Currency<select value={currency} onChange={event => setCurrency(event.currentTarget.value)}>{currencies.map(code => <option key={code} value={code}>{code}</option>)}</select></label>}
    </div><button class="bank-button secondary" aria-expanded={showTable} aria-controls="analytics-activity-table" onClick={() => setShowTable(value => !value)}>{showTable ? `Hide ${intervalName} data` : `View ${intervalName} data`}</button></div>
    {activity.paymentAmounts.length > 0 && <div class="analytics-payment-totals" aria-label="Total posted value in this period"><span>Period value</span>{activity.paymentAmounts.map(item => <strong key={item.currencyCode}>{formatMoney(item.amount, item.currencyCode)} <small>{item.currencyCode}</small></strong>)}</div>}
    {activity.postedPayments === 0 ? <p class="analytics-empty">No posted payments in this period.</p>
      : isHourly ? activity.hourly.length ? <HourlyActivityChart hourly={activity.hourly} period={period} timezone={timezone} currency={currency} isAmount={isAmount} description={description}/>
        : <p class="analytics-empty">Hourly payment details are unavailable. Refresh analytics to try again.</p>
      : activity.daily.length ? <ActivityChart daily={activity.daily} currency={currency} isAmount={isAmount} description={description}/>
        : <p class="analytics-empty">Daily payment details are unavailable. Refresh analytics to try again.</p>}
    {isHourly && <p class="analytics-footnote">Each point is the total for its time interval, including hours with no activity. The first and last intervals may be partial hours. Lines connect recorded totals; they do not show activity within an hour.</p>}
    <p class="analytics-footnote">Posted payments include customer funding and transfers, counted once per payment. Sandbox transfers and unposted attempts are excluded. Payment value is activity volume, not bank revenue.</p>
    {showTable && <div id="analytics-activity-table" class="admin-table-scroll analytics-data-table" role="region" aria-label={isHourly ? "Hourly activity data" : "Daily activity data"} tabIndex={0}><table class="admin-table">
      <caption>{isHourly ? <>Hourly activity in {timezone}. Each interval includes its start and excludes its end. Partial intervals are labelled.</>
        : <>Daily activity, {dateLabel(period.startDate)} – {dateLabel(period.endDate)}. Dates use the dashboard time zone.</>}</caption>
      <thead><tr><th scope="col">{isHourly ? "Time interval" : "Date"}</th><th scope="col">Posted payments</th>{currencies.map(code => <th scope="col" key={code}>Payment value ({code})</th>)}<th scope="col">New customers</th><th scope="col">New accounts</th></tr></thead>
      <tbody>{isHourly ? activity.hourly.map(hour => <tr key={hour.startAt}><th scope="row"><time dateTime={hour.startAt} title={hour.startAt}>{timestampLabel(hour.startAt, timezone)}</time><span class="analytics-interval-end">to <time dateTime={hour.endAt} title={hour.endAt}>{timestampLabel(hour.endAt, timezone)}</time></span>{partialHour(hour) && <span class="analytics-partial-hour">Partial hour</span>}</th><td>{countFormat.format(hour.postedPayments)}</td>{currencies.map(code => <td key={code}>{formatMoney(amountFor(hour.paymentAmounts, code), code)}</td>)}<td>{countFormat.format(hour.newCustomers)}</td><td>{countFormat.format(hour.newAccounts)}</td></tr>)
        : activity.daily.map(day => <tr key={day.date}><th scope="row">{dateLabel(day.date)}</th><td>{countFormat.format(day.postedPayments)}</td>{currencies.map(code => <td key={code}>{formatMoney(amountFor(day.paymentAmounts, code), code)}</td>)}<td>{countFormat.format(day.newCustomers)}</td><td>{countFormat.format(day.newAccounts)}</td></tr>)}</tbody>
    </table></div>}
  </Panel>;
}

function HourlyActivityChart({ hourly, period, timezone, currency, isAmount, description }: {
  hourly: AnalyticsHour[]; period: AdminAnalyticsSnapshot["period"]; timezone: string; currency: string; isAmount: boolean; description: string
}) {
  const values = hourly.map(hour => isAmount ? Number(amountFor(hour.paymentAmounts, currency)) : hour.postedPayments);
  const largest = Math.max(1, ...values);
  const maximum = isAmount ? largest : Math.ceil(largest / 2) * 2;
  const width = 760, height = 260, left = 74, right = 20, top = 20, bottom = 54;
  const plotWidth = width - left - right, plotHeight = height - top - bottom, baseline = top + plotHeight;
  const start = Date.parse(period.startAt), end = Date.parse(period.endAt), duration = Math.max(1, end - start);
  const points = hourly.map((hour, index) => ({
    x: left + ((Date.parse(hour.startAt) + Date.parse(hour.endAt)) / 2 - start) / duration * plotWidth,
    y: baseline - values[index] / maximum * plotHeight
  }));
  const line = points.map(point => `${point.x},${point.y}`).join(" ");
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(fraction => ({ x: left + fraction * plotWidth, timestamp: new Date(start + fraction * duration) }));
  const axis = (value: number) => new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 }).format(value);
  const localDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", timeZone: timezone });
  const localTime = new Intl.DateTimeFormat("en-IN", { hour: "2-digit", minute: "2-digit", hourCycle: "h23", timeZone: timezone });
  return <figure class="analytics-chart analytics-hourly-chart"><figcaption>{description}</figcaption><div class="analytics-chart-scroll">
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${description}, hourly for the last 24 hours in ${timezone}. Each point represents its interval total. Use View hourly data for exact time bounds and figures.`}>
      {[0, 0.5, 1].map(fraction => <g key={fraction}><line class="analytics-chart-grid" x1={left} x2={width - right} y1={baseline - plotHeight * fraction} y2={baseline - plotHeight * fraction}/><text class="analytics-chart-axis" text-anchor="end" x={left - 12} y={baseline - plotHeight * fraction + 4}>{isAmount ? axis(maximum * fraction) : countFormat.format(Math.round(maximum * fraction))}</text></g>)}
      <polygon class="analytics-chart-area" points={`${points[0].x},${baseline} ${line} ${points[points.length - 1].x},${baseline}`} aria-hidden="true"/>
      <polyline class="analytics-chart-line" points={line} aria-hidden="true"/>
      {hourly.map((hour, index) => <circle key={hour.startAt} class="analytics-chart-point" cx={points[index].x} cy={points[index].y} r={3.5}><title>{intervalLabel(hour, timezone)}: {isAmount ? formatMoney(amountFor(hour.paymentAmounts, currency), currency) : `${countFormat.format(hour.postedPayments)} posted payments`}</title></circle>)}
      {ticks.map((tick, index) => <text key={index} class="analytics-chart-axis" text-anchor={index === 0 ? "start" : index === ticks.length - 1 ? "end" : "middle"} x={tick.x} y={height - 28}><tspan x={tick.x}>{localTime.format(tick.timestamp)}</tspan><tspan x={tick.x} dy={16}>{localDate.format(tick.timestamp)}</tspan></text>)}
    </svg>
  </div></figure>;
}

function ActivityChart({ daily, currency, isAmount, description }: { daily: AnalyticsDay[]; currency: string; isAmount: boolean; description: string }) {
  const values = daily.map(day => isAmount ? Number(amountFor(day.paymentAmounts, currency)) : day.postedPayments);
  const largest = Math.max(1, ...values);
  const maximum = isAmount ? largest : Math.ceil(largest / 2) * 2;
  const width = 760, height = 230, left = 74, right = 16, top = 18, bottom = 38;
  const plotWidth = width - left - right, plotHeight = height - top - bottom;
  const slot = plotWidth / Math.max(1, daily.length), barWidth = Math.max(1, slot * 0.66);
  const axis = (value: number) => new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 }).format(value);
  const tickIndices = Array.from(new Set([0, Math.floor((daily.length - 1) / 2), daily.length - 1]));
  return <figure class="analytics-chart"><figcaption>{description}</figcaption><div class="analytics-chart-scroll">
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`${description}, daily from ${dateLabel(daily[0].date)} to ${dateLabel(daily[daily.length - 1].date)}. Use View daily data for exact figures.`}>
      {[0, 0.5, 1].map(fraction => <g key={fraction}><line class="analytics-chart-grid" x1={left} x2={width - right} y1={top + plotHeight * (1 - fraction)} y2={top + plotHeight * (1 - fraction)}/><text class="analytics-chart-axis" text-anchor="end" x={left - 12} y={top + plotHeight * (1 - fraction) + 4}>{isAmount ? axis(maximum * fraction) : countFormat.format(Math.round(maximum * fraction))}</text></g>)}
      {daily.map((day, index) => <rect key={day.date} class="analytics-chart-bar" x={left + slot * index + (slot - barWidth) / 2} y={top + plotHeight * (1 - values[index] / maximum)} width={barWidth} height={plotHeight * values[index] / maximum} rx={Math.min(3, barWidth / 2)}><title>{dateLabel(day.date)}: {isAmount ? formatMoney(amountFor(day.paymentAmounts, currency), currency) : `${countFormat.format(day.postedPayments)} posted payments`}</title></rect>)}
      {tickIndices.map(index => <text key={index} class="analytics-chart-axis" text-anchor={index === 0 ? "start" : index === daily.length - 1 ? "end" : "middle"} x={index === 0 ? left : index === daily.length - 1 ? width - right : left + slot * (index + 0.5)} y={height - 12}>{dateLabel(daily[index].date)}</text>)}
    </svg>
  </div></figure>;
}
