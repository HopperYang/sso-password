# SSO Service POC

实现内容见根目录 [`requirements.md`](requirements.md)。后端为 Spring Boot：**RSA-OAEP-256 + AES-256-GCM** 混合加密（会话信封 + PIN 提交）、JWT 签发与校验。两套前端 POC 共用同一 `sso-server`，差别在**谁托管页面、是否模拟第三方**。

## 两套 POC 方案（实现方式）

| | **方案一：同站演示 + WPF** | **方案二：档位 A（银行源 PIN + Vendor 壳站）** |
|---|---------------------------|-----------------------------------------------|
| **目的** | 一条命令跑通 SSO 登录、密码加密提交、桌面 WebView2 带 Token 打开站点 | 模拟「第三方托管壳站 + 银行托管 PIN」：跨源 iframe，父页脚本无法读 PIN |
| **前端工程** | 仅 [`poc-web`](poc-web/)（Vite + React，默认端口 **5173**） | [`poc-web`](poc-web/)（银行 PIN 页）+ [`poc-vendor`](poc-vendor/)（极简壳站，端口 **5174**） |
| **登录 / 身份** | 首页 `?token=` 或开发表单调 `POST /api/auth/token`；`GET /api/auth/me` 展示用户 | 不强制改登录模型；PIN 页经 `5173` 同源调 `/api`（与方案一共后端） |
| **密码采集路由** | [`/password`](poc-web/src/pages/PasswordPage.tsx)：`EncryptedPinFlow` 默认策略，单行 [`SecurePinField`](poc-web/src/components/SecurePinField.tsx)（`type=password`） | [`/bank/pin`](poc-web/src/pages/BankPinPage.tsx)：`pinPolicy="bankCard"`，[**6 圆点**](poc-web/src/components/PinSixCirclesInput.tsx) + 首屏输满 6 位后自动进入「请再次输入」+ 两遍一致后提交；[`?embed=1`](poc-web/src/App.tsx) 时隐藏顶栏便于 iframe |
| **跨源与嵌入** | 无（单源） | `poc-vendor` 内嵌 `http://localhost:5173/bank/pin?embed=1`；`poc-web` 开发服务器设置 **`Content-Security-Policy: frame-ancestors`**，仅允许 `localhost:5174` 等嵌入 |
| **桌面客户端** | [`poc-wpf`](poc-wpf/)：WebView2 打开 `Poc:WebUrl/?token=...` | 本方案 POC 以浏览器双端口为主；WPF 仍可只打开银行 URL 作类比 |
| **启动命令** | `sso-server` + `cd poc-web && npm run dev`；可选 `poc-wpf` | 在方案一基础上再启 `cd poc-vendor && npm run dev`，浏览器访问 **http://localhost:5174** |

两套方案的**密码学协议相同**（`POST /api/crypto/password/session` → 解信封 → `POST /api/crypto/password/submit`），差异在**交互形态与信任边界演示**。

### 优缺点与安全风险（概览）

以下针对**本仓库 POC 实现**；生产环境需另行威胁建模与渗透测试。

#### 方案一：同站演示 + WPF

| 类型 | 说明 |
|------|------|
| **优点** | 架构简单，单前端源、单 dev 命令即可跑通登录 + 加密提交流程；与 `poc-wpf`、JWT、`/api/auth/me` 串联完整，便于开发与联调。 |
| **缺点** | **未区分「第三方不可信」边界**：密码页与首页同源，不模拟 vendor 托管；若业务上由第三方整站托管，本方案**不能**体现跨源隔离与嵌入策略。 |
| **典型风险 / 漏洞面** | **同源 XSS** 或恶意扩展仍可在加密前访问 DOM/输入；开发态 **JWT 放 query**、**固定 API Key** 易泄露会话与代发令牌能力；**无 `frame-ancestors` 等嵌入策略**（因无 iframe 场景）；用户若访问钓鱼站则与方案无关。 |

#### 方案二：档位 A（Vendor 壳站 + 银行源 iframe）

| 类型 | 说明 |
|------|------|
| **优点** | **跨源**：父页面（5174）与 PIN 页（5173）不同源，父页脚本**不能**读取 iframe 内 DOM/输入，利于说明「采集面在银行源」；开发服务器对银行页设置 **`frame-ancestors`**，演示限制谁可嵌入；`/bank/pin` 使用 **6 圆点 + 双屏确认**，降低误触与明文展示。 |
| **缺点** | 需同时起两个前端；iframe 在部分 WebView/内嵌浏览器中行为需单独测；**POC 仅白名单 `localhost:5174`**，上生产需改为真实 vendor 域并同步 CSP。 |
| **典型风险 / 漏洞面** | **钓鱼**：用户若打开假域名假页面，同源策略帮不上忙；**银行源 XSS / 供应链** 仍可篡改银行页脚本，在加密前窃 PIN；**点击劫持 / 叠层**（父页透明覆盖诱导点击）需依赖 CSP、`frame-ancestors` 与交互设计缓解，POC 未穷尽；**错误配置 CSP** 时可能允许恶意父域嵌入；**侧信道**（日志、APM、键盘记录、剪贴板）仍存在。 |

#### 两套方案与后端 POC 的共用局限

| 类型 | 说明 |
|------|------|
| **实现层面** | 会话私钥 **内存 + TTL**、无 HSM；`/submit` POC **不落库**，仅日志示意解密成功；**无会话/提交频控**、无设备绑定；**HTTPS 未在文档流程中强制**（本地常为 HTTP）。 |
| **密码学层面** | 混合加密防「链路与部分 TLS 终结侧」窥视内层公钥与密文，**不**等价于防钓鱼、防恶意宿主、防终端木马。 |

文末 [安全说明](#安全说明) 补充生产级改造方向。

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
- `sso.password-session.ttl-minutes`：密码会话 TTL
- `sso.password-session.redis-enabled` / 环境变量 `SSO_PASSWORD_SESSION_REDIS_ENABLED`：是否用 Redis 存会话私钥（多机 + LB 时设为 `true`）
- `sso.password-session.redis-key-prefix` / 环境变量 `SSO_PASSWORD_SESSION_REDIS_KEY_PREFIX`：Redis 会话 key 前缀（默认 `sso:pwd-session:`，多环境/多租户可区分）
- Redis 连接：`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`（仅 `redis-enabled=true` 时需要）

## 启动前端（开发）

```bash
cd poc-web
npm install
npm run dev
```

开发模式下 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`，因此 `fetch('/api/...')` 无需配置 `VITE_API_BASE`。

首页在 **`npm run dev` 且未带 `?token=`** 时，会显示 **「开发：模拟 WPF 取令牌」**：填写工号、姓名后点击即可调用 `POST /api/auth/token`（默认 API Key 与后端 `sso.api-key` 一致）。**生产构建 (`npm run build`) 不会包含该表单。** 也可在 macOS 等环境用 `?token=JWT` 手工传入。

生产构建静态资源时，若前后端不同源，可设置 `VITE_API_BASE` 为后端根 URL（例如 `http://localhost:8080`）。可选在 `poc-web/.env.local` 中设置 `VITE_SSO_API_KEY`（勿提交仓库）。

## 档位 A 启动步骤（方案二）

1. 与上文相同，先启动 **sso-server** 与 **poc-web**（`npm run dev`，监听 **5173**）。
2. 再开一终端：

```bash
cd poc-vendor
npm install
npm run dev
```

3. 浏览器打开 **http://localhost:5174**：黄色背景的页面为「第三方」壳站；内嵌 iframe 为银行源 PIN 页。也可直接访问 **http://localhost:5173/bank/pin** 对比同页独立打开（带导航栏）与 **http://localhost:5173/bank/pin?embed=1**（嵌入时隐藏导航栏）。

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

密码会话外层：服务端用客户端临时 RSA 公钥以 **RSA-OAEP-256** 仅封装 **AES-256 密钥**；内层 JSON（`sessionId` + 服务端 RSA 公钥 SPKI）由 **AES-256-GCM** 保护。提交时浏览器以 **RSA-OAEP-256** 仅封装 AES 密钥，与服务端会话公钥算法一致（详见 `requirements.md` §1.1.1）。

## 安全说明

本仓库为 **POC**，除上表所列外：JWT 放 URL、固定 API Key、内存会话等均不适合生产。上线前建议至少包括：**授权码换票**、**密钥托管（HSM/KMS）**、**mTLS / 设备信任**、**限流与风控**、**全站 HTTPS 与 HSTS**、**CSP / 子资源完整性**、**审计日志（禁止 PIN 明文）**、**密钥与会话轮换**；档位 A 场景另需 **生产域名级 `frame-ancestors` 白名单** 与 **anti-clickjacking** 策略评审。
