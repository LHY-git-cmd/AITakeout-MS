/**
 * 回归验证图表优先使用当前应用主题变量，避免白天模式误用深色主题的浅灰文字。
 */
import {
  CHART_THEME_CHANGE_EVENT,
  chartPalette,
  notifyChartThemeChange
} from '@/utils/chartTheme'

describe('chart theme palette', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('style')
    document.body.innerHTML = ''
  })

  it('prefers theme variables defined on #app', () => {
    document.documentElement.style.setProperty('--chart-text', '#c2cde4')
    document.body.innerHTML = '<div id="app" style="--chart-text: #111111"></div>'

    expect(chartPalette().axisLabel).toBe('#111111')
  })

  it('notifies charts after the application theme changes', () => {
    const listener = jest.fn()
    window.addEventListener(CHART_THEME_CHANGE_EVENT, listener)

    notifyChartThemeChange()

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener(CHART_THEME_CHANGE_EVENT, listener)
  })
})
