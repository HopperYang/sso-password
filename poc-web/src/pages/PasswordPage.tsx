import { FormEvent, useCallback, useState } from "react";
import { createPasswordSession, submitPassword } from "../api";
import {
  exportSpkiB64,
  generateEphemeralRsaOaep2048,
  importRsaOaepPublicSpki,
  rsaOaepEncrypt,
  unwrapSessionEnvelope,
  aesGcmEncrypt,
  bytesToBase64,
} from "../crypto/webCrypto";

export default function PasswordPage() {
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const onSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setMsg(null);
      setErr(null);
      if (!password) {
        setErr("请输入密码");
        return;
      }
      setBusy(true);
      try {
        const pair = await generateEphemeralRsaOaep2048();
        const clientSpki = await exportSpkiB64(pair.publicKey);
        const envelope = await createPasswordSession(clientSpki);
        const inner = await unwrapSessionEnvelope(pair.privateKey, envelope);
        const serverPub = await importRsaOaepPublicSpki(inner.publicKeySpki);

        const pwdBytes = new TextEncoder().encode(password);
        const { keyBytes, iv, ciphertext, tag } = await aesGcmEncrypt(pwdBytes);
        const encKey = await rsaOaepEncrypt(serverPub, keyBytes);

        await submitPassword({
          sessionId: envelope.sessionId,
          encryptedKey: bytesToBase64(encKey),
          iv: bytesToBase64(iv),
          ciphertext: bytesToBase64(ciphertext),
          tag: bytesToBase64(tag),
        });
        setMsg("提交成功，服务端已解密并校验（见服务端日志）。");
        setPassword("");
      } catch {
        setErr("会话或提交失败，请重试。");
      } finally {
        setBusy(false);
      }
    },
    [password]
  );

  return (
    <div className="card">
      <h1 style={{ marginTop: 0 }}>密码收集（POC）</h1>
      <p className="muted">
        流程：生成临时 RSA 密钥 → 申请加密会话 → 解开外层 AES 包得到服务端公钥 → 本地 AES-GCM
        加密密码并用服务端 RSA 公钥封装密钥后提交。
      </p>
      <form onSubmit={onSubmit}>
        <label>
          <div className="muted" style={{ marginBottom: "0.35rem" }}>
            借记卡密码（演示）
          </div>
          <input
            type="password"
            autoComplete="off"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={busy}
          />
        </label>
        <div style={{ marginTop: "1rem" }}>
          <button type="submit" className="primary" disabled={busy}>
            {busy ? "处理中…" : "加密并提交"}
          </button>
        </div>
      </form>
      {msg && <p className="ok">{msg}</p>}
      {err && <p className="error">{err}</p>}
    </div>
  );
}
