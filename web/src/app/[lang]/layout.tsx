import type { Metadata, Viewport } from "next";
import { Fira_Code, Golos_Text, Tektur } from "next/font/google";
import localFont from "next/font/local";
import { notFound } from "next/navigation";
import { getDictionary } from "@/i18n";
import { hasLocale, locales } from "@/i18n/config";
import "./globals.css";

const display = Tektur({ subsets: ["latin", "cyrillic"], axes: ["wdth"], variable: "--font-display" });
const body = Golos_Text({ subsets: ["latin", "cyrillic"], variable: "--font-body" });
const code = Fira_Code({ subsets: ["latin", "cyrillic"], variable: "--font-code" });
const icons = localFont({ src: "../fonts/mindustry-icons.ttf", variable: "--font-icons", display: "block" });

export function generateStaticParams() {
  return locales.map((lang) => ({ lang }));
}

export async function generateMetadata({ params }: LayoutProps<"/[lang]">): Promise<Metadata> {
  const { lang } = await params;
  if (!hasLocale(lang)) return {};
  const { meta } = getDictionary(lang);
  return {
    title: meta.title,
    description: meta.description,
    alternates: { languages: Object.fromEntries(locales.map((l) => [l, `/${l}`])) },
    openGraph: { title: meta.title, description: meta.description, siteName: "XCore", locale: lang },
  };
}

export const viewport: Viewport = { themeColor: "#14161c", colorScheme: "dark" };

export default async function RootLayout({ children, params }: LayoutProps<"/[lang]">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();

  return (
    <html lang={lang} className={`${display.variable} ${body.variable} ${code.variable} ${icons.variable}`}>
      <body>{children}</body>
    </html>
  );
}
