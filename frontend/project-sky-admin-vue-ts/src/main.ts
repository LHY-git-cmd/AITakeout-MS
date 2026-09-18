import Vue from 'vue'
import Router from 'vue-router'
import 'normalize.css'
import ElementUI from 'element-ui'
import SvgIcon from 'vue-svgicon'
import VueAreaLinkage from 'vue-area-linkage'
import moment from 'moment'
import '@/styles/element-variables.scss'
import '@/styles/index.scss'
import '@/styles/home.scss'
import 'vue-area-linkage/dist/index.css'

import * as echarts from 'echarts'
// 瑞吉外卖样式表
import '@/styles/newRJWMsystem.scss'
import '@/styles/icon/iconfont.css'
// 平衡型深色星空主题（最后引入，确保覆盖上面的浅色样式）
import '@/styles/theme-dark.scss'
// 白天模式（必须在深色主题之后引入，才能覆盖深色变量与覆盖层）
import '@/styles/theme-light.scss'
import App from '@/App.vue'
import store from '@/store'
import router from '@/router'
import '@/icons/components'
import '@/permission'
import { checkProcessEnv } from '@/utils/common'

Vue.use(ElementUI)
Vue.use(VueAreaLinkage)
Vue.use(SvgIcon, {
  'tagName': 'svg-icon',
  'defaultWidth': '1em',
  'defaultHeight': '1em'
})

Vue.config.productionTip = false
Vue.prototype.moment = moment
Vue.prototype.$checkProcessEnv = checkProcessEnv
const routerPush = Router.prototype.push
Router.prototype.push = function push(location) {
 return routerPush.call(this, location).catch(error=> error)
}
Vue.prototype.$echarts = echarts
new Vue({
  router,
  store,
  'render': (h) => h(App)
}).$mount('#app')
