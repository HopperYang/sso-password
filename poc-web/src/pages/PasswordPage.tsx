import EncryptedPinFlow from "../components/EncryptedPinFlow";

export default function PasswordPage() {
  return (
    <EncryptedPinFlow
      title="密码收集（POC）"
      pinLabel="借记卡密码（演示）"
      description={
        <>
          流程：生成临时 RSA 密钥 → 申请加密会话 → 解开外层 AES 包得到服务端公钥 → 本地 AES-GCM
          加密密码并用服务端 RSA 公钥封装密钥后提交。
        </>
      }
    />
  );
}
