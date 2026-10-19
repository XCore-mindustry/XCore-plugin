import en from "./en";
import ru from "./ru";
import type { Locale } from "./config";

export type { Dictionary } from "./ru";

const dictionaries = { ru, en };

export const getDictionary = (locale: Locale) => dictionaries[locale];
