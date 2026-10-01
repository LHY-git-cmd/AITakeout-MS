/** 菜单页 AI 面板冒烟验收：页面可渲染，且游客可在面板内直接看到登录入口。 */
import { expect, test } from '@playwright/test'

test.use({ viewport: { width: 1440, height: 900 } })

test('菜单页直接承载 AI 对话面板', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))

  await page.goto('/')

  await expect(page.getByRole('complementary', { name: 'AI 聊天栏' })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录 / 注册' })).toBeVisible()
  await expect(page.getByRole('link', { name: '打开饱饱助手' })).toHaveCount(0)
  const panelLayout = await page.getByRole('complementary', { name: 'AI 聊天栏' }).evaluate((panel) => ({
    position: getComputedStyle(panel).position,
    height: panel.getBoundingClientRect().height,
    viewportHeight: window.innerHeight,
  }))
  expect(panelLayout.position).toBe('sticky')
  expect(panelLayout.height).toBeLessThan(panelLayout.viewportHeight)
  expect(pageErrors).toEqual([])
})
