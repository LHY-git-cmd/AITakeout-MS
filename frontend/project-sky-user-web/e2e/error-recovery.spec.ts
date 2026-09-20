/** 手机浏览器异常恢复验收：支付状态查询短暂失败后保留订单并允许可信重查。 */
import { expect, test } from '@playwright/test'
import { createDeliverableAddress, registerUser, submitOrderForPayment } from './user-flow'

test('支付状态首次查询失败后可恢复且不会重复下单扣款', async ({ page }) => {
  const phone = await registerUser(page, '135')
  await createDeliverableAddress(page, phone)
  let failedOnce = false
  await page.route('**/api/user/payments/*', async (route) => {
    if (!failedOnce) {
      failedOnce = true
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: '临时查询失败' }) })
      return
    }
    await route.continue()
  })

  const orderId = await submitOrderForPayment(page)
  expect(orderId).not.toBe('')
  await expect(page.getByRole('heading', { name: '暂时无法查询支付结果' })).toBeVisible()
  await expect(page.getByText('临时查询失败')).toBeVisible()
  await page.getByRole('button', { name: '重新查询' }).click()
  await expect(page.getByRole('heading', { name: '支付成功' })).toBeVisible({ timeout: 20_000 })

  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /模拟钱包/ }).click()
  await expect(page.getByText('订单支付支出')).toHaveCount(1)
  await expect(page.locator('.ledger-list__amount b').filter({ hasText: '-¥18.00' })).toHaveCount(1)
})
