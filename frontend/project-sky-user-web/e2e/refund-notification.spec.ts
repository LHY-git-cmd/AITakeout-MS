/** 手机浏览器完整验证：支付后取消、全额退款、时间轴和通知中心。 */
import { expect, test } from '@playwright/test'

test('支付订单取消后全额退回模拟余额并产生通知', async ({ page }) => {
  const phone = `139${String(Date.now()).slice(-8)}`

  await page.goto('/wallet')
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('姓名').fill('退款验收用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.getByLabel('短信验证码').fill('246810')
  await page.getByLabel('设置密码').fill('SkyE2e!2026')
  await page.getByLabel('确认密码').fill('SkyE2e!2026')
  await page.getByRole('button', { name: '注册并登录' }).click()

  await expect(page.getByRole('dialog', { name: '注册正式账号' })).toBeHidden()

  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /收货地址/ }).click()
  await page.getByRole('button', { name: '新增地址' }).first().click()
  await page.getByLabel('收货人').fill('退款用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByLabel('省份').selectOption('110000')
  await page.getByLabel('城市').selectOption('110100')
  await page.getByLabel('区县').selectOption('110108')
  await page.getByLabel('详细地址').fill('上地十街 10 号')
  await page.getByRole('button', { name: '保存地址' }).click()

  await page.getByRole('link', { name: '点餐', exact: true }).click()
  const product = page.getByRole('article').filter({ hasText: 'E2E 测试餐' })
  await product.getByRole('button', { name: '添加E2E 测试餐' }).click()
  await page.getByRole('button', { name: '打开购物车' }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  await page.getByRole('button', { name: '提交订单并支付' }).click()
  await expect(page.getByRole('heading', { name: '支付成功' })).toBeVisible({ timeout: 20_000 })

  await page.getByRole('link', { name: '订单', exact: true }).click()
  await page.getByRole('button', { name: '取消订单' }).first().click()
  await expect(page.getByRole('dialog', { name: '申请取消订单' })).toBeVisible()
  await page.getByLabel('申请原因').fill('行程临时变化')
  await page.getByRole('button', { name: '确认提交' }).click()
  await expect(page.getByText('申请已处理完成')).toBeVisible()

  await page.locator('.order-card__content').first().click()
  await expect(page.getByText('退款成功')).toBeVisible()
  await expect(page.getByText('退款已原路返回模拟余额')).toBeVisible()
  await expect(page.getByRole('heading', { name: '订单进度' })).toBeVisible()

  await page.getByRole('link', { name: '通知' }).click()
  await expect(page.getByText('退款成功').first()).toBeVisible()
})
