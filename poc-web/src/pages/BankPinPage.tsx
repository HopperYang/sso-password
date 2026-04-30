import EncryptedPinFlow from "../components/EncryptedPinFlow";

/**
 * 档位 A：仅在「银行源」加载的 PIN 页；vendor 壳站通过 iframe 嵌入本 URL（跨源），父页面无法读取框内输入。
 */
export default function BankPinPage() {
  return (
    <EncryptedPinFlow
      title="行内托管 PIN（档位 A POC）"
      pinPolicy="bankCard"
      pinLabel="银行卡 PIN"
      description={
        <>
          本页由<strong>银行前端源</strong>提供脚本与输入控件；第三方 vendor 页面仅可嵌入 iframe，无法通过 DOM
          读取 PIN（同源策略）。算法仍为 RSA-OAEP-256 + AES-256-GCM，详见仓库 requirements.md。
        </>
      }
    />
  );
}
