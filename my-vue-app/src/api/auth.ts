import request from "@/utils/request";
import type { ApiResponse, LoginRequest, LoginResult, UserInfo } from "@/types/api";

export const authApi = {
    /** 发送短信验证码 */
    async sendSms(phone: string): Promise<ApiResponse<string>> {
        const res = await request.post<ApiResponse<string>>("/sms/send", null, {
            params: { phone },
        });
        return res.data;
    },

    /** 短信验证码登录 */
    async login(data: LoginRequest): Promise<ApiResponse<LoginResult>> {
        const res = await request.post<ApiResponse<LoginResult>>("/sms/login", data);
        return res.data;
    },

    /** 查询当前登录用户，token 由请求拦截器带上 */
    async me(): Promise<ApiResponse<UserInfo>> {
        const res = await request.get<ApiResponse<UserInfo>>("/user/me");
        return res.data;
    },
};
