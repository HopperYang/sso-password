# SSO Service POC

实现内容见根目录 [`requirements.md`](requirements.md)：Spring Boot 密码会话（外层 RSA 封装 AES + 内层 JSON；提交时 RSA+AES-GCM）、JWT 签发与校验、React POC 页面、Windows WPF + WebView2 拉起前端并带 Token。

## 前置条件

- JDK 21、Maven 3.9+
- Node 20+（前端）
- Windows + .NET 8 SDK（仅构建/运行 `poc-wpf`）

## 启动后端

```bash
cd sso-server
mvn spring-boot:run
```

默认端口 `8080`。可通过环境变量覆盖：

- `SSO_JWT_SECRET`：HS256 密钥（足够长）
- `SSO_API_KEY`：WPF / 脚本调用 `/api/auth/token` 时的 `X-Api-Key`
- `application.yml` 中 `sso.password-session-ttl-minutes`：密码会话 TTL

## 启动前端（开发）

```bash
cd poc-web
npm install
npm run dev
```

开发模式下 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`，因此 `fetch('/api/...')` 无需配置 `VITE_API_BASE`。

首页在 **`npm run dev` 且未带 `?token=`** 时，会显示 **「开发：模拟 WPF 取令牌」**：填写工号、姓名后点击即可调用 `POST /api/auth/token`（默认 API Key 与后端 `sso.api-key` 一致）。**生产构建 (`npm run build`) 不会包含该表单。** 也可在 macOS 等环境用 `?token=JWT` 手工传入。

生产构建静态资源时，若前后端不同源，可设置 `VITE_API_BASE` 为后端根 URL（例如 `http://localhost:8080`）。可选在 `poc-web/.env.local` 中设置 `VITE_SSO_API_KEY`（勿提交仓库）。

## 启动 WPF（Windows）

1. 先启动后端与前端（`npm run dev`）。
2. 按需编辑 `poc-wpf/appsettings.json`（`Sso:ApiBase`、`Sso:ApiKey`、`Poc:WebUrl`、工号 `Poc:EmployeeId` 等）。
3. 在仓库根目录执行：

```bash
cd poc-wpf
dotnet build
dotnet run
```

应用会使用 `X-Api-Key` 调用 `POST /api/auth/token`，再打开 WebView2 访问 `Poc:WebUrl/?token=...`。首页从 query 读取 Token 后调用 `GET /api/auth/me` 展示工号与姓名。

## API 摘要

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/token` | Header `X-Api-Key`，Body `employeeId`、`displayName`，返回 JWT |
| GET | `/api/auth/me` | Header `Authorization: Bearer <jwt>` |
| POST | `/api/crypto/password/session` | Body `clientPublicKeySpki`（Base64 SPKI），返回 `sessionId`（与内层明文一致）及外层信封 `encryptedKey` / `iv` / `ciphertext` / `tag` |
| POST | `/api/crypto/password/submit` | Body `sessionId`、`encryptedKey`、`iv`、`ciphertext`、`tag`（均 Base64） |

密码会话外层：服务端用客户端临时 RSA 公钥仅封装 **AES-256 密钥**；内层 JSON（`sessionId` + 服务端 RSA 公钥 SPKI）由 AES-GCM 保护。提交时浏览器用 **仅封装 AES 密钥** 的 RSA-OAEP 与服务端会话公钥一致。

## 安全说明

本仓库为 POC：JWT 放 URL、固定 API Key、内存会话等均不适合生产。上线前需替换为授权码换票、密钥托管、mTLS、审计与密钥轮换等方案。
