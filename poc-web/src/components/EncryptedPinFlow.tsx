import type { ReactNode } from "react";
import { FormEvent, useCallback, useEffect, useId, useRef, useState } from "react";
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
import SecurePinField from "./SecurePinField";
import PinSixCirclesInput from "./PinSixCirclesInput";

const DIGITS_6 = 6;
const AUTO_NEXT_MS = 280;

type Props = {
  title: string;
  description: ReactNode;
  pinLabel?: string;
  /**
   * default：单框演示。
   * bankCard：仅 6 位数字、两遍一致；首屏 6 圆点输入满后自动进入「请再次输入」屏；圆点不展示明文。
   */
  pinPolicy?: "default" | "bankCard";
};

export default function EncryptedPinFlow({
  title,
  description,
  pinLabel = "密码",
  pinPolicy = "default",
}: Props) {
  const baseId = useId();
  const idFirst = `${baseId}-c1`;
  const idSecond = `${baseId}-c2`;
  const idLegacy = `${baseId}-legacy`;

  const [password, setPassword] = useState("");
  const [confirmPin, setConfirmPin] = useState("");
  /** bankCard：1 = 首屏 6 圆点，2 = 再次输入 */
  const [bankStep, setBankStep] = useState<1 | 2>(1);
  const advanceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const bankCard = pinPolicy === "bankCard";

  /** 首屏 6 位输满后延时进入「请再次输入」；若用户在延时内删位则取消跳转 */
  useEffect(() => {
    if (!bankCard || bankStep !== 1) {
      return;
    }
    if (password.length < DIGITS_6) {
      if (advanceTimerRef.current) {
        clearTimeout(advanceTimerRef.current);
        advanceTimerRef.current = null;
      }
      return;
    }
    advanceTimerRef.current = setTimeout(() => {
      setBankStep(2);
      setConfirmPin("");
      advanceTimerRef.current = null;
    }, AUTO_NEXT_MS);
    return () => {
      if (advanceTimerRef.current) {
        clearTimeout(advanceTimerRef.current);
        advanceTimerRef.current = null;
      }
    };
  }, [bankCard, bankStep, password]);

  const onSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setMsg(null);
      setErr(null);

      if (bankCard) {
        if (password.length !== DIGITS_6 || confirmPin.length !== DIGITS_6) {
          setErr("请输入 6 位数字。");
          return;
        }
        if (password !== confirmPin) {
          setErr("两次输入不一致，请返回第一步重新设置。");
          return;
        }
      } else if (!password) {
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
        setConfirmPin("");
        setBankStep(1);
      } catch {
        setErr("会话或提交失败，请重试。");
      } finally {
        setBusy(false);
      }
    },
    [password, confirmPin, bankCard]
  );

  const canSubmitBank =
    !busy && bankStep === 2 && password.length === DIGITS_6 && confirmPin.length === DIGITS_6;

  const restartBankFlow = useCallback(() => {
    setBankStep(1);
    setPassword("");
    setConfirmPin("");
    setErr(null);
  }, []);

  return (
    <div className="card">
      <h1 style={{ marginTop: 0 }}>{title}</h1>
      <p className="muted">{description}</p>
      {bankCard && (
        <p className="muted" style={{ fontSize: "0.85rem", marginTop: "-0.25rem" }}>
          仅支持 6 位数字；界面以圆点表示已输入位数，不显示明文；首屏输满后自动进入确认页。
        </p>
      )}
      <form onSubmit={onSubmit}>
        {bankCard ? (
          <>
            {bankStep === 1 && (
              <section className="pin-bank-screen" aria-labelledby={`${baseId}-t1`}>
                <h2 id={`${baseId}-t1`} className="pin-bank-screen-title">
                  {pinLabel}
                </h2>
                <p className="muted pin-bank-screen-hint">请输入 6 位数字</p>
                <PinSixCirclesInput
                  id={idFirst}
                  autoFocus
                  value={password}
                  onChange={setPassword}
                  disabled={busy}
                  aria-label={`${pinLabel}，第一遍，6 位数字`}
                />
              </section>
            )}
            {bankStep === 2 && (
              <section className="pin-bank-screen" aria-labelledby={`${baseId}-t2`}>
                <h2 id={`${baseId}-t2`} className="pin-bank-screen-title">
                  请再次输入
                </h2>
                <p className="muted pin-bank-screen-hint">须与上一步完全一致</p>
                <PinSixCirclesInput
                  id={idSecond}
                  autoFocus
                  value={confirmPin}
                  onChange={setConfirmPin}
                  disabled={busy}
                  aria-label={`${pinLabel}，确认，6 位数字`}
                />
                <div className="pin-bank-actions">
                  <button type="button" className="ghost" disabled={busy} onClick={restartBankFlow}>
                    返回上一步
                  </button>
                  <button type="submit" className="primary" disabled={!canSubmitBank}>
                    {busy ? "处理中…" : "加密并提交"}
                  </button>
                </div>
              </section>
            )}
          </>
        ) : (
          <>
            <label htmlFor={idLegacy}>
              <div className="muted" style={{ marginBottom: "0.35rem" }}>
                {pinLabel}
              </div>
              <SecurePinField
                id={idLegacy}
                aria-label={pinLabel}
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
          </>
        )}
      </form>
      {msg && <p className="ok">{msg}</p>}
      {err && <p className="error">{err}</p>}
    </div>
  );
}
