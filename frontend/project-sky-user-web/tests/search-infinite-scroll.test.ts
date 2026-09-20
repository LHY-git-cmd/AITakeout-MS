/** 验证跨分类搜索分批加载、并发去重和失败后的结果保留。 */
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  searchProducts: vi.fn(), getSearchHistory: vi.fn(),
  recordSearchHistory: vi.fn(), clearSearchHistory: vi.fn(),
}))
vi.mock('@/api/search', () => api)

import { useSearchStore } from '@/stores/search'

const item = (stableKey: string) => {
  const [productType, rawId] = stableKey.split(':')
  return {
    id: Number(rawId), productType, stableKey, name: stableKey, price: 12, image: null,
    description: null, categoryId: 1, categoryName: '测试分类', relevanceScore: 1, hasFlavor: false,
  }
}

describe('商品搜索连续加载', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('下一游标只请求一次且失败时保留已有结果', async () => {
    api.searchProducts.mockResolvedValueOnce({ items: [item('dish:1')], nextCursor: 'c2', hasMore: true })
      .mockRejectedValueOnce(new Error('timeout'))
    const store = useSearchStore()

    await store.search('牛肉')
    await Promise.all([store.loadMore(), store.loadMore()])

    expect(api.searchProducts).toHaveBeenCalledTimes(2)
    expect(store.items.map((value) => value.stableKey)).toEqual(['dish:1'])
    expect(store.retryable).toBe(true)
  })

  it('合并新批次时按商品类型和编号去重并恢复滚动状态', async () => {
    api.searchProducts.mockResolvedValueOnce({ items: [item('dish:1')], nextCursor: 'c2', hasMore: true })
      .mockResolvedValueOnce({ items: [item('dish:1'), item('setmeal:2')], nextCursor: null, hasMore: false })
    const store = useSearchStore()

    await store.search('套餐')
    await store.loadMore()
    store.save('menu', 640)
    store.reset()

    expect(store.restore('menu')).toBe(640)
    expect(store.items.map((value) => value.stableKey)).toEqual(['dish:1', 'setmeal:2'])
  })
})
