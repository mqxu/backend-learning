import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { auth, hasAnyRole } from '../store/auth'
import type { RoleCode } from '../types/api'

/**
 * 给路由元信息加类型，写 meta.roles 时 IDE 会提示可选值，
 * 写错角色名在 typecheck 阶段就报错，不用等运行时白屏。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** true 表示不需要登录 */
    public?: boolean
    /** 允许进入的角色，不写表示登录即可 */
    roles?: RoleCode[]
  }
}

/**
 * 路由守卫是前端的第一道门，但它只是体验层的拦截。
 * 真正的权限在后端，前端就算绕过去，接口照样返回 403。
 */
export function homeOf(): string {
  if (hasAnyRole(['ADMIN'])) return '/users'
  if (hasAnyRole(['TEACHER'])) return '/review'
  return '/tasks'
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('../views/TaskList.vue'),
    meta: { roles: ['STUDENT'] },
  },
  {
    path: '/review',
    name: 'review',
    component: () => import('../views/ReviewList.vue'),
    meta: { roles: ['TEACHER', 'ADMIN'] },
  },
  {
    path: '/users',
    name: 'users',
    component: () => import('../views/Users.vue'),
    meta: { roles: ['ADMIN'] },
  },
  { path: '/', redirect: () => homeOf() },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) {
    // 角色不对时回到自己的首页，而不是踢回登录页
    return homeOf()
  }
  return true
})

export default router
