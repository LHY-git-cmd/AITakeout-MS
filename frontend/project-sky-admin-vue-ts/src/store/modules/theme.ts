import { VuexModule, Module, Mutation, Action, getModule } from 'vuex-module-decorators'
import store from '@/store'

const STORAGE_KEY = 'sky_theme'

export enum ThemeType {
  Dark = 'dark',
  Light = 'light'
}

function readStoredTheme(): ThemeType {
  try {
    return localStorage.getItem(STORAGE_KEY) === ThemeType.Light
      ? ThemeType.Light
      : ThemeType.Dark
  } catch (e) {
    return ThemeType.Dark
  }
}

function persistTheme(theme: ThemeType) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch (e) {
    /* 隐私模式下 localStorage 可能不可写，忽略即可，不影响本次会话 */
  }
}

/**
 * 主题（深色星空 / 白天模式）
 * 只负责状态与持久化；具体配色由 styles/theme-dark.scss 与 theme-light.scss 决定。
 * 不涉及任何接口、路由与权限逻辑。
 */
@Module({ 'dynamic': true, store, 'name': 'theme' })
class Theme extends VuexModule {
  public theme: ThemeType = readStoredTheme()

  get isLight() {
    return this.theme === ThemeType.Light
  }

  @Mutation
  private SET_THEME(theme: ThemeType) {
    this.theme = theme
    persistTheme(theme)
    applyHtmlClass(theme)
  }

  @Action
  public SetTheme(theme: ThemeType) {
    this.SET_THEME(theme)
  }

  @Action
  public ToggleTheme() {
    this.SET_THEME(this.isLight ? ThemeType.Dark : ThemeType.Light)
  }

  /** 把主题同步到 <html>，让 body 与滚动条区域一起换底色 */
  @Action
  public SyncHtmlClass() {
    applyHtmlClass(this.theme)
  }
}

function applyHtmlClass(theme: ThemeType) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (theme === ThemeType.Light) {
    root.classList.add('theme-light')
  } else {
    root.classList.remove('theme-light')
  }
}

export const ThemeModule = getModule(Theme)
