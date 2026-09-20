/** 手机浏览器一条龙验收：点餐、试算、下单、支付查询、取消、全额返还与通知。 */
import { expect, test } from '@playwright/test'
import { createDeliverableAddress, registerUser, submitOrderForPayment } from './user-flow'

test('支付后取消订单会按实际支付金额全额返还', async ({ page }) => {
  const phone = await registerUser(page, '136')
  await createDeliverableAddress(page, phone)
  const orderId = await submitOrderForPayment(page)

  expect(orderId).not.toBe('')
  await expect(page.getByRole('heading', { name: '支付成功' })).toBeVisible({ timeout: 20_000 })
  await page.getByRole('link', { name: '查看订单' }).click()
  await expect(page.getByRole('heading', { name: '订单进度' })).toBeVisible()
  await page.getByRole('button', { name: '取消订单' }).click()
  await page.getByLabel('申请原因').fill('全链路退款验收')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByText('申请已处理完成')).toBeVisible()
  await expect(page.getByText('退款已原路返回模拟余额')).toBeVisible()

  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /模拟钱包/ }).click()
  await expect(page.locator('.wallet-balance strong')).toContainText('500.00')
  await expect(page.locator('.ledger-list__amount b').filter({ hasText: '+¥18.00' })).toHaveCount(1)

  await page.getByRole('link', { name: '通知' }).click()
  await expect(page.getByText('退款成功').first()).toBeVisible()
})
