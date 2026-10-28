/**
 * 登录态的读写集中在这里，localStorage 的 key 只在这个文件里出现，
 * 拦截器、路由守卫、页面都从这里取。
 */

const TOKEN_KEY = "token";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
}
