import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/register/index.vue')
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    meta: {
      requiresAuth: true
    },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue')
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/profile/index.vue')
      },
      {
        path: 'pets',
        name: 'Pets',
        component: () => import('../views/pets/index.vue')
      },
      {
        path: 'pets/create',
        name: 'PetCreate',
        component: () => import('../views/pets/create.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'pets/:id',
        name: 'PetDetail',
        component: () => import('../views/pets/detail.vue')
      },
      {
        path: 'pets/:id/edit',
        name: 'PetEdit',
        component: () => import('../views/pets/edit.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'pets/my-posts',
        name: 'MyPosts',
        component: () => import('../views/pets/my-posts.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'pets/collections',
        name: 'Collections',
        component: () => import('../views/pets/collections.vue'),
        meta: { requiresAuth: true }
      },      {        path: 'recycle',        name: 'Recycle',        component: () => import('../views/recycle/index.vue'),        meta: { requiresAuth: true }      },
      {        path: 'user/:id',        name: 'UserInfo',        component: () => import('../views/user/index.vue'),        meta: { requiresAuth: true }      },
      {
        path: 'activities',
        name: 'Activities',
        component: () => import('../views/activity/index.vue')
      },
      {
        path: 'activities/create',
        name: 'ActivityCreate',
        component: () => import('../views/activity/create.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'activities/:id',
        name: 'ActivityDetail',
        component: () => import('../views/activity/detail.vue')
      },
      {
        path: 'activities/:id/edit',
        name: 'ActivityEdit',
        component: () => import('../views/activity/edit.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'my-activities',
        name: 'MyActivities',
        component: () => import('../views/activity/my-activities.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'activities/recycle',
        name: 'ActivityRecycle',
        component: () => import('../views/activity/recycle.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/settings/index.vue')
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('../views/audit/index.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'audit/:id',
        name: 'AuditDetail',
        component: () => import('../views/audit/detail.vue'),
        meta: { requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router