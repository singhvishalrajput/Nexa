import { ComponentChildren } from "preact";
import "ojs/ojbutton";

/** JET owns keyboard activation, focus, and disabled state. */
export function Action({ children, label, onAction, disabled = false, primary = false, className = "" }: {
  children?: ComponentChildren; label?: string; onAction: () => void; disabled?: boolean;
  primary?: boolean; className?: string;
}) {
  return <oj-button class={`nexa-action ${className}`} chroming={primary ? "callToAction" : "borderless"}
    disabled={disabled} aria-label={label} onojAction={onAction}>{children || label}</oj-button>;
}
