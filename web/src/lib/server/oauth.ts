import "server-only";
import * as oauth from "oauth4webapi";
import { RequestError, siteOrigin } from "./security";

const authorizationServer: oauth.AuthorizationServer = {
  issuer: "https://discord.com",
  authorization_endpoint: "https://discord.com/oauth2/authorize",
  token_endpoint: "https://discord.com/api/oauth2/token",
  authorization_response_iss_parameter_supported: true,
};

export function oauthConfigured(): boolean {
  return !!(
    process.env.DISCORD_CLIENT_ID &&
    process.env.DISCORD_CLIENT_SECRET &&
    process.env.WEB_ORIGIN
  );
}

function configuration() {
  if (!oauthConfigured()) throw new RequestError(503, "not-configured");
  return {
    client: { client_id: process.env.DISCORD_CLIENT_ID! },
    secret: process.env.DISCORD_CLIENT_SECRET!,
    redirect: new URL("/api/auth/discord/callback", siteOrigin()).href,
  };
}

export async function authorizationUrl(
  state: string,
  verifier: string,
): Promise<URL> {
  const { client, redirect } = configuration();
  const url = new URL(authorizationServer.authorization_endpoint!);
  url.search = new URLSearchParams({
    client_id: client.client_id,
    redirect_uri: redirect,
    response_type: "code",
    scope: "identify",
    state,
    code_challenge: await oauth.calculatePKCECodeChallenge(verifier),
    code_challenge_method: "S256",
  }).toString();
  return url;
}

export async function exchangeIdentity(
  callback: URL,
  state: string,
  verifier: string,
): Promise<{ discordId: string; username: string }> {
  const { client, secret, redirect } = configuration();
  const params = oauth.validateAuthResponse(
    authorizationServer,
    client,
    callback,
    state,
  );
  const response = await oauth.authorizationCodeGrantRequest(
    authorizationServer,
    client,
    oauth.ClientSecretPost(secret),
    params,
    redirect,
    verifier,
    { signal: AbortSignal.timeout(10_000) },
  );
  const tokens = await oauth.processAuthorizationCodeResponse(
    authorizationServer,
    client,
    response,
  );
  const userResponse = await oauth.protectedResourceRequest(
    tokens.access_token,
    "GET",
    new URL("https://discord.com/api/v10/users/@me"),
    undefined,
    undefined,
    { signal: AbortSignal.timeout(10_000) },
  );
  if (!userResponse.ok) throw new RequestError(502, "discord-unavailable");
  const user: unknown = await userResponse.json();
  if (
    !user ||
    typeof user !== "object" ||
    !("id" in user) ||
    typeof user.id !== "string" ||
    !/^\d{17,20}$/.test(user.id) ||
    !("username" in user) ||
    typeof user.username !== "string"
  ) {
    throw new RequestError(502, "invalid-discord-response");
  }
  const name =
    "global_name" in user && typeof user.global_name === "string"
      ? user.global_name
      : user.username;
  return {
    discordId: user.id,
    username: name.replace(/[\x00-\x1f\x7f]/g, "").slice(0, 100),
  };
}
