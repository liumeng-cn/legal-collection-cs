import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/chat' },
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue') },
  { path: '/chat', name: 'Chat', component: () => import('../views/ChatView.vue') },
  { path: '/diagnose', name: 'Diagnose', component: () => import('../views/DiagnoseView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/chat' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !getToken()) {
    return '/login'
  }
  if (to.path === '/login' && getToken()) {
    return '/chat'
  }
  return true
})

export default router
