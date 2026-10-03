/** 导航验收：用“饱饱点餐”替换分类占位，并进入独立助手页面。 */
import { expect, test } from '@playwright/test'

test('桌面导航可进入饱饱点餐独立页面', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/')

  const entry = page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '饱饱点餐' })
  await expect(entry).toBeVisible()
  await expect(page.getByRole('button', { name: '分类功能暂未开放' })).toHaveCount(0)
  await entry.click()

  await expect(page).toHaveURL(/\/assistant(?:\?|$)/)
  await expect(page.getByRole('heading', { name: '饱饱助手' })).toBeVisible()
})

test('移动端导航保持五个单行入口', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')

  const mobileNav = page.locator('.mobile-nav')
  await expect(mobileNav.getByRole('link', { name: '饱饱点餐' })).toBeVisible()
  const columns = await mobileNav.evaluate((nav) => getComputedStyle(nav).gridTemplateColumns.split(' ').length)
  expect(columns).toBe(5)
})
