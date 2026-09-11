import { ComponentChildren } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";

/** Object collections compare side by side; records read from top to bottom. */
export function collectionLayout(type: string, count: number): "direct" | "horizontal" | "vertical" {
  if (count < 2) return "direct";
  return ["ACCOUNTS", "CARDS", "LOANS"].includes(type) ? "horizontal" : "vertical";
}

export function collectionNotice(items: { status: string }[]): string {
  const labels: Record<string, string> = { FAILED: "failed", OVERDUE: "overdue", ACTION_REQUIRED: "need attention", PENDING: "pending", PENDING_VERIFICATION: "awaiting verification", BLOCKED: "blocked" };
  return Object.entries(labels).map(([status, label]) => {
    const count = items.filter(item => item.status === status).length;
    return count ? count + " " + label : "";
  }).filter(Boolean).join(" · ");
}

export function BankingCollection({ type, count, label, attention, children }: {
  type: string; count: number; label: string; attention?: string; children: ComponentChildren;
}) {
  const layout = collectionLayout(type, count);
  const viewport = useRef<HTMLDivElement>(null);
  const items = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ overflow: false, start: true, end: true });
  const measure = () => {
    const el = viewport.current;
    if (!el) return;
    const horizontal = layout === "horizontal";
    const extent = horizontal ? el.scrollWidth - el.clientWidth : el.scrollHeight - el.clientHeight;
    const offset = horizontal ? el.scrollLeft : el.scrollTop;
    setEdges({ overflow: extent > 2, start: offset < 2, end: offset >= extent - 2 });
  };
  useEffect(() => {
    measure();
    const observer = new ResizeObserver(measure);
    if (viewport.current) observer.observe(viewport.current);
    if (items.current) observer.observe(items.current);
    return () => observer.disconnect();
  }, [layout, count]);
  const move = (direction: number) => {
    const el = viewport.current;
    if (!el) return;
    const distance = items.current?.firstElementChild?.getBoundingClientRect().width || el.clientWidth;
    el.scrollBy({ left: direction * (distance + 12), behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };
  return <div class={"bank-collection is-" + layout}>
    <div class="bank-collection-caption"><span>{count} {count === 1 ? "item" : "items"}</span>{edges.overflow && <span>{layout === "horizontal" ? "Scroll across to browse" : "Scroll for more"}</span>}</div>
    {attention && <p class="bank-collection-attention">{attention}</p>}
    <div ref={viewport} class="bank-collection-viewport" role={edges.overflow ? "region" : undefined} aria-label={label} tabIndex={edges.overflow ? 0 : undefined} onScroll={measure} onKeyDown={event => {
      if (event.target !== event.currentTarget || layout !== "horizontal") return;
      if (event.key === "ArrowRight" || event.key === "ArrowLeft") { event.preventDefault(); move(event.key === "ArrowRight" ? 1 : -1); }
    }}>
      <div ref={items} class="bank-collection-items">{children}</div>
    </div>
    {layout === "horizontal" && edges.overflow && <nav class="bank-collection-navigation" aria-label={label + " navigation"}>
      <button type="button" class="bank-inline-action" disabled={edges.start} onClick={() => move(-1)}>Previous</button>
      <button type="button" class="bank-inline-action" disabled={edges.end} onClick={() => move(1)}>Next</button>
    </nav>}
  </div>;
}
