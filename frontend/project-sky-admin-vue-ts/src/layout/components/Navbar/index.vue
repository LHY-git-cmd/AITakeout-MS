<template>
  <div class="navbar">
    <div class="statusBox">
      <hamburger id="hamburger-container"
                 :is-active="sidebar.opened"
                 class="hamburger-container"
                 @toggleClick="toggleSideBar"
      />
      <!-- 营业状态：小圆点（营业=绿亮 / 打烊=灰灭），悬停显示状态文字 -->
      <el-tooltip
        :content="status === 1 ? '营业中' : '打烊中'"
        placement="bottom"
        effect="dark"
        :open-delay="120"
      >
        <span
          class="shop-status"
          :class="status === 1 ? 'is-open' : 'is-closed'"
          role="status"
          :aria-label="status === 1 ? '营业中' : '打烊中'"
        />
      </el-tooltip>
    </div>

    <div class="right-menu">
      <div class="rightStatus">
        <audio ref="audioVo"
               hidden
        >
          <source src="./../../../assets/preview.mp3" type="audio/mp3">
        </audio>
        <audio ref="audioVo2"
               hidden
        >
          <source src="./../../../assets/reminder.mp3" type="audio/mp3">
        </audio>
      </div>
      <!-- 账户：点击展开 修改密码 / 退出登录（不用悬停，避免与下方内容抢层级） -->
      <div class="account-entry">
        <button
          type="button"
          class="account-btn"
          :class="{ active: menuOpen }"
          :aria-expanded="menuOpen ? 'true' : 'false'"
          @click.stop="toggleMenu"
        >
          <i class="el-icon-user-solid" />
          <span class="account-name">{{ name }}</span>
          <i class="el-icon-arrow-up" />
        </button>
        <transition name="account-menu-fade">
          <div v-if="menuOpen" class="account-menu" @click.stop>
            <p @click="handlePwd">
              <i class="el-icon-edit-outline" />修改密码
            </p>
            <p class="out-login" @click="logout">
              <i class="el-icon-switch-button" />退出登录
            </p>
          </div>
        </transition>
      </div>
    </div>
    <!-- 修改密码 -->
    <Password :dialog-form-visible="dialogFormVisible" @handleclose="handlePwdClose" />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import Cookies from 'js-cookie'
import { AppModule } from '@/store/modules/app'
import { UserModule } from '@/store/modules/user'
import Hamburger from '@/components/Hamburger/index.vue'
import { getStatus } from '@/api/users'
import { AccountModule } from '@/store/modules/account'
import { getToken } from '@/utils/cookies'
// 修改密码弹层
import Password from '../components/password.vue'

@Component({
  name: 'Navbar',
  components: {
    Hamburger,
    Password,
  },
})
export default class extends Vue {
  private websocket = null
  private websocketReconnectTimer = 0
  private websocketClosedByUser = false
  private status = 1
  private menuOpen = false
  private dialogFormVisible = false

  get sidebar() {
    return AppModule.sidebar
  }

  get device() {
    return AppModule.device.toString()
  }

  get name() {
    const fromStore = (UserModule.userInfo as any).name
    if (fromStore) return fromStore
    // 登录信息也可能只存在 cookie 里
    const raw = Cookies.get('user_info')
    if (!raw) return '管理员'
    try {
      return (JSON.parse(raw) as any).name || '管理员'
    } catch (e) {
      return '管理员'
    }
  }

  /** 账户下拉：点击切换（不用悬停，避免与下方内容抢层级） */
  private openMenu() {
    this.menuOpen = true
  }

  private closeMenu() {
    this.menuOpen = false
  }

  private toggleMenu() {
    this.menuOpen = !this.menuOpen
  }

  /** 点击页面其它地方关闭下拉 */
  private handleDocumentClick(event: MouseEvent) {
    if (!this.menuOpen) return
    const entry = this.$el && this.$el.querySelector
      ? this.$el.querySelector('.account-entry')
      : null
    if (entry && event.target instanceof Node && entry.contains(event.target)) return
    this.menuOpen = false
  }

  private handlePwd() {
    this.menuOpen = false
    this.dialogFormVisible = true
  }

  private handlePwdClose() {
    this.dialogFormVisible = false
  }

  private async logout() {
    this.menuOpen = false
    this.$store.dispatch('LogOut').then(() => {
      this.$router.replace({ path: '/login' })
    })
  }

  mounted() {
    this.getStatus()
    document.addEventListener('click', this.handleDocumentClick)
  }

  beforeDestroy() {
    document.removeEventListener('click', this.handleDocumentClick)
  }

  created() {
    this.webSocket()
  }

  onload() {
  }

  destroyed() {
    this.websocketClosedByUser = true
    window.clearTimeout(this.websocketReconnectTimer)
    if (this.websocket) this.websocket.close() //离开路由之后断开websocket连接
  }

  // 添加新订单提示弹窗
  webSocket() {
    const that = this as any
    this.websocketClosedByUser = false
    const clientId = Math.random().toString(36).substr(2)
    const token = getToken()
    const configuredSocketUrl = process.env.VUE_APP_SOCKET_URL
    const socketBaseUrl = configuredSocketUrl || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/`
    const socketUrl = `${socketBaseUrl}${clientId}?role=admin&token=${encodeURIComponent(token || '')}`
    console.log(socketUrl, 'socketUrl')
    if (typeof WebSocket == 'undefined') {
      that.$notify({
        title: '提示',
        message: '当前浏览器无法接收实时报警信息，请使用谷歌浏览器！',
        type: 'warning',
        duration: 0,
      })
    } else {
      this.websocket = new WebSocket(socketUrl)
      // 监听socket打开
      this.websocket.onopen = function () {
        window.clearTimeout(that.websocketReconnectTimer)
        console.log('浏览器WebSocket已打开')
      }
      // 监听socket消息接收
      this.websocket.onmessage = function (msg) {
        // 转换为json对象
        that.$refs.audioVo.currentTime = 0
        that.$refs.audioVo2.currentTime = 0

        console.log(msg, JSON.parse(msg.data), 'msg')
        // const h = this.$createElement
        const jsonMsg = JSON.parse(msg.data)
        // connected/pong 等控制帧不属于新单或催单通知
        if (jsonMsg.event || (jsonMsg.type !== 1 && jsonMsg.type !== 2)) return
        if (jsonMsg.type === 1) {
          that.$refs.audioVo.play()
        } else if (jsonMsg.type === 2) {
          that.$refs.audioVo2.play()
        }
        that.$notify({
          title: jsonMsg.type === 1 ? '待接单' : '催单',
          duration: 0,
          dangerouslyUseHTMLString: true,
          onClick: () => {
            that.$router
              .push(`/order?orderId=${jsonMsg.orderId}`)
              .catch((err) => {
                console.log(err)
              })
            setTimeout(() => {
              location.reload()
            }, 100)
          },
          // 这里也可以把返回信息加入到message中显示
          message: `${
            jsonMsg.type === 1
              ? `<span>您有1个<span style=color:#419EFF>订单待处理</span>,${jsonMsg.content},请及时接单</span>`
              : `${jsonMsg.content}<span style='color:#419EFF;cursor: pointer'>去处理</span>`
          }`,
        })
      }
      // 监听socket错误
      this.websocket.onerror = function () {
        that.$notify({
          title: '错误',
          message: '服务器错误，无法接收实时报警信息',
          type: 'error',
          duration: 0,
        })
      }
      // 监听socket关闭
      this.websocket.onclose = function () {
        console.log('WebSocket已关闭')
        if (!that.websocketClosedByUser) {
          window.clearTimeout(that.websocketReconnectTimer)
          that.websocketReconnectTimer = window.setTimeout(() => that.webSocket(), 3000)
        }
      }
    }
  }

  private toggleSideBar() {
    AppModule.ToggleSideBar(false)
  }

  // 营业状态徽标：优先用侧栏面板保存的共享值，失败再回退到接口
  async getStatus() {
    try {
      const { data } = await getStatus()
      this.status = Number(data.data) === 0 ? 0 : 1
      AccountModule.SetShopStatus(this.status)
    } catch (e) {
      this.status = AccountModule.shopStatus
    }
  }
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 60px;
  // 用 flex 布局：左侧状态区固定，账户靠右
  display: flex;
  align-items: center;
  position: relative;
  // 导航栏必须浮在内容之上：
  // sticky + z-index 会让导航栏自成层叠上下文，内部下拉的 z-index 无法越过它，
  // 若此处层级低于内容卡片，下拉菜单会被卡片盖住。故整体提到内容之上。
  z-index: 2000;
  // 深色半透明：用带透明度的深色底，透出下层星空
  background: rgba(14, 19, 38, 0.74);
  border-bottom: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  backdrop-filter: blur(16px) saturate(150%);

  // box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  .statusBox {
    height: 100%;
    align-items: center;
    display: flex;
    flex: 0 0 auto;
  }
  .hamburger-container {
    // line-height: 54px;

    padding: 0 12px 0 20px;
    cursor: pointer;
    transition: background 0.3s;
    -webkit-tap-highlight-color: transparent;

    &:hover {
      background: rgba(255, 255, 255, 0.08);
    }
  }

  .breadcrumb-container {
    float: left;
  }
  /* 右侧区域：靠右对齐 */
  .right-menu {
    margin-left: auto;
    margin-right: 20px;
    display: flex;
    align-items: center;
    color: var(--text-2, #c2cde4);
    font-size: 14px;

    &:focus {
      outline: none;
    }
  }
  .rightStatus {
    height: 100%;
    display: flex;
    align-items: center;
  }

  /* ---- 账户按钮 ---- */
  .account-entry {
    position: relative;
    flex: 0 0 auto;
  }

  .account-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 34px;
    padding: 0 12px;
    border: 1px solid var(--border, rgba(140, 165, 220, 0.16));
    border-radius: 6px;
    background: var(--surface-inset, rgba(255, 255, 255, 0.06));
    color: var(--text-1, #eef3ff);
    font-size: 13px;
    cursor: pointer;
    white-space: nowrap;
    transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;

    i {
      font-size: 15px;
      flex: 0 0 auto;
    }

    .account-name {
      max-width: 120px;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .el-icon-arrow-up {
      font-size: 12px;
    }

    &:hover,
    &.active {
      background: var(--surface-hover, rgba(255, 255, 255, 0.12));
      border-color: var(--border-strong, rgba(140, 165, 220, 0.3));
    }
  }

  /* ---- 账户下拉 ---- */
  .account-menu {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    min-width: 132px;
    padding: 5px;
    background: var(--surface-raised, rgba(35, 43, 74, 0.96));
    border: 1px solid var(--border-strong, rgba(140, 165, 220, 0.3));
    border-radius: 8px;
    box-shadow: 0 12px 30px rgba(2, 6, 20, 0.45);
    // 必须浮在内容之上：导航栏同级内容卡片会形成层叠，用足够高的 z-index 压过
    z-index: 3000;
    // 导航栏继承的 60px 行高会撑高菜单，这里复位
    line-height: normal;

    p {
      margin: 0;
      display: flex;
      align-items: center;
      gap: 8px;
      height: 32px;
      padding: 0 9px;
      border-radius: 5px;
      font-size: 13px;
      color: var(--text-2, #c2cde4);
      cursor: pointer;
      white-space: nowrap;

      i {
        font-size: 14px;
      }

      &:hover {
        background: var(--surface-hover, rgba(255, 255, 255, 0.1));
        color: var(--text-1, #eef3ff);
      }
    }

    .out-login:hover {
      color: #ff8f8f;
    }
  }

  /* 展开动画 */
  .account-menu-fade-enter-active,
  .account-menu-fade-leave-active {
    transition: opacity 0.16s ease, transform 0.16s ease;
  }

  .account-menu-fade-enter,
  .account-menu-fade-leave-to {
    opacity: 0;
    transform: translateY(-4px);
  }

  /* ---- 营业状态小圆点 ---- */
  .shop-status {
    display: inline-block;
    width: 10px;
    height: 10px;
    margin-left: 6px;
    border-radius: 50%;
    cursor: default;
    transition: background 0.25s ease, box-shadow 0.25s ease;
  }

  /* 营业中：绿色点亮 + 呼吸光晕 */
  .shop-status.is-open {
    background: #34d399;
    animation: shop-pulse 2.2s ease-in-out infinite;
  }

  /* 打烊中：灰色熄灭，无光晕 */
  .shop-status.is-closed {
    background: #6b7280;
    box-shadow: none;
    animation: none;
  }
  .mesCenter {
    i {
      background: url('./../../../assets/icons/msg.png') no-repeat;
      background-size: contain;
    }
  }
  // .el-badge__content.is-fixed {
  //   top: 20px;
  //   right: 6px;
  // }
}

/* 营业中的呼吸光晕（放在 scoped 块内，keyframes 名会被一并作用域化） */
@keyframes shop-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.22), 0 0 10px rgba(52, 211, 153, 0.85);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(52, 211, 153, 0.1), 0 0 16px rgba(52, 211, 153, 1);
  }
}
</style>
<style lang="scss">
.el-notification {
  // background: rgba(255, 255, 255, 0.71);
  width: 419px !important;
  .el-notification__title {
    margin-bottom: 14px;
    color: var(--text-1, #eef3ff);
    .el-notification__content {
      color: var(--text-2, #c2cde4);
    }
  }
}
.navbar {
  .el-dialog {
    min-width: auto !important;
  }
  .el-dialog__header {
    height: 61px;
    line-height: 60px;
    background: rgba(255, 255, 255, 0.03);
    padding: 0 30px;
    font-size: 16px;
    color: var(--text-1, #eef3ff);
    border: 0 none;
    border-bottom: 1px solid var(--border, rgba(140, 165, 220, 0.16));
  }
  .el-dialog__body {
    padding: 10px 30px 30px;
    .el-radio,
    .el-radio__input {
      white-space: normal;
    }
    .el-radio__label {
      padding-left: 5px;
      color: var(--text-1, #eef3ff);
      font-weight: 700;
      span {
        display: block;
        line-height: 20px;
        padding-top: 12px;
        color: var(--text-3, #8e9cb8);
        font-weight: normal;
      }
    }
    .el-radio__input.is-checked .el-radio__inner {
      &::after {
        background: var(--brand-ink, #1a1600);
      }
    }
    .el-radio-group {
      & > .is-checked {
        border: 1px solid var(--action);
      }
    }
    .el-radio {
      width: 100%;
      background: var(--surface-inset, rgba(12, 17, 34, 0.55));
      border: 1px solid var(--border, rgba(140, 165, 220, 0.16));
      border-radius: 4px;
      padding: 14px 22px;
      margin-top: 20px;
    }
    .el-radio__input.is-checked + .el-radio__label {
      span {
      }
    }
  }
  .el-badge__content.is-fixed {
    top: 24px;
    right: 2px;
    width: 18px;
    height: 18px;
    font-size: 10px;
    line-height: 16px;
    font-size: 10px;
    border-radius: 50%;
    padding: 0;
  }
  .badgeW {
    .el-badge__content.is-fixed {
      width: 30px;
      border-radius: 20px;
    }
  }
}
.el-icon-arrow-down {
  // 深色导航栏上原来引用的深色箭头图标不可见，这里用边框画一个浅色箭头
  background: none;
  border-right: 1.5px solid var(--text-2, #c2cde4);
  border-bottom: 1.5px solid var(--text-2, #c2cde4);
  width: 6px;
  height: 6px;
  transform: rotate(45deg);
  transform-origin: 60% 60%;
  margin-left: 16px;
  position: absolute;
  right: 16px;
  top: 12px;
  transition: transform 0.2s ease;
  &:before {
    content: '';
  }
}

.msgTip {
  color: #419eff;
  padding: 0 5px;
}
// .el-dropdown{
//   .el-button--primary{
//     height: 32px;
//     background: rgba(255,255,255,0.52);
//     border-radius: 4px;
//     padding-top: 0px;
//     padding-bottom: 0px;
//   }
//   margin-top: 2px;
// }
// .el-popper{
//   top: 45px !important;
//   padding-top: 50px !important;
//   border-radius: 0 0 4px 4px;
// }
// .el-popper[x-placement^=bottom] .popper__arrow::after,.popper__arrow{
//   display: none !important;
// }
</style>
