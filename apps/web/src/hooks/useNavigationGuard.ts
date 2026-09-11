import { useEffect, useRef } from "preact/hooks";

const drafts = new Map<object, { dirty: boolean; pending: boolean }>();

export function useNavigationGuard(dirty: boolean, pending = false) {
  const key = useRef({});
  useEffect(() => {
    const id = key.current;
    drafts.set(id, { dirty, pending });
    return () => { drafts.delete(id); };
  }, [dirty, pending]);
}

export function hasUnsavedWork() {
  return [...drafts.values()].some(draft => draft.dirty || draft.pending);
}

export function confirmNavigation() {
  if ([...drafts.values()].some(draft => draft.pending)) {
    window.alert("Please wait for your request to finish before leaving this screen.");
    return false;
  }
  return !hasUnsavedWork() || window.confirm("You have unsaved details or a message. Leave this screen and discard them?");
}
