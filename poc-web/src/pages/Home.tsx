import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { fetchMe, issueToken } from "../api";

const TOKEN_KEY = "sso_access_token";
const isDev = import.meta.env.DEV;
const defaultApiKey = import.meta.env.VITE_SSO_API_KEY ?? "poc-dev-api-key";

export default function Home() {
  const [params] = useSearchParams();
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<{ employeeId: string; displayName: string } | null>(null);
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
    if (!token) {
      setUser(null);
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
              ? "令牌无效或已过期。可在下方「开发模拟登录」重新取令牌，或使用 WPF / ?token=。"
              : "令牌无效或已过期。请从 WPF 示例重新打开页面。"
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
      {!token && (
        <>
          <p className="muted">
            未检测到登录令牌。Windows 上可运行 <code>poc-wpf</code>；任意系统可用{" "}
            <code>?token=...</code> 传入 JWT，或在开发模式下使用下方模拟登录。
          </p>
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
        借记卡密码设置请前往「密码收集」子页；密码在浏览器内用会话公钥加密后提交。
      </p>
    </div>
  );
}
