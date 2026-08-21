/**
 * The operational state classes, and only these.
 *
 * Taken from `docs/product/design-language-and-status-semantics.md`. They are named for what a
 * state means rather than for the colour it happens to be: a class called `red` cannot be
 * re-themed, and it tells a reader nothing about why it is red.
 *
 * `tokens.css` declares the matching CSS classes. A test asserts these two lists agree, for the
 * same reason the capability map is checked against the server's enum — two declarations of one
 * truth drift the moment somebody adds to one of them.
 */
export const statusClasses = [
  "neutral",
  "info",
  "warning",
  "critical",
  "success",
  "restricted"
] as const;

export type StatusClass = (typeof statusClasses)[number];

/** Whether a value is one of the six. */
export function isStatusClass(value: string): value is StatusClass {
  return (statusClasses as readonly string[]).includes(value);
}
