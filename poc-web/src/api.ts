const API = import.meta.env.VITE_API_BASE ?? "";

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
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error("token issue failed");
  const data = (await res.json()) as { accessToken: string };
  return data.accessToken;
}

export async function fetchMe(token: string): Promise<{ employeeId: string; displayName: string }> {
  const res = await fetch(`${API}/api/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error("unauthorized");
  return res.json();
}

export async function fetchSsoLoginUrl(): Promise<string> {
  const res = await fetch(`${API}/api/sso/login-url`, {
    credentials: "include",
  });
  if (!res.ok) throw new Error("sso login-url failed");
  const data = (await res.json()) as { loginUrl: string };
  return data.loginUrl;
}

export async function fetchSsoSession(): Promise<SsoSession> {
  const res = await fetch(`${API}/api/sso/session/me`, {
    credentials: "include",
  });
  if (!res.ok) throw new Error("sso session not found");
  return res.json();
}

export async function logoutSso(): Promise<string> {
  const res = await fetch(`${API}/api/sso/logout`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok) throw new Error("sso logout failed");
  const data = (await res.json()) as { logoutUrl: string };
  return data.logoutUrl;
}

export async function createPasswordSession(clientPublicKeySpki: string): Promise<Envelope> {
  const res = await fetch(`${API}/api/crypto/password/session`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ clientPublicKeySpki }),
  });
  if (!res.ok) throw new Error("session failed");
  return res.json();
}

export async function submitPassword(body: PasswordSubmitBody): Promise<void> {
  const res = await fetch(`${API}/api/crypto/password/submit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error("submit failed");
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

export type SsoUser = {
  subject: string;
  employeeId: string;
  displayName: string;
  email: string;
  groups: string[];
};

export type SsoSession = {
  user: SsoUser;
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
};
