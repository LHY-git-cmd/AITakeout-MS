import Vue from 'vue'
import Router from 'vue-router'
import Layout from '@/layout/index.vue'
import {
  getToken,
  setToken,
  removeToken,
  getStoreId,
  setStoreId,
  removeStoreId,
  setUserInfo,
  getUserInfo,
  removeUserInfo,
} from '@/utils/cookies'
import store from '@/store'

Vue.use(Router)

const router = new Router({
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) {
      return savedPosition
    }
    return { x: 0, y: 0 }
  },
  base: process.env.BASE_URL,
  routes: [
    {
      path: '/login',
      component: () => import(/* webpackChunkName: "login" */ '@/views/login/index.vue'),
      meta: { title: '饱饱点餐', hidden: true, notNeedAuth: true },
    },
    {
      path: '/404',
      component: () => import(/* webpackChunkName: "404" */ '@/views/404.vue'),
      meta: { title: '饱饱点餐', hidden: true, notNeedAuth: true },
    },
    {
      path: '/',
      component: Layout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          component: () => import(/* webpackChunkName: "dashboard" */ '@/views/dashboard/index.vue'),
          name: 'Dashboard',
          meta: {
            title: '工作台',
            icon: 'dashboard',
            affix: true,
          },
        },
        {
          path: '/statistics',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/statistics/index.vue'),
          meta: {
            title: '数据统计',
            icon: 'icon-statistics',
          },
        },
        {
          path: 'order',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/orderDetails/index.vue'),
          meta: {
            title: '订单管理',
            icon: 'icon-order',
          },
        },
        {
          path: 'after-sale',
          component: () => import(/* webpackChunkName: "after-sale" */ '@/views/afterSale/index.vue'),
          meta: { title: '退款与售后', icon: 'icon-order' },
        },
        {
          path: '/after-sale/:id',
          component: () => import(/* webpackChunkName: "after-sale-detail" */ '@/views/afterSale/detail.vue'),
          meta: { title: '售后详情', hidden: true },
        },
        {
          path: 'setmeal',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/setmeal/index.vue'),
          meta: {
            title: '套餐管理',
            icon: 'icon-combo',
          },
        },
        {
          path: 'dish',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/dish/index.vue'),
          meta: {
            title: '菜品管理',
            icon: 'icon-dish',
          },
        },
        {
          path: '/dish/add',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/dish/addDishtype.vue'),
          meta: {
            title: '添加菜品',
            hidden: true,
          },
        },

        {
          path: 'category',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/category/index.vue'),
          meta: {
            title: '分类管理',
            icon: 'icon-category',
          },
        },
        {
          path: 'employee',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/employee/index.vue'),
          meta: {
            title: '员工管理',
            icon: 'icon-employee',
          },
        },
        {
          path: 'agent',
          component: () => import(/* webpackChunkName: "agent" */ '@/views/agent/index.vue'),
          meta: {
            title: 'AI助手',
            icon: 'icon-category',
          },
        },
        {
          path: 'knowledge',
          component: () => import(/* webpackChunkName: "knowledge" */ '@/views/knowledge/index.vue'),
          meta: { title: '知识库', icon: 'icon-category' },
        },
        {
          path: 'public-knowledge',
          component: () => import(/* webpackChunkName: "public-knowledge" */ '@/views/publicKnowledge/index.vue'),
          meta: { title: '用户公共知识库', icon: 'icon-category' },
        },
        {
          path: 'user-account',
          component: () => import(/* webpackChunkName: "user-account" */ '@/views/userAccount/index.vue'),
          name: 'UserAccount',
          meta: { title: '用户账户', icon: 'icon-wallet', roles: ['SUPER_ADMIN'] },
        },
        {
          path: '/employee/add',
          component: () => import(/* webpackChunkName: "dashboard" */ '@/views/employee/addEmployee.vue'),
          meta: {
            title: '添加员工',
            hidden: true,
          },
        },

        {
          path: '/setmeal/add',
          component: () => import(/* webpackChunkName: "shopTable" */ '@/views/setmeal/addSetmeal.vue'),
          meta: {
            title: '添加套餐',
            hidden: true,
          },
        },
      ],
    },
    {
      path: '*',
      redirect: '/404',
      meta: { hidden: true },
    },
  ],
})

export default router
