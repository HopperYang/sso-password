/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string;
  /** 仅开发：与后端 sso.api-key 一致，勿提交到仓库 */
  readonly VITE_SSO_API_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
