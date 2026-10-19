import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";
import { getToken } from "@/utils/auth";

const routes: Array<RouteRecordRaw> = [
    {
        path: "/",
        redirect: "/home",
    },
    {
        path: "/login",
        name: "Login",
        component: () => import("@/views/Login.vue"),
    },
    {
        path: "/home",
        name: "Home",
        component: () => import("@/views/Home.vue"),
        meta: {requiresAuth: true},
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

// 路由守卫只做“本地有没有 token”这一层拦截，token 是否真的有效由 /user/me 判断
router.beforeEach((to) => {
    if (to.meta.requiresAuth && !getToken()) {
        return "/login";
    }
    return true;
});

export default router;
