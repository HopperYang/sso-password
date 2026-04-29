import { Link, Navigate, Route, Routes } from "react-router-dom";
import Home from "./pages/Home";
import PasswordPage from "./pages/PasswordPage";

export default function App() {
  return (
    <div className="layout">
      <nav className="row" style={{ marginBottom: "1.25rem" }}>
        <Link to="/">首页</Link>
        <Link to="/password">密码收集</Link>
      </nav>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/password" element={<PasswordPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}
