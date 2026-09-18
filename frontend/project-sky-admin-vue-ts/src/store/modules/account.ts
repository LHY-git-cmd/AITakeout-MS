import { VuexModule, Module, Mutation, Action, getModule } from 'vuex-module-decorators'
import store from '@/store'

/**
 * 营业状态共享模块
 * ----------------------------------------------------------------------------
 * 「营业状态设置」入口在侧栏左下角（AccountPanel），但弹层不能放在侧栏内：
 * 侧栏有 overflow:hidden，position:fixed 的弹层会被裁切成一条窄缝。
 * 因此弹层独立成组件挂在布局根节点，用这里的开关通信。
 */
@Module({ 'dynamic': true, store, 'name': 'account' })
class Account extends VuexModule {
  /** 导航栏徽标展示用（1 营业中 / 0 打烊中） */
  public shopStatus = 1
  /** 营业状态弹层开关 */
  public statusDialogVisible = false

  @Mutation
  private SET_SHOP_STATUS(status: number) {
    this.shopStatus = Number(status) === 0 ? 0 : 1
  }

  @Mutation
  private SET_STATUS_DIALOG(visible: boolean) {
    this.statusDialogVisible = visible
  }

  @Action
  public SetShopStatus(status: number) {
    this.SET_SHOP_STATUS(status)
  }

  @Action
  public OpenStatusDialog() {
    this.SET_STATUS_DIALOG(true)
  }

  @Action
  public CloseStatusDialog() {
    this.SET_STATUS_DIALOG(false)
  }
}

export const AccountModule = getModule(Account)
