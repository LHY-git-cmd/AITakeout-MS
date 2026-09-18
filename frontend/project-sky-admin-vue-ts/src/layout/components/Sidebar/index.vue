<template>
  <div>
    <div class="logo">
      <!-- 展开显示 AITakeout-MS，折叠显示 MS；尺寸适配 190px / 80px -->
      <div class="brand">
        <div class="brand-mark">MS</div>
        <div v-if="!isCollapse" class="brand-text">AITakeout<b>-MS</b></div>
      </div>
    </div>
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu :default-openeds="defOpen"
               :default-active="defAct"
               :collapse="isCollapse"
               :background-color="variables.menuBg"
               :text-color="variables.menuText"
               :active-text-color="variables.menuActiveText"
               :unique-opened="false"
               :collapse-transition="false"
               mode="vertical">
        <el-menu-item v-for="route in routes"
                      :key="route.path"
                      :index="resolveRoute(route.path)"
                      @click="navigate(route.path)">
          <i v-if="route.meta && route.meta.icon" class="iconfont" :class="route.meta.icon" />
          <span slot="title">{{ route.meta && route.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-scrollbar>
    <!-- 左下角：主题切换 + 账户（修改密码/退出登录）+ 营业状态设置 -->
    <div class="side-bottom">
      <account-panel :collapsed="isCollapse" />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import { AppModule } from '@/store/modules/app'
import { UserModule } from '@/store/modules/user'
import AccountPanel from './AccountPanel.vue'
@Component({
  name: 'SideBar',
  components: { AccountPanel },
})
export default class extends Vue {
  get defOpen() {
    // const urlArr = this.$route.path.split('/')
    // const openStr = urlArr.length > 2 ? `/${urlArr[1]}` : '/'
    let path = ['/']
    this.routes.forEach((n: any, i: number) => {
      if (n.meta.roles && n.meta.roles[0] === this.roles[0]) {
        path.splice(0, 1, n.path)
      }
    })
    return path
  }

  get defAct() {
    let path = this.$route.path
    return path
  }

  get sidebar() {
    return AppModule.sidebar
  }

  get roles() {
    return UserModule.roles
  }

  get routes() {
    let routes = JSON.parse(
      JSON.stringify([...(this.$router as any).options.routes])
    )
    let menuList = []
    let menu = routes.find(item => item.path === '/')
    if (menu && menu.children) {
      // Only visible first-level routes are rendered in the sidebar.
      menuList = menu.children.filter((item: any) => !item.meta || !item.meta.hidden)
    }
    return menuList
  }

  private resolveRoute(routePath: string) {
    return routePath.charAt(0) === '/' ? routePath : `/${routePath}`
  }

  private navigate(routePath: string) {
    const target = this.resolveRoute(routePath)
    if (this.$route.path !== target) this.$router.push(target)
  }

  get variables() {
    // 深色半透明侧栏：底色与 main-container 的深空表面保持一致
    return {
      menuBg: 'transparent',
      menuText: '#c2cde4',
      menuActiveText: '#8fb5ff'
    }
  }

  get isCollapse() {
    return !this.sidebar.opened
  }
}
</script>

<style lang="scss" scoped>
/* ---------------------------------------------------------------------------
   品牌区：展开 AITakeout-MS，折叠 MS（尺寸适配 190px / 80px 侧栏）
   立体感只用少量文字阴影 + 轻微倾斜，不生成多层 DOM。
   动画仅此一处（配合星空共两处），并在 prefers-reduced-motion 下停用。
   --------------------------------------------------------------------------- */
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(143, 181, 255, 0.14), rgba(242, 168, 216, 0.06) 62%);
  border-bottom: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  position: relative;
  overflow: hidden;
}

/* 微光扫过：慢速、低亮度，不与星空抢注意力 */
.logo::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 36%, rgba(255, 214, 92, 0.16) 50%, transparent 64%);
  transform: translateX(-120%);
  animation: brand-sheen 9s ease-in-out infinite;
  pointer-events: none;
}

@keyframes brand-sheen {
  0%,
  62% {
    transform: translateX(-120%);
  }
  82%,
  100% {
    transform: translateX(120%);
  }
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  position: relative;
  z-index: 1;
  width: 100%;
}

.brand-mark {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: -0.5px;
  // 淡蓝 → 淡粉 渐变（替代原品牌黄）
  color: var(--text-on-accent);
  text-shadow: 0 1px 2px rgba(30, 41, 99, 0.35);
  background: linear-gradient(140deg, #7cc4ff 0%, #8fb5ff 45%, #f2a8d8 100%);
  box-shadow: 0 4px 14px rgba(124, 196, 255, 0.34), inset 0 1px 0 rgba(255, 255, 255, 0.7);
  transform: perspective(220px) rotateX(9deg) rotateY(-7deg);
  transition: transform 0.3s ease, box-shadow 0.3s ease, filter 0.3s ease;
}

.brand:hover .brand-mark {
  transform: perspective(220px) rotateX(0deg) rotateY(0deg) translateY(-1px);
  box-shadow: 0 6px 18px rgba(160, 190, 255, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.8);
  filter: brightness(1.06);
}

.brand-text {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.2px;
  white-space: nowrap;
  color: var(--text-on-accent);
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.3), 0 3px 10px rgba(76, 141, 255, 0.55),
    0 8px 22px rgba(0, 0, 0, 0.55);
  transform: perspective(320px) rotateY(-8deg) skewY(-1.6deg);
  transition: transform 0.28s ease;

  b {
    // 用背景裁切实现文字渐变，与 MS 方块同一套色
    background: linear-gradient(120deg, #9fd0ff 0%, #a9c4ff 50%, #f7b6e0 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
    color: #b9d4ff; // 不支持 background-clip:text 时的回退色
  }
}

/* 侧栏滚动容器与菜单 */
.el-scrollbar {
  /* 顶部品牌区 60px + 左下角账户面板（约 136px），滚动区占中间剩余空间 */
  height: calc(100% - 60px - 136px);
  background-color: transparent;
}

.el-scrollbar ::v-deep .scrollbar-wrapper {
  background-color: transparent;
}

.el-menu {
  border: none;
  height: auto;
  width: 100% !important;
  padding: 20px 15px 0;
  background-color: transparent !important;
  /* 折叠态下 80px 宽，去掉左右内边距让图标居中 */
  &.el-menu--collapse {
    padding: 20px 8px 0;
  }
}

/* ---------------------------------------------------------------------------
   左下角：主题切换 + 账户 + 营业状态设置（AccountPanel 组件）
   --------------------------------------------------------------------------- */
.side-bottom {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: transparent;
}
</style>

<style lang="scss">
/* ---------------------------------------------------------------------------
   账户面板的浅色/深色适配
   ⚠ 必须放在「非 scoped」块里：
   scoped 样式会被编译成 .xxx[data-v-父组件哈希]，而 AccountPanel 的按钮带的是
   它自己的哈希，两者不匹配 → 覆盖永远不生效。这里刻意不 scoped。
   --------------------------------------------------------------------------- */
#app.theme-light .account-panel .panel-btn {
  background: #ffffff !important;
  border-color: #d7dee8 !important;
  color: #1f2937 !important;
}

#app.theme-light .account-panel .panel-btn:hover,
#app.theme-light .account-panel .panel-btn.active {
  background: #eef2f7 !important;
  border-color: #b9c4d4 !important;
  color: #111827 !important;
}

#app.theme-light .account-panel .account-menu {
  background: #ffffff !important;
  border-color: #d7dee8 !important;
  box-shadow: 0 12px 30px rgba(21, 34, 63, 0.16) !important;
}

#app.theme-light .account-panel .account-menu p {
  color: #1f2937 !important;
}

#app.theme-light .account-panel .account-menu p:hover {
  background: #eef2f7 !important;
  color: #111827 !important;
}

#app.theme-light .account-panel .account-menu .out-login:hover {
  color: #dc2626 !important;
}

/* 深色主题：面板按钮保持半透明白底 + 浅色字（与侧栏一致） */
#app:not(:has(.login)) .account-panel .panel-btn {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--border, rgba(140, 165, 220, 0.16));
  color: var(--text-2, #c2cde4);
}

#app:not(:has(.login)) .account-panel .panel-btn:hover,
#app:not(:has(.login)) .account-panel .panel-btn.active {
  background: rgba(255, 255, 255, 0.12);
  border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
  color: var(--text-1, #eef3ff);
}

#app:not(:has(.login)) .account-panel .account-menu {
  background: var(--surface-raised, rgba(35, 43, 74, 0.96));
  border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
}

#app:not(:has(.login)) .account-panel .account-menu p {
  color: var(--text-2, #c2cde4);
}

#app:not(:has(.login)) .account-panel .account-menu p:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-1, #eef3ff);
}
</style>
