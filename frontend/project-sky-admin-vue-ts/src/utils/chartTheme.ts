/**
 * 图表配色：从 CSS 变量读取，保证与主题（深色星空 / 白天模式）一致。
 *
 * 背景：ECharts 的配置是 JS 对象，拿不到 CSS 变量，之前把文字色写死成白色，
 * 在浅色主题下会变成「白底白字」。这里统一在渲染时读取当前主题的实际值。
 * 主题切换后通过 window 上的自定义事件通知图表重绘（见 ThemeChartMixin）。
 */

const FALLBACK = {
  text1: '#eef3ff',
  text2: '#c2cde4',
  text3: '#8e9cb8',
  axis: '#3b5a8f',
  split: 'rgba(140, 165, 220, 0.35)',
  tooltipBg: 'rgba(35, 43, 74, 0.96)',
}

/** 图表监听此事件，在应用主题切换后重新读取配色并绘制。 */
export const CHART_THEME_CHANGE_EVENT = 'sky:chart-theme-change'

/** 通知当前页面中的 ECharts 实例重新应用主题色。 */
export function notifyChartThemeChange() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(CHART_THEME_CHANGE_EVENT))
  }
}

function readVar(name: string, fallback: string): string {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return fallback
  }
  // 当前主题变量挂在 #app 上，必须先于 :root 读取，避免白天模式误用深色默认值。
  const app = document.getElementById('app')
  if (app) {
    const appValue = getComputedStyle(app).getPropertyValue(name).trim()
    if (appValue) return appValue
  }
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim()
  return value || fallback
}

/** 当前主题下的图表色板 */
export function chartPalette() {
  return {
    // 文字
    title: readVar('--text-1', FALLBACK.text1),
    label: readVar('--chart-text', FALLBACK.text2),
    axisLabel: readVar('--chart-text', FALLBACK.text2),
    legend: readVar('--chart-text', FALLBACK.text3),
    // 线与网格
    axis: readVar('--field-border', FALLBACK.axis),
    split: readVar('--border-strong', FALLBACK.split),
    // 浮层
    tooltipBg: readVar('--surface-raised', FALLBACK.tooltipBg),
    tooltipText: readVar('--text-1', FALLBACK.text1),
    tooltipBorder: readVar('--border-strong', FALLBACK.split),
  }
}

/**
 * y 轴网格线：与背景拉开对比
 * （浅色主题下 --border-strong 是灰，深色下是淡蓝，都清晰可见）
 */
export function splitLineStyle() {
  const p = chartPalette()
  return {
    show: true,
    lineStyle: {
      color: p.split,
      type: 'dashed',
      width: 1,
    },
  }
}
