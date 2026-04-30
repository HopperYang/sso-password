import { useEffect, useRef } from "react";

const MAX = 6;

function onlyDigits(value: string): string {
  return value.replace(/\D/g, "").slice(0, MAX);
}

type Props = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  autoFocus?: boolean;
  "aria-label": string;
};

/**
 * 页面上仅展示 6 个圆点占位；真实输入在透明层，圆点内仅显示填充点，不展示数字。
 */
export default function PinSixCirclesInput({
  id,
  value,
  onChange,
  disabled,
  autoFocus,
  "aria-label": ariaLabel,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus) {
      const t = window.setTimeout(() => inputRef.current?.focus(), 50);
      return () => window.clearTimeout(t);
    }
  }, [autoFocus]);

  return (
    <div className="pin-circles-wrap">
      <input
        ref={inputRef}
        id={id}
        type="tel"
        name="pin-circles"
        inputMode="numeric"
        pattern="[0-9]*"
        autoComplete="off"
        autoCorrect="off"
        spellCheck={false}
        autoCapitalize="off"
        enterKeyHint="done"
        maxLength={MAX}
        className="pin-circles-input"
        value={value}
        disabled={disabled}
        aria-label={ariaLabel}
        onChange={(e) => onChange(onlyDigits(e.target.value))}
      />
      <div className="pin-circles-row" aria-hidden="true">
        {Array.from({ length: MAX }, (_, i) => (
          <span
            key={i}
            className={i < value.length ? "pin-circle pin-circle--filled" : "pin-circle"}
          />
        ))}
      </div>
    </div>
  );
}
