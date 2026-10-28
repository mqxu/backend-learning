/**
 * 前后端需要对齐的几个数字放在这里，改一处就能同时影响倒计时与提示文案。
 */

/** 后端接口地址，来自 .env 的 VITE_API_BASE_URL */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8006";

/** 重发验证码的间隔（秒），与后端 mqxu.sms.send-interval-seconds 一致 */
export const SEND_INTERVAL_SECONDS = 60;

/** 验证码有效期（分钟），与后端 mqxu.sms.code-expire-seconds 一致 */
export const CODE_TTL_MINUTES = 5;

/** 登录态失效的业务码，与后端 ErrorCode.TOKEN_INVALID 一致 */
export const CODE_TOKEN_INVALID = 40100;
