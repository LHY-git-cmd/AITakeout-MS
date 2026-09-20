import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'menu',
          component: () => import('@/views/MenuView.vue'),
          meta: { title: '点餐' },
        },
        {
          path: 'checkout',
          name: 'checkout',
          component: () => import('@/views/CheckoutView.vue'),
          meta: { title: '确认订单' },
        },
        {
          path: 'payment/result',
          name: 'payment-result',
          component: () => import('@/views/PaymentResultView.vue'),
          meta: { title: '支付结果' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: '订单' },
        },
        {
          path: 'orders/:id',
          name: 'order-detail',
          component: () => import('@/views/OrderDetailView.vue'),
          meta: { title: '订单详情' },
        },
        {
          path: 'addresses',
          name: 'addresses',
          component: () => import('@/views/AddressesView.vue'),
          meta: { title: '收货地址' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '我的' },
        },
        {
          path: 'wallet',
          name: 'wallet',
          component: () => import('@/views/WalletView.vue'),
          meta: { title: '模拟钱包' },
        },
        {
          path: 'profile/edit',
          name: 'profile-edit',
          component: () => import('@/views/ProfileEditView.vue'),
          meta: { title: '编辑个人资料' },
        },
        {
          path: 'profile/security',
          name: 'profile-security',
          component: () => import('@/views/SecuritySettingsView.vue'),
          meta: { title: '账号与安全' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/NotificationsView.vue'),
          meta: { title: '通知中心' },
        },
        {
          path: 'after-sales',
          name: 'after-sales',
          component: () => import('@/views/AfterSaleView.vue'),
          meta: { title: '退款与售后' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

router.afterEach((to) => {
  document.title = '智能点餐平台'
})

export default router
