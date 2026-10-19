import en from "./en";
import ru from "./ru";
import uk from "./uk";
import type { Locale } from "./config";

export type { Dictionary } from "./en";

const dictionaries = { en, uk, ru };

export const getDictionary = (locale: Locale) => dictionaries[locale] ?? dictionaries.en;