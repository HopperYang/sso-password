import "./style.css";

/** 银行前端（poc-web）开发服务器；与 vendor 不同源以演示 iframe 隔离 */
const BANK_PIN_URL = "http://localhost:3000/bank/pin?embed=1";

document.querySelector<HTMLDivElement>("#app")!.innerHTML = `
  <main class="vendor">
    <h1>第三方 Vendor 壳站（档位 A POC）</h1>
    <p class="lead">
      本页模拟<strong>不可信托管环境</strong>（本机端口 <code>5174</code>）。下方 iframe 指向<strong>银行源</strong>
      （<code>3000</code>）；父页面脚本无法跨源读取 iframe 内密码框内容。
    </p>
    <p class="hint">
      请先启动：<code>sso-server</code>（8080）与 <code>poc-web</code>（3000）。本目录仅提供壳页，不代理 API。
    </p>
    <section class="frame-wrap">
      <iframe
        title="银行 PIN（银行源）"
        src="${BANK_PIN_URL}"
        referrerpolicy="strict-origin-when-cross-origin"
        loading="lazy"
      ></iframe>
    </section>
  </main>
`;
