import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { saveAccessToken } from "../auth";

export default function SsoCallbackPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [message, setMessage] = useState("正在完成 Azure SSO 登录...");

  useEffect(() => {
    const hash = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    const token = hash.get("access_token");
    const error = params.get("error");
    const description = params.get("error_description");

    if (token) {
      saveAccessToken(token);
      window.history.replaceState({}, "", window.location.pathname);
      navigate("/azure-sso", { replace: true });
      return;
    }

    setMessage(
      error
        ? `SSO 登录失败：${description || error}`
        : "没有在回调中找到 access_token，请从 Azure SSO 页面重新登录。"
    );
  }, [navigate, params]);

  return (
    <div className="card">
      <h1 style={{ marginTop: 0 }}>Azure SSO 回调</h1>
      <p className={message.includes("失败") ? "error" : "muted"}>{message}</p>
      <Link to="/azure-sso">返回 Azure SSO Demo</Link>
    </div>
  );
}
