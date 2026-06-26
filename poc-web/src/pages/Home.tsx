import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { fetchMe, fetchSessionUser, issueToken, redirectToAzureLogin } from "../api";

const TOKEN_KEY = "sso_access_token";
const isDev = import.meta.env.DEV;
const autoRedirectToAzure = import.meta.env.VITE_AUTH_AUTO_REDIRECT !== "false";
const defaultApiKey = import.meta.env.VITE_SSO_API_KEY ?? "poc-dev-api-key";

export default function Home() {
  const [params] = useSearchParams();
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<{ employeeId: string; displayName: string } | null>(null);
  const [checkingSession, setCheckingSession] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [devEmployeeId, setDevEmployeeId] = useState("E0001");
  const [devDisplayName, setDevDisplayName] = useState("本地调试");
  const [devApiKey, setDevApiKey] = useState(defaultApiKey);
  const [devBusy, setDevBusy] = useState(false);

  useEffect(() => {
    const q = params.get("token");
    if (q) {
      sessionStorage.setItem(TOKEN_KEY, q);
      setToken(q);
      const url = new URL(window.location.href);
      url.searchParams.delete("token");
      window.history.replaceState({}, "", url.pathname + url.search);
    }
  }, [params]);

  useEffect(() => {
    if (token) {
      return;
    }
    let cancelled = false;
    setCheckingSession(true);
    (async () => {
      try {
        const me = await fetchSessionUser({ redirectOnUnauthorized: autoRedirectToAzure });
        if (!cancelled) {
          setUser(me);
          setErr(null);
        }
      } catch {
        if (!cancelled) {
          setUser(null);
          if (!autoRedirectToAzure) {
            setErr("未检测到后端 Session。可点击后端登录入口，或使用下方开发模拟登录。");
          }
        }
      } finally {
        if (!cancelled) {
          setCheckingSession(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const me = await fetchMe(token);
        if (!cancelled) {
          setUser(me);
          setErr(null);
        }
      } catch {
        if (!cancelled) {
          setUser(null);
          setErr(
            isDev
              ? "令牌无效或已过期。默认推荐使用 Microsoft Entra ID 登录；调试时可在下方「开发模拟登录」重新取令牌，或使用 WPF / ?token=。"
              : "令牌无效或已过期。请重新登录。"
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function onDevLogin(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    setDevBusy(true);
    try {
      const access = await issueToken(devApiKey, {
        employeeId: devEmployeeId.trim(),
        displayName: devDisplayName.trim(),
      });
      sessionStorage.setItem(TOKEN_KEY, access);
      setToken(access);
    } catch {
      setErr("取令牌失败：请确认后端已启动，且 API Key 与 sso-server 配置一致。");
    } finally {
      setDevBusy(false);
    }
  }

  return (
    <div className="card">
      <h1 style={{ marginTop: 0 }}>SSO POC 首页</h1>
      {checkingSession && (
        <p className="muted">
          正在检查后端登录会话；如果未登录，将跳转到 Microsoft Entra ID。
        </p>
      )}
      {!token && !user && !checkingSession && (
        <>
          <p className="muted">
            未检测到后端 Session。默认流程会跳转到 Microsoft Entra ID；Windows 上仍可运行{" "}
            <code>poc-wpf</code>，任意系统可用 <code>?token=...</code> 传入 JWT，或在开发模式下使用下方模拟登录。
          </p>
          <button type="button" className="primary" onClick={redirectToAzureLogin}>
            使用 Microsoft Entra ID 登录
          </button>
          {isDev && (
            <form
              onSubmit={onDevLogin}
              style={{
                marginTop: "1rem",
                padding: "1rem",
                background: "#f1f5f9",
                borderRadius: 8,
                border: "1px solid #e2e8f0",
              }}
            >
              <div style={{ fontWeight: 600, marginBottom: "0.5rem" }}>开发：模拟 WPF 取令牌</div>
              <p className="muted" style={{ marginTop: 0, fontSize: "0.85rem" }}>
                调用 <code>POST /api/auth/token</code>，与 WPF 行为一致；生产构建不会显示此区域。
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", maxWidth: 360 }}>
                <label className="muted">
                  工号
                  <input
                    type="text"
                    value={devEmployeeId}
                    onChange={(ev) => setDevEmployeeId(ev.target.value)}
                    disabled={devBusy}
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <label className="muted">
                  姓名
                  <input
                    type="text"
                    value={devDisplayName}
                    onChange={(ev) => setDevDisplayName(ev.target.value)}
                    disabled={devBusy}
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <label className="muted">
                  X-Api-Key（默认与后端 <code>sso.api-key</code> 一致）
                  <input
                    type="password"
                    autoComplete="off"
                    value={devApiKey}
                    onChange={(ev) => setDevApiKey(ev.target.value)}
                    disabled={devBusy}
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <button type="submit" className="primary" disabled={devBusy} style={{ alignSelf: "flex-start" }}>
                  {devBusy ? "请求中…" : "获取令牌并登录"}
                </button>
              </div>
            </form>
          )}
        </>
      )}
      {err && <p className="error">{err}</p>}
      {user && (
        <div>
          <p>
            <strong>工号：</strong> {user.employeeId}
          </p>
          <p>
            <strong>姓名：</strong> {user.displayName}
          </p>
        </div>
      )}
      <p className="muted" style={{ marginBottom: 0 }}>
        借记卡密码：同站演示见「密码收集」；<strong>档位 A（不信任 vendor）</strong>请另开终端启动{" "}
        <code>poc-vendor</code>（5174）嵌入银行源 <code>/bank/pin</code>（5173），详见根目录 README。
      </p>
    </div>
  );
}
