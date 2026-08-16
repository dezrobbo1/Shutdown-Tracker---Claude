/**
 * Minimal shims for the Node APIs the tests use.
 *
 * Vitest already runs these tests in Node; only the type declarations were missing, and a
 * three-line shim is cheaper than adding @types/node to a browser app's dependencies.
 */
declare module "node:fs" {
  export function readFileSync(path: string | URL, encoding: "utf8"): string;
}
