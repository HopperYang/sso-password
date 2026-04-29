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
