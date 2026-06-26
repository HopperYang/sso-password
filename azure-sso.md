# 技术落地PRD与开发指南：Microsoft Entra ID SSO 集成（BFF模式）

## 1. 总体架构与设计模式
本项目采用 **BFF (Backend For Frontend)** 架构实现单页应用 (SPA) 与 Microsoft Entra ID (Azure AD) 的单点登录 (SSO) 集成。
- **核心逻辑**：所有的 OAuth2 授权码流程、令牌（Token）交换及存储逻辑均在 Java 后端处理。
- **会话机制**：认证成功后，后端生成 `JSESSIONID` 并写入浏览器 Cookie，前端通过安全的 Session Cookie 与后端维持会话状态，前端不直接接触任何微软的敏感令牌（如 Access Token）。
- **Entra ID 应用注册类型**：必须在 Azure 控制台中注册为 `Web` 类型，绝对不能选择 SPA 类型。
- **重定向 URL (Redirect URI)**：`http://localhost:8080/login/oauth2/code/azure` (由 Spring Security 默认接管)。

---

## 2. 认证交互流详细设计
请严格遵循以下步骤实现全链路代码：
1. **状态检查与拦截**：用户打开前端（`localhost:3000`），前端初始化时或任意请求遭遇 `401 Unauthorized` 时，向后端发送状态检查请求。若未登录，前端执行：`window.location.href = "http://localhost:8080/oauth2/authorization/azure";`
2. **后端引导至微软**：Spring Security 拦截该请求，自动生成 OIDC 授权请求 URL，将用户浏览器 302 重定向至 Microsoft Entra ID 登录页。
3. **微软认证与回调**：用户完成登录，微软将浏览器重定向回调至后端的 Redirect URI：`http://localhost:8080/login/oauth2/code/azure?code=授权码&state=状态值`
4. **后端换取令牌与建立会话**：Spring Security 的 `OAuth2LoginAuthenticationFilter` 自动拦截此回调，在后台换取 `Access Token` 和 `ID Token`，验证成功后创建 Session，并将 `JSESSIONID` 写入浏览器 Cookie（设置 HttpOnly 属性）。
5. **重定向回前端**：后端通过 `defaultSuccessUrl` 将用户浏览器再次重定向跳转回前端主页：`http://localhost:3000/dashboard`。

---

## 3. 后端 Java Service (Spring Boot) 实现规范

### 3.1 依赖管理 (pom.xml)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
</dependencies>
