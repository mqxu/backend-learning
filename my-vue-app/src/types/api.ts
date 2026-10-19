/**
 * 与后端 top.mqxu.redis.common.Result 一一对应的响应体。
 * 字段名是 message，后端不叫 msg，写错拿到的永远是 undefined。
 */
export interface ApiResponse<T = unknown> {
    code: number;
    message: string;
    data: T;
}

/** POST /sms/login 的入参 */
export interface LoginRequest {
    phone: string;
    code: string;
}

/** POST /sms/login 的 data 部分 */
export interface LoginResult {
    token: string;
    phone: string;
    message: string;
    /** token 有效期，单位秒 */
    expiresIn: number;
}

/** GET /user/me 的 data 部分 */
export interface UserInfo {
    phone: string;
    loginTime: string;
}
