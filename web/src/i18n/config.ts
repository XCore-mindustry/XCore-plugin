export const locales = ["en", "uk", "ru"] as const;
export type Locale = (typeof locales)[number];

export const hasLocale = (value: string): value is Locale =>
    (locales as readonly string[]).includes(value);

/**
 * The first supported language in an Accept-Language header, in the browser's order.
 * Falls back to "en".
 */
export function pickLocale(acceptLanguage: string | null): Locale {
  for (const part of (acceptLanguage ?? "").split(",")) {
    const primary = part.split(";")[0].trim().split("-")[0].toLowerCase();
    if (hasLocale(primary)) return primary;
  }

  return "en";
}