import { expect, test } from '@playwright/test'

test('手机用户注册、领取模拟金、下单支付且重复请求只扣款一次', async ({ page }) => {
  const phone = `138${String(Date.now()).slice(-8)}`
  const password = 'SkyE2e!2026'

  await page.goto('/wallet')
  await page.getByRole('tab', { name: '注册' }).click()
  await page.getByLabel('姓名').fill('移动端验收用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.getByLabel('短信验证码').fill('246810')
  await page.getByLabel('设置密码').fill(password)
  await page.getByLabel('确认密码').fill(password)
  await page.getByRole('button', { name: '注册并登录' }).click()

  await expect(page.getByText('可用模拟余额')).toBeVisible()
  await expect(page.locator('.wallet-balance strong')).toContainText('500.00')
  await page.getByRole('button', { name: '领取 ¥500.00 模拟金' }).click()
  await expect(page.locator('.wallet-balance strong')).toContainText('1000.00')

  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /收货地址/ }).click()
  await page.getByRole('button', { name: '新增地址' }).first().click()
  await page.getByLabel('收货人').fill('验收用户')
  await page.getByLabel('手机号').fill(phone)
  await page.getByLabel('省份').fill('北京市')
  await page.getByLabel('城市').fill('北京市')
  await page.getByLabel('区县').fill('海淀区')
  await page.getByLabel('详细地址').fill('上地十街 10 号')
  await page.getByRole('button', { name: '保存地址' }).click()
  await expect(page.getByText('北京市北京市海淀区上地十街 10 号')).toBeVisible()
  await expect(page.getByText('默认')).toBeVisible()

  await page.getByRole('link', { name: '点餐', exact: true }).click()
  const product = page.getByRole('article').filter({ hasText: 'E2E 测试餐' })
  await expect(product).toBeVisible()
  await product.getByRole('button', { name: '添加E2E 测试餐' }).click()
  await page.getByRole('button', { name: '打开购物车' }).click()
  await page.getByRole('button', { name: '去结算' }).click()
  await expect(page.getByRole('main').getByRole('heading', { name: '确认订单' })).toBeVisible()
  const paymentRequestPromise = page.waitForRequest((request) => request.method() === 'POST'
    && /\/user\/orders\/\d+\/payments(?:\?|$)/.test(request.url()))
  await page.getByRole('button', { name: '提交订单并支付' }).click()

  const paymentRequest = await paymentRequestPromise
  const requestHeaders = await paymentRequest.allHeaders()
  const paidOrderId = paymentRequest.url().match(/\/user\/orders\/(\d+)\/payments/)?.[1] ?? ''
  const authentication = requestHeaders.authentication ?? ''
  const idempotencyKey = requestHeaders['idempotency-key'] ?? ''
  await expect(page.getByRole('heading', { name: '支付成功' })).toBeVisible({ timeout: 20_000 })
  expect(authentication).not.toBe('')
  expect(idempotencyKey).not.toBe('')
  expect(paidOrderId).not.toBe('')

  const replay = await page.request.post(`/api/user/orders/${paidOrderId}/payments`, {
    headers: {
      authentication,
      'Idempotency-Key': idempotencyKey,
    },
  })
  expect(replay.ok()).toBe(true)

  await page.getByRole('link', { name: '我的' }).click()
  await page.getByRole('link', { name: /模拟钱包/ }).click()
  await expect(page.locator('.wallet-balance strong')).toContainText('982.00')
  await expect(page.getByText('订单支付支出')).toHaveCount(1)
  await expect(page.locator('.ledger-list__amount b').filter({ hasText: '-¥18.00' })).toHaveCount(1)
})
