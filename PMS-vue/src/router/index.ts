import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/pets'
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
        path: 'pets/create',
        name: 'PetCreate',
        component: () => import('../views/pets/create.vue')
      },
      {
        path: 'pets/:id/edit',
        name: 'PetEdit',
        component: () => import('../views/pets/edit.vue')
      },
      {
        path: 'pets/my-posts',
        name: 'MyPosts',
        component: () => import('../views/pets/my-posts.vue')
      },
      {
        path: 'pets/collections',
        name: 'Collections',
        component: () => import('../views/pets/collections.vue')
      },
      {
        path: 'recycle',
        name: 'Recycle',
        component: () => import('../views/recycle/index.vue')
      },
      {
        path: 'user/:id',
        name: 'UserInfo',
        component: () => import('../views/user/index.vue')
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/settings/index.vue'),
        meta: { isAdminOnly: true }
      },
      {
        path: 'message',
        name: 'Message',
        component: () => import('../views/message/index.vue')
      },
      {
        path: 'private-message',
        name: 'PrivateMessage',
        component: () => import('../views/private-message/index.vue')
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('../views/audit/index.vue')
      },
      {
        path: 'audit/:id',
        name: 'AuditDetail',
        component: () => import('../views/audit/detail.vue')
      }
    ]
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      {
        path: 'pets',
        name: 'Pets',
        component: () => import('../views/pets/index.vue')
      },
      {
        path: 'feed',
        name: 'Feed',
        component: () => import('../views/feed/index.vue')
      },
      {
        path: 'pets/:id',
        name: 'PetDetail',
        component: () => import('../views/pets/detail.vue')
      },
      {
        path: 'pets/activity/:id',
        name: 'ActivityDetail',
        component: () => import('../views/pets/activity-detail.vue')
      },
      {
        path: 'daily',
        name: 'Daily',
        component: () => import('../views/daily/index.vue')
      },
      {
        path: 'daily/publish',
        name: 'DailyPublish',
        component: () => import('../views/daily/publish.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'daily/:id',
        name: 'DailyDetail',
        component: () => import('../views/daily/detail.vue')
      },

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
  } else if (to.meta.isAdminOnly) {
    const userInfo = localStorage.getItem('userInfo')
    if (userInfo) {
      const info = JSON.parse(userInfo)
      if (info.role === 1) {
        next()
      } else {
        next('/dashboard')
      }
    } else {
      next('/login')
    }
  } else {
    next()
  }
})

export default router