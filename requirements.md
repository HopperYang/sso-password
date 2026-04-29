# SSO 服务需求说明（已定稿）

## 已定稿决策（2026-04-24）

| 序号 | 议题 | 定稿 |
|------|------|------|
| 1 | 公钥下发的「二次报文加密」 | **方案 A**：应用层对「内含会话公钥的载荷」再加密，使公钥在传输中隐藏；配合 TLS。 |
| 2 | 密码本体加密 | **RSA + AES 混合**：随机 AES-256-GCM 密钥加密密码明文，RSA-OAEP(SHA-256) 仅封装 AES 密钥与必要元数据。 |
| 3 | 第三方 / 报文与登录 | **信任 SSO 签发的 Token 简化**：第三方或 POC H5 不持有与用户包相同的解密密钥；以 SSO 校验通过的 Token（或等效票据）建立登录态。 |
| 4 | 用户标识 | **工号**作为业务侧 `userId`（WPF 从 Windows 身份映射或 POC 配置；不以 SID 为主键展示）。 |
| 5 | POC 深度 | **接受 MVP**：会话与密钥可内存 + TTL，无真实用户库；验证加解密与展示流程即可。 |

---

## 1. 密码数据收集加密服务

### 1.1 目标

- 密码明文仅存在于用户浏览器输入过程与 SSO 服务端解密后的受控逻辑中（POC 可不落库，仅校验解密成功）。
- 传输链路上中间节点无法还原密码；会话级 RSA 公钥在链路上以**外层密文**形式传递。

### 1.2 二次报文加密（方案 A）

- 前端打开密码页时，先生成**临时 RSA 密钥对**（仅本页/本会话使用，私钥留在内存，不落本地存储）。
- 调用 SSO「创建密码加密会话」接口，请求体携带：**临时 RSA 公钥**（SPKI Base64 或 PEM）。
- SSO：生成**服务端会话 RSA 密钥对**（私钥仅服务端内存/缓存，绑定 `sessionId`，短 TTL，如 5–15 分钟）。
- SSO 构造内层明文载荷：`sessionId` + 用于加密密码的 **RSA 公钥**（SPKI）+ 可选算法标识。
- SSO 使用**前端提交的临时 RSA 公钥**对该内层载荷做 **RSA-OAEP(SHA-256)** 加密，将**外层密文**返回前端（可 Base64）。
- 前端用**临时 RSA 私钥**解密外层，得到 `sessionId` 与服务端 RSA 公钥，再用于 1.3 的混合加密。

> 说明：TLS 为第一层；上述为第二层，确保即使企业 HTTPS 解密场景下，无临时私钥的第三方仍无法拿到「密码加密用公钥」明文（POC 级威胁模型与性能可接受）。

### 1.3 密码混合加密（RSA + AES）

- 前端生成随机 **AES-256** 密钥与 IV/nonce，使用 **AES-GCM** 加密 UTF-8 密码明文，得到密文与 auth tag。
- 使用服务端下发的会话 **RSA 公钥** 对「AES 密钥 +（可选）IV」做 **RSA-OAEP(SHA-256)** 封装，得到 `encryptedKey`（若长度策略需要，可仅封装 AES 密钥，IV 可随密文明文传输由约定约束）。
- 提交接口：`sessionId`、`encryptedKey`、`ciphertext`、`iv`、`tag`（字段名可统一 snake_case 或 camelCase，前后端一致即可）。

### 1.4 服务端解密

- 校验 `sessionId` 有效且未过期；用会话私钥解 `encryptedKey` 得 AES 密钥；再 AES-GCM 解密得到密码明文（POC 可记录「成功/失败」，禁止日志打印明文密码）。

### 1.5 技术栈

- 后端：**Java 21 + Spring Boot 3.x**。
- 前端：**React** 密码收集子页；优先 **Web Crypto API** 与后端算法参数一致。
- 交付：可运行的密码收集 POC 页面（同 POC 站点子路由）。

---

## 2. 报文加密服务（与 Token 模型衔接）

- **MVP**：与「登录票据」统一为 **SSO 签发的 Token**（建议 **JWT**：`sub`=工号，声明 `name` 等展示字段，`exp` 短时效；签名算法 HS256 配置密钥或 RS256 POC 密钥）。
- WPF 侧：取得当前 Windows 用户后解析或映射为 **工号**，请求 SSO **签发 Token**（需服务端信任 WPF 调用方式：POC 可用共享 `client_secret` 或内网 API Key；生产应替换为 mTLS / 域机器凭证等）。
- H5 / 第三方：**不解密**原「用户信息密文」；携带 Token 调用 SSO **校验接口**（`GET /api/auth/me` 或 `POST /api/auth/introspect`）换取用户信息并建立前端会话。
- 若后续需「独立报文加密 API」，Payload 可与 JWT 内声明对齐，避免两套体系；本阶段以 Token 为准。

---

## 3. SSO 登录与 POC 首页

### 3.1 流程概要

1. WPF（**WebView2**）通过 Windows 身份得到当前用户，映射为 **工号** 与展示名。
2. WPF 调用 SSO **令牌签发接口**，获得 Token（或带 `code` 的跳转 URL，由 H5 换 Token——URL 过长时优先 code 换票）。
3. WebView2 打开 POC 站点 URL：`https://poc-host/?token=...` 或 `?code=...`（生产应避免长期把 JWT 放 query，POC 可简化）。
4. React 首页：用 Token 调 SSO 校验接口，展示当前用户（工号、姓名等）。
5. **密码收集页**为同站子路由（如 `/password`），走第 1 节流程。

### 3.2 第三方 H5（概念）

- 第三方需 SSO 登录时：**重定向到 SSO 或使用 POC 同款 Token**，第三方后端或 BFF 向 SSO 校验 Token；本仓库 POC 以「信任 Token」演示，不实现完整 OIDC 发现文档，除非后续扩展。

### 3.3 验收（POC）

- WPF 启动后打开 WebView2，首页显示与配置/映射一致的工号与用户信息。
- 密码页：申请会话 → 混合加密提交 → 服务端解密成功（无用户库亦可）。

---

## 4. 非功能与安全（POC）

- **HTTPS**：部署侧启用 TLS；本地开发可用 HTTP 但需知风险。
- **日志**：禁止输出密码明文、完整 Token、私钥材料。
- **CORS**：仅允许 POC 前端来源；WebView2  origin 按实际 file:// 或 https:// 配置。
- **配置**：JWT 密钥、API Key、会话 TTL 等走 `application.yml` / 环境变量。

---

## 5. 原始功能列表（保留）

1. 提供密码数据收集加密服务（含 React POC、Spring Boot 后端）。
2. 提供报文加密服务（MVP 与 Token/JWT 模型合并实现）。
3. 提供 SSO 登录功能（WPF + WebView2 + POC 首页；密码页为子页面；工号为用户 id；信任 Token）。
