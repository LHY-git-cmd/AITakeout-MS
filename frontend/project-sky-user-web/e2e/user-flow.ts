/** 用户端 E2E 公共流程：创建正式账号、地址并提交模拟支付订单。 */
import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'

export async function registerUser(page: Page, phonePrefix = '137') {
  const phone = `${phonePrefix}${String(Date.now()).slice(-8)}`
  await page.goto('/wallet')
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('姓名').fill('全链路验收用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.getByLabel('短信验证码').fill('246810')
  await page.getByLabel('设置密码').fill('SkyE2e!2026')
  await page.getByLabel('确认密码').fill('SkyE2e!2026')
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page.getByRole('dialog', { name: '注册正式账号' })).toBeHidden()
  await expect(page.getByText('可用模拟余额')).toBeVisible()
  return phone
}

export async function createDeliverableAddress(page: Page, phone: string) {
  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /收货地址/ }).click()
  await page.getByRole('button', { name: '新增地址' }).first().click()
  await page.getByLabel('收货人').fill('验收用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByLabel('省份').selectOption('110000')
  await page.getByLabel('城市').selectOption('110100')
  await page.getByLabel('区县').selectOption('110108')
  await page.getByLabel('详细地址').fill('上地十街 10 号')
  await page.getByRole('button', { name: '保存地址' }).click()
  await expect(page.getByText('可配送')).toBeVisible()
}

export async function submitOrderForPayment(page: Page) {
  await page.getByRole('link', { name: '点餐', exact: true }).click()
  const product = page.getByRole('article').filter({ hasText: 'E2E 测试餐' })
  await product.getByRole('button', { name: '添加E2E 测试餐' }).click()
  await page.getByRole('button', { name: '打开购物车' }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  const paymentRequest = page.waitForRequest((request) => request.method() === 'POST'
    && /\/user\/orders\/\d+\/payments(?:\?|$)/.test(request.url()))
  await page.getByRole('button', { name: '提交订单并支付' }).click()
  const request = await paymentRequest
  return request.url().match(/\/user\/orders\/(\d+)\/payments/)?.[1] ?? ''
}
