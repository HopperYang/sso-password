import { useEffect, useState } from "react";
import { fetchSsoLoginUrl, fetchSsoSession, logoutSso, SsoSession } from "../api";
import { clearAccessToken, saveAccessToken } from "../auth";

export default function AzureSsoPage() {
  const [session, setSession] = useState<SsoSession | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function refreshSession() {
    try {
      const next = await fetchSsoSession();
      saveAccessToken(next.accessToken);
      setSession(next);
      setError(null);
    } catch {
      setSession(null);
    }
  }

  useEffect(() => {
    void refreshSession();
  }, []);

  async function startLogin() {
    setBusy(true);
    setError(null);
    try {
      const loginUrl = await fetchSsoLoginUrl();
      window.location.assign(loginUrl);
    } catch {
      setError("获取 Azure 登录地址失败：请确认后端 Azure 配置已启用。");
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError(null);
    try {
      const logoutUrl = await logoutSso();
      clearAccessToken();
      setSession(null);
      window.location.assign(logoutUrl);
    } catch {
      setError("退出失败：请确认后端仍在运行。");
      setBusy(false);
    }
  }

  return (
    <div className="card">
      <h1 style={{ marginTop: 0 }}>Azure OIDC SSO Demo</h1>
      <p className="muted">
        前端调用 <code>GET /api/sso/login-url</code> 获取 Azure 授权地址；Azure 回调后端{" "}
        <code>/api/sso/callback</code>，后端校验 AD group 后签发应用 JWT。
      </p>

      <div className="row" style={{ margin: "1rem 0" }}>
        <button type="button" className="primary" onClick={startLogin} disabled={busy}>
          {busy ? "处理中..." : "使用 Azure 登录"}
        </button>
        <button type="button" onClick={refreshSession} disabled={busy}>
          刷新 session/me
        </button>
        {session && (
          <button type="button" className="ghost" onClick={logout} disabled={busy}>
            Logout
          </button>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      {session ? (
        <div>
          <h2>当前 SSO Session</h2>
          <p>
            <strong>账号：</strong> {session.user.employeeId}
          </p>
          <p>
            <strong>姓名：</strong> {session.user.displayName}
          </p>
          <p>
            <strong>Email：</strong> {session.user.email || "-"}
          </p>
          <p>
            <strong>JWT TTL：</strong> {session.expiresIn}s
          </p>
          <div>
            <strong>Azure AD Groups：</strong>
            {session.user.groups.length > 0 ? (
              <ul className="group-list">
                {session.user.groups.map((group) => (
                  <li key={group}>
                    <code>{group}</code>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">ID Token 中没有 groups claim；如配置了 required-groups，后端会拒绝登录。</p>
            )}
          </div>
          <label className="muted" style={{ display: "block", marginTop: "1rem" }}>
            给其他 API 使用的 Authorization Bearer JWT
            <textarea className="token-box" readOnly value={session.accessToken} />
          </label>
          <pre className="code-block">{`Authorization: Bearer ${session.accessToken}`}</pre>
        </div>
      ) : (
        <p className="muted">
          当前没有 SSO session。登录前请在 Azure App Registration 中启用 ID Token，并把 group claim 配到 token 中。
        </p>
      )}
    </div>
  );
}
