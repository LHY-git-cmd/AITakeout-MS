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
  document.title = `${String(to.meta.title ?? '在线点餐')} - 苍穹外卖`
})

export default router
