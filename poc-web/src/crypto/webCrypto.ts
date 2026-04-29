export function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function bytesToBase64(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

export async function generateEphemeralRsaOaep2048(): Promise<CryptoKeyPair> {
  return crypto.subtle.generateKey(
    {
      name: "RSA-OAEP",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["encrypt", "decrypt"]
  );
}

export async function exportSpkiB64(publicKey: CryptoKey): Promise<string> {
  const spki = await crypto.subtle.exportKey("spki", publicKey);
  return bytesToBase64(new Uint8Array(spki));
}

export async function importRsaOaepPublicSpki(spkiB64: string): Promise<CryptoKey> {
  const spki = base64ToBytes(spkiB64);
  return crypto.subtle.importKey(
    "spki",
    spki,
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"]
  );
}

export async function rsaOaepDecrypt(
  privateKey: CryptoKey,
  ciphertext: Uint8Array
): Promise<Uint8Array> {
  const buf = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, privateKey, ciphertext);
  return new Uint8Array(buf);
}

export async function rsaOaepEncrypt(publicKey: CryptoKey, plaintext: Uint8Array): Promise<Uint8Array> {
  const buf = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, publicKey, plaintext);
  return new Uint8Array(buf);
}

export async function aesGcmDecrypt(
  keyBytes: Uint8Array,
  iv: Uint8Array,
  ciphertext: Uint8Array,
  tag: Uint8Array
): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "AES-GCM", length: 256 }, false, [
    "decrypt",
  ]);
  const combined = new Uint8Array(ciphertext.length + tag.length);
  combined.set(ciphertext, 0);
  combined.set(tag, ciphertext.length);
  const out = await crypto.subtle.decrypt({ name: "AES-GCM", iv, tagLength: 128 }, key, combined);
  return new Uint8Array(out);
}

export async function aesGcmEncrypt(
  passwordUtf8: Uint8Array
): Promise<{ key: CryptoKey; keyBytes: Uint8Array; iv: Uint8Array; ciphertext: Uint8Array; tag: Uint8Array }> {
  const keyBytes = crypto.getRandomValues(new Uint8Array(32));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "AES-GCM", length: 256 }, false, [
    "encrypt",
  ]);
  const enc = await crypto.subtle.encrypt({ name: "AES-GCM", iv, tagLength: 128 }, key, passwordUtf8);
  const encArr = new Uint8Array(enc);
  const tagLen = 16;
  const ciphertext = encArr.slice(0, encArr.length - tagLen);
  const tag = encArr.slice(encArr.length - tagLen);
  return { key, keyBytes, iv, ciphertext, tag };
}

export type SessionInner = { sessionId: string; publicKeySpki: string; alg: string };

export type SessionEnvelope = {
  sessionId: string;
  encryptedKey: string;
  iv: string;
  ciphertext: string;
  tag: string;
};

export async function unwrapSessionEnvelope(
  ephemeralPrivate: CryptoKey,
  envelope: SessionEnvelope
): Promise<SessionInner> {
  const aesKeyBytes = await rsaOaepDecrypt(ephemeralPrivate, base64ToBytes(envelope.encryptedKey));
  const plain = await aesGcmDecrypt(
    aesKeyBytes,
    base64ToBytes(envelope.iv),
    base64ToBytes(envelope.ciphertext),
    base64ToBytes(envelope.tag)
  );
  const text = new TextDecoder().decode(plain);
  const inner = JSON.parse(text) as SessionInner;
  if (inner.sessionId !== envelope.sessionId) {
    throw new Error("sessionId mismatch");
  }
  return inner;
}
