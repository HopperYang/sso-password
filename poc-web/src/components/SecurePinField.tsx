import { InputHTMLAttributes } from "react";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "inputMode"> & {
  /** 典型银行卡 PIN 为 6 位数字 */
  maxDigits?: number;
};

/**
 * 行内托管场景下的 PIN 输入：始终 type=password（不提供「显示明文」）；数字键盘、限制长度。
 * 不替代 TLS / 同源策略；与档位 A「银行源采集」配合使用。
 */
export default function SecurePinField({
  maxDigits = 6,
  id,
  name = "bank-pin",
  "aria-label": ariaLabel = "银行卡 PIN",
  ...rest
}: Props) {
  const fieldId = id ?? "bank-pin-input";
  return (
    <input
      id={fieldId}
      type="password"
      name={name}
      inputMode="numeric"
      pattern="[0-9]*"
      autoComplete="off"
      autoCorrect="off"
      spellCheck={false}
      maxLength={maxDigits}
      enterKeyHint="done"
      aria-label={ariaLabel}
      autoCapitalize="off"
      data-lpignore="true"
      data-form-type="other"
      {...rest}
    />
  );
}
