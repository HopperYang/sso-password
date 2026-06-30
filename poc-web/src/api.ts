const API = import.meta.env.VITE_API_BASE ?? "";
const AUTH_BASE = import.meta.env.VITE_AUTH_BASE ?? "http://localhost:8080";
const AZURE_LOGIN_URL = `${AUTH_BASE}/oauth2/authorization/azure`;

export type AuthUser = {
  employeeId: string;
  displayName: string;
};

export function redirectToAzureLogin(): void {
  window.location.assign(AZURE_LOGIN_URL);
}

async function ensureOk(res: Response, message: string, redirectOnUnauthorized = true): Promise<void> {
  if (res.status === 401) {
    if (redirectOnUnauthorized) {
      redirectToAzureLogin();
    }
    throw new Error("unauthorized");
  }
  if (!res.ok) throw new Error(message);
}

export async function fetchSessionUser(options: { redirectOnUnauthorized?: boolean } = {}): Promise<AuthUser> {
  const res = await fetch(`${API}/api/auth/session`, {
    credentials: "include",
  });
  await ensureOk(res, "session check failed", options.redirectOnUnauthorized ?? true);
  return res.json();
}

export async function issueToken(
  apiKey: string,
  body: { employeeId: string; displayName: string }
): Promise<string> {
  const res = await fetch(`${API}/api/auth/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Api-Key": apiKey,
    },
    credentials: "include",
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error("token issue failed");
  const data = (await res.json()) as { accessToken: string };
  return data.accessToken;
}

export async function fetchMe(token: string): Promise<AuthUser> {
  const res = await fetch(`${API}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: "include",
  });
  if (!res.ok) throw new Error("unauthorized");
  return res.json();
}

export async function createPasswordSession(clientPublicKeySpki: string): Promise<Envelope> {
  const res = await fetch(`${API}/api/crypto/password/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ clientPublicKeySpki }),
  });
  await ensureOk(res, "session failed");
  return res.json();
}

export async function submitPassword(body: PasswordSubmitBody): Promise<void> {
  const res = await fetch(`${API}/api/crypto/password/submit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(body),
  });
  await ensureOk(res, "submit failed");
}

export type Envelope = {
  /** 与内层明文 JSON 中的 sessionId 一致，提交密码时原样带回 */
  sessionId: string;
  encryptedKey: string;
  iv: string;
  ciphertext: string;
  tag: string;
};

export type PasswordSubmitBody = {
  sessionId: string;
  encryptedKey: string;
  iv: string;
  ciphertext: string;
  tag: string;
};
