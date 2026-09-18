<template>
  <div class="login">
    <!-- 星空背景铺满全屏；登录卡片浮在其上 -->
    <div class="login-box">
      <div class="login-form">
        <el-form ref="loginForm" :model="loginForm" :rules="loginRules">
          <div class="login-form-title">
            <!-- 品牌标识：与侧栏一致（MS 渐变方块 + AITakeout-MS） -->
            <div class="brand">
              <span class="brand-mark">MS</span>
              <span class="brand-text">AITakeout<b>-MS</b></span>
            </div>
          </div>
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="账号"
              prefix-icon="iconfont icon-user"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="iconfont icon-lock"
              @keyup.enter.native="handleLogin"
            />
          </el-form-item>
          <el-form-item style="width: 100%">
            <el-button
              :loading="loading"
              class="login-btn"
              size="medium"
              type="primary"
              style="width: 100%"
              @click.native.prevent="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中...</span>
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import { Route } from 'vue-router'
import { Form as ElForm, Input } from 'element-ui'
import { UserModule } from '@/store/modules/user'
import { isValidUsername } from '@/utils/validate'

@Component({
  name: 'Login',
})
export default class extends Vue {
  private validateUsername = (rule: any, value: string, callback: Function) => {
    if (!value) {
      callback(new Error('请输入用户名'))
    } else {
      callback()
    }
  }
  private validatePassword = (rule: any, value: string, callback: Function) => {
    if (value.length < 6) {
      callback(new Error('密码必须在6位以上'))
    } else {
      callback()
    }
  }
  private loginForm = {
    username: 'admin',
    password: '123456',
  } as {
    username: String
    password: String
  }

  loginRules = {
    username: [{ validator: this.validateUsername, trigger: 'blur' }],
    password: [{ validator: this.validatePassword, trigger: 'blur' }],
  }
  private loading = false
  private redirect?: string

  @Watch('$route', { immediate: true })
  private onRouteChange(route: Route) {}

  // 登录
  private handleLogin() {
    ;(this.$refs.loginForm as ElForm).validate(async (valid: boolean) => {
      if (valid) {
        this.loading = true
        await UserModule.Login(this.loginForm as any)
          .then((res: any) => {
            if (String(res.code) === '1') {
              this.$router.push('/')
            } else {
              // this.$message.error(res.msg)
              this.loading = false
            }
          })
          .catch(() => {
            // this.$message.error('用户名或密码错误！')
            this.loading = false
          })
      } else {
        return false
      }
    })
  }
}
</script>

<style lang="scss">
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  /* 星空全景背景：铺满全屏，保持比例裁切 */
  background-color: #0b1120;
  background-image: url('~@/assets/login/login-sky.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 登录卡片：黑色玻璃「水面」效果 —— 低不透明度 + 中等模糊，星空透过卡片 */
.login-box {
  position: relative;
  width: 420px;
  padding: 38px 40px;
  border-radius: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  // 黑色打底，不透明度 0.1：只做轻微压暗，星空几乎原样透出
  background: rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(16px) saturate(170%);
  -webkit-backdrop-filter: blur(16px) saturate(170%);
  // 外投影 + 内高光：内高光制造玻璃厚度感
  box-shadow: 0 24px 60px rgba(10, 15, 40, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    inset 0 -1px 0 rgba(255, 255, 255, 0.22);
}

/* 水面光泽：斜向高光带，制造"水波反光"的层次 */
.login-box::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
    115deg,
    rgba(255, 255, 255, 0.28) 0%,
    rgba(255, 255, 255, 0.06) 28%,
    rgba(255, 255, 255, 0) 52%,
    rgba(255, 255, 255, 0.1) 78%,
    rgba(255, 255, 255, 0.22) 100%
  );
  pointer-events: none;
}

/* 顶部高光弧，强化水面的边缘反光 */
.login-box::after {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  border-radius: 50%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.95), transparent);
  pointer-events: none;
}

/* 内容需要浮在光泽层之上 */
.login-box > * {
  position: relative;
  z-index: 1;
}

.title {
  margin: 0px auto 10px auto;
  text-align: left;
  color: #707070;
}

/* 品牌标识：MS 方块在上，AITakeout-MS 在下（按草图竖排居中） */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.brand-mark {
  flex: 0 0 auto;
  // 登录页作为品牌主视觉，方块比侧栏更大（侧栏 32px）
  width: 60px;
  height: 60px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 25px;
  letter-spacing: -1px;
  color: #ffffff;
  text-shadow: 0 1px 2px rgba(30, 41, 99, 0.35);
  background: linear-gradient(140deg, #7cc4ff 0%, #8fb5ff 45%, #f2a8d8 100%);
  // 光晕随尺寸放大，否则大块配小阴影会"飘"
  box-shadow: 0 8px 24px rgba(124, 196, 255, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.brand-text {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.2px;
  white-space: nowrap;
  // 卡片透明度低 → 底色偏深，用白色主标 + 淡色渐变
  color: #ffffff;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.22), 0 6px 18px rgba(2, 8, 26, 0.5);

  b {
    // 与 MS 方块同一套淡蓝→淡粉渐变
    background: linear-gradient(120deg, #9fd0ff 0%, #a9c4ff 50%, #f7b6e0 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
    color: #b9d4ff; // 回退色
  }
}

.login-form {
  // 表单不再自带底色，由 .login-box 的浅灰玻璃层承担
  background: transparent;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  .el-form {
    width: 100%;
    height: auto;
  }
  .el-form-item {
    margin-bottom: 24px;
  }
  .el-form-item.is-error .el-input__inner {
    border: 1px solid #fd7065 !important;
    background: #ffffff !important;
  }
  .input-icon {
    height: 32px;
    width: 18px;
    margin-left: 10px;
  }
  /* 输入框：半透明玻璃（卡片已很透，输入框也要跟着透） */
  .el-input__inner {
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.16);
    backdrop-filter: blur(4px);
    -webkit-backdrop-filter: blur(4px);
    font-size: 13px;
    font-weight: 400;
    color: #ffffff;
    height: 44px;
    line-height: 44px;
    padding-left: 38px;
    transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
  }
  .el-input__inner:focus {
    background: rgba(255, 255, 255, 0.26);
    border-color: rgba(255, 255, 255, 0.8);
    box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.16);
  }
  .el-input__prefix {
    left: 12px;
    color: rgba(255, 255, 255, 0.8);
  }
  .el-input--prefix .el-input__inner {
    padding-left: 38px;
  }
  .el-input__inner::placeholder {
    color: rgba(255, 255, 255, 0.62);
  }
  .el-form-item--medium .el-form-item__content {
    line-height: 44px;
  }
  .el-input--medium .el-input__icon {
    line-height: 44px;
  }
}

/* 登录按钮：淡蓝色（与整体操作色一致） */
.login-btn {
  width: 100%;
  height: 44px;
  border-radius: 22px;
  padding: 0 20px !important;
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  border: 0;
  color: #ffffff;
  background: linear-gradient(135deg, #7cc4ff 0%, #4c8dff 100%);
  transition: background 0.25s ease, box-shadow 0.25s ease;

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #9fd4ff 0%, #3b82f6 100%);
    color: #ffffff;
    box-shadow: 0 8px 22px rgba(76, 141, 255, 0.5);
  }
}
.login-form-title {
  // 品牌区改为竖排后高度不固定，交给内容撑开
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 34px;
  .title-label {
    font-weight: 500;
    font-size: 20px;
    color: #333333;
    margin-left: 10px;
  }
}
</style>
