import { createRouter, createWebHistory } from 'vue-router'
import { auth, hasAnyRole } from '../store/auth'

/**
 * 路由守卫是“前端的第一道门”，但它只是体验层的拦截。
 * 真正的权限在后端，前端就算绕过去，接口照样返回 403。
 *
 * 这一点要讲清：很多学生的作业把权限判断全写在前端，改一下 localStorage 就进去了。
 */
export function homeOf() {
  if (hasAnyRole(['ADMIN'])) return '/users'
  if (hasAnyRole(['TEACHER'])) return '/review'
  return '/tasks'
}

const routes = [
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
