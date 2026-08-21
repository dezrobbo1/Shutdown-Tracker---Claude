/**
 * Saves a downloaded blob to the reader's machine.
 *
 * `link.download` is what keeps this safe, and it is the reason a shared helper is worth having.
 * A blob URL opened in a tab runs in this application's origin, and both things saved through here
 * are files somebody else's software produced — evidence a field user uploaded, and a candidate
 * schedule the export worker generated. Saving one never renders it.
 *
 * Both downloads are fetched rather than linked, because the actor headers travel on the request
 * and a plain `<a href>` cannot carry them. That is what leaves a blob to save in the first place.
 */
export function saveBlob(blob: Blob, filename: string) {
  if (typeof URL.createObjectURL !== "function") {
    return;
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
