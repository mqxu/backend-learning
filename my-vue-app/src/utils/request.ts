/**
 * axios 封装：统一 baseURL、统一带 token、统一按业务码判断成败。
 * 后端约定 HTTP 状态码恒为 200，成功与否看响应体里的 code。
 */
import axios, {
    type AxiosError,
    type AxiosInstance,
    type AxiosResponse,
    type InternalAxiosRequestConfig,
} from "axios";
import { ElMessage } from "element-plus";
import { API_BASE_URL, CODE_TOKEN_INVALID } from "@/config";
import { clearToken, getToken } from "@/utils/auth";
import type { ApiResponse } from "@/types/api";

const request: AxiosInstance = axios.create({
    baseURL: API_BASE_URL,
    timeout: 10000,
    headers: {
        "Content-Type": "application/json;charset=UTF-8",
    },
});

/**
 * 请求拦截器：有 token 就带上，后端 AuthInterceptor 靠这个头认人
 */
request.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const token = getToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error),
);

/**
 * 响应拦截器：
 * 业务码 200 原样返回；40100 清掉登录态并回登录页；其余把后端的 message 包成 Error 抛出。
 * 调用方在 catch 里拿到的就是后端文案，不需要自己判断 code。
 */
request.interceptors.response.use(
    (response: AxiosResponse<ApiResponse>) => {
        const body = response.data;
        if (body.code === 200) {
            return response;
        }

        const message = body.message || "请求失败";
        if (body.code === CODE_TOKEN_INVALID) {
            clearToken();
            ElMessage.warning(message);
            // 用 replace 换掉当前页，避免用户点后退又回到失效的页面
            if (window.location.pathname !== "/login") {
                window.location.replace("/login");
            }
        }
        return Promise.reject(new Error(message));
    },
    (error: AxiosError) => {
        // 走到这里说明请求没能拿到正常响应：服务没起、断网、超时、HTTP 5xx
        const message = error.response
            ? `请求失败：HTTP ${error.response.status}`
            : "网络连接失败，请确认后端服务已启动";
        ElMessage.error(message);
        return Promise.reject(new Error(message));
    },
);

export default request;
