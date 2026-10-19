/// <reference types="vite/client" />

interface ImportMetaEnv {
    /** 后端接口地址，在 .env 里配置 */
    readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
