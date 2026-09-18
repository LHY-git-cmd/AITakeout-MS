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

function readVar(name: string, fallback: string): string {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return fallback
  }
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim()
  if (value) return value
  // 变量挂在 #app 上（如 .theme-light），再兜一层
  const app = document.getElementById('app')
  if (!app) return fallback
  const appValue = getComputedStyle(app).getPropertyValue(name).trim()
  return appValue || fallback
}

/** 当前主题下的图表色板 */
export function chartPalette() {
  return {
    // 文字
    title: readVar('--text-1', FALLBACK.text1),
    label: readVar('--text-2', FALLBACK.text2),
    axisLabel: readVar('--text-2', FALLBACK.text2),
    legend: readVar('--text-3', FALLBACK.text3),
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
