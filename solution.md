面按你给的威胁模型说清楚：第三方 vendor 开发并托管页面，且要经过第三方中间系统调用行内 API。此时单靠「在页面上做一层 RSA/AES 加密再调 API」不能保证密码不被第三方拿到——因为 PIN 输入与加密逻辑若跑在 vendor 的 JS 里，vendor 随时可改代码，在加密前把密码抄走。TLS 只防链路上的外人，防不了托管方与恶意脚本。

目标与结论
要防的对象：vendor 运维、vendor CDN、被入侵的 vendor 发布链路、恶意前端版本、中间代理（在 TLS 终点之后）。
最佳实践核心：PIN 只能在「银行可控来源」里采集与加密；vendor 只负责编排、展示非敏感 UI、拿结果令牌，永不接触明文 PIN。
推荐方案（按优先级组合）
1. 银行托管的 PIN 采集面（必做）
任选一种可落地形态（可组合）：

形态	做法	作用
全页跳转
设置密码时 302 到 pin.bank.com/...，整页在银行域名下完成，结束再带着一次性结果令牌回 vendor
vendor 页面根本加载不到 PIN 输入逻辑
银行子域 iframe
vendor 页面嵌入 <iframe src="https://pin.bank.com/...">，仅银行源提供输入框与脚本；父页与 iframe 跨源，父页不能读 iframe 内 DOM/输入值
用户仍感觉在「一家」流程里，但 PIN 在银行源内
独立 WebView / 小程序银行页
App 内打开银行 URL，同上
移动端常见
配套技术建议：

严格 CSP、最小权限 iframe sandbox（按业务能收多紧收多紧）、银行侧 防点击劫持（X-Frame-Options / frame-ancestors）仅允许已登记的 vendor 父域嵌入。
PIN 页静态资源完整性（SRI / 银行自有构建与发布）、子资源全部同源或可信清单，降低 XSS 面。
短会话 + 一次性 nonce：iframe URL 带 vendor 签发的短寿命、单次使用的 state/nonce，银行校验后再展示 PIN 页，防重放与随意嵌套。
这样：明文 PIN 只存在于用户设备 + 银行可控页面的内存中；vendor 的 HTML/JS 看不到 iframe 里的输入（同源策略），除非用户被诱导到假页面（用 明确 URL/品牌展示 + 书签教育 缓解）。

2. 传输与行内 API：TLS 为基线，PIN 不落 vendor 服务
浏览器 → 银行：TLS 1.3，证书与域名归属银行；禁止把 PIN 明文 POST 到 vendor 域名再由 vendor 转发给银行（vendor 服务端会看到明文）。
行内 API：仅接受来自银行托管流程产生的请求（例如银行会话 Cookie 仅对 pin.bank.com 有效，或 mTLS / 设备绑定），不要设计「vendor 服务器代用户提交 PIN」的模型。
3. 协议层增强（在「银行托管采集」前提下才有意义）
在 PIN 已在银行源内的前提下，可再叠加：

OPAQUE / SRP 等 PAKE：网络上不出现可离线穷举的「密码等价物」；仍须配合银行托管客户端，否则恶意 vendor 仍可换实现窃密。
公钥封装 / 会话密钥：与你们 POC 类似，但密钥与脚本必须来自银行源；用于防 TLS 终结内侧监听等纵深防御，不是防 vendor 的主手段。
4. 合规与运营（银行场景常和「最佳」绑定）
PCI / 本地监管对 PIN 采集常有托管方、审计、渗透要求；vendor 只做「壳站」、不碰 PIN 通常更易过审。
发布与签名：vendor 只嵌入固定银行 URL 或经银行白名单校验的入口；银行侧监控异常嵌套与钓鱼。
不推荐当作「主防线」的做法
仅在 vendor 域名上用 Web Crypto 包一层再调行内 API：vendor 能改 JS，加密前截获。
把 PIN 发给 vendor 后端再由其调银行：vendor 服务器必然接触明文或等价物。
单靠「API Key + 自定义加密」：既不解决托管方信任，也不等价于监管认可的 PIN 处理。
一句话架构
第三方页面只做导流/嵌套；PIN 输入与提交逻辑 100% 在银行控制的 HTTPS 源（跳转页或跨源 iframe）内完成；成功后向 vendor 返回不可还原 PIN 的完成凭证（opaque token）。 这是在你给定前提下，兼顾防第三方中间系统与防恶意/被黑 vendor 前端的业界主路径。若你愿意补充「是否必须嵌在 vendor 同一页」「是否有 App WebView」等约束，可以再收敛成一种具体集成草图（URL 参数、postMessage 边界、令牌格式）。

---

## 算法定稿（与 requirements.md 一致）

应用层密码与会话信封在算法上统一采用 **RSA-OAEP-256 + AES-256-GCM** 混合加密：**RSA-OAEP-256** 即 **RSA-OAEP with SHA-256**（业界常称 RSA-OAEP-256）；RSA 仅封装随机 AES-256 密钥；密码明文与内层 JSON 仅由 **AES-256-GCM** 保护。详见根目录 [`requirements.md`](requirements.md) §1.1.1。