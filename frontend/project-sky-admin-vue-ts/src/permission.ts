import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { Message } from 'element-ui'
import { Route } from 'vue-router'
import { UserModule } from '@/store/modules/user'
import Cookies from 'js-cookie'
import { canAccessRoute } from '@/utils/routePermission'

NProgress.configure({ 'showSpinner': false })

function currentRoles() {
  if (UserModule.roles.length) return UserModule.roles
  try {
    const stored = Cookies.get('userInfo')
    return stored ? (JSON.parse(stored).roles || []) : []
  } catch (_) {
    return []
  }
}

router.beforeEach(async (to: Route, _: Route, next: any) => {
  NProgress.start()
  if (Cookies.get('token')) {
    if (canAccessRoute(to, currentRoles())) next()
    else next('/404')
  } else {
    if (!to.meta.notNeedAuth) {
      next('/login')
    } else {
      next()
    }
  }
})

router.afterEach((to: Route) => {
  NProgress.done()
  document.title = to.meta.title
})
