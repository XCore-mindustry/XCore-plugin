import { expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { CommunityNavLinks } from "../src/components/community-nav";

test("language links preserve a game link code and confirmation intent", () => {
  const html = renderToStaticMarkup(
    <CommunityNavLinks lang="ru" path="/ru/link" query="code=ABC234&intent=test-intent" />,
  );
  expect(html).toContain('href="/uk/link?code=ABC234&amp;intent=test-intent"');
  expect(html).toContain('href="/en/link?code=ABC234&amp;intent=test-intent"');
});

test("language links preserve leaderboard filters and the page", () => {
  const html = renderToStaticMarkup(
    <CommunityNavLinks lang="en" path="/en/top" query="category=hexed-elo&season=2&page=3" />,
  );
  expect(html).toContain('href="/ru/top?category=hexed-elo&amp;season=2&amp;page=3"');
});
