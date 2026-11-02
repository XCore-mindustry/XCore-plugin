/**
 * The game's "no PID" value (PlayerPids.NONE in XCore-plugin). Zero and negative PIDs are real:
 * technical admins hand them out to special players.
 */
export const NO_PID = -2147483648;

export function isPid(value: unknown): value is number {
  return Number.isSafeInteger(value) && value !== NO_PID;
}

/** A PID typed in a URL or a search box, or null. */
export function parsePid(text: unknown): number | null {
  if (typeof text !== "string" || !/^-?\d{1,10}$/.test(text)) return null;
  const pid = Number(text);
  return isPid(pid) ? pid : null;
}
