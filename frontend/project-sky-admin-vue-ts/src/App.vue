<template>
  <div id="app" :class="{ 'theme-light': isLightTheme }">
    <router-view />
  </div>
</template>

<script lang="ts">
import { Component, Vue, Watch } from 'vue-property-decorator'
import { ThemeModule } from '@/store/modules/theme'

@Component({
  name: 'App',
})
export default class extends Vue {
  get isLightTheme() {
    return ThemeModule.isLight
  }

  created() {
    this.applyTheme()
  }

  @Watch('isLightTheme')
  onThemeChange() {
    this.applyTheme()
  }

  /** 同步主题到 <html> 与 <body>，让页面底色与滚动区域一起切换 */
  private applyTheme() {
    ThemeModule.SyncHtmlClass()
    if (typeof document !== 'undefined') {
      document.body.classList.toggle('theme-light', this.isLightTheme)
    }
  }
}
</script>
