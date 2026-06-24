import { Link, Navigate, Route, Routes, useLocation, useSearchParams } from "react-router-dom";
import Home from "./pages/Home";
import BankPinPage from "./pages/BankPinPage";
import PasswordPage from "./pages/PasswordPage";
import AzureSsoPage from "./pages/AzureSsoPage";
import SsoCallbackPage from "./pages/SsoCallbackPage";

export default function App() {
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const embedBank =
    pathname === "/bank/pin" && searchParams.get("embed") === "1";

  return (
    <div className="layout">
      {!embedBank && (
        <nav className="row" style={{ marginBottom: "1.25rem" }}>
          <Link to="/">首页</Link>
          <Link to="/azure-sso">Azure SSO</Link>
          <Link to="/password">密码收集（同站）</Link>
          <Link to="/bank/pin">行内 PIN（档位 A）</Link>
        </nav>
      )}
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/azure-sso" element={<AzureSsoPage />} />
        <Route path="/sso/callback" element={<SsoCallbackPage />} />
        <Route path="/password" element={<PasswordPage />} />
        <Route path="/bank/pin" element={<BankPinPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
