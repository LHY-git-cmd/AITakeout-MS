import type { AxiosRequestConfig } from 'axios'
import { request } from './http'

export interface ProductSearchItem {
  id: number
  productType: 'dish' | 'setmeal'
  stableKey: string
  name: string
  price: number
  image: string | null
  description: string | null
  categoryId: number
  categoryName: string
  relevanceScore: number
  hasFlavor: boolean
}

export interface ProductSearchPage {
  items: ProductSearchItem[]
  nextCursor: string | null
  hasMore: boolean
}

export function searchProducts(keyword: string, cursor?: string | null, limit = 20, signal?: AbortSignal) {
  const config: AxiosRequestConfig = {
    url: '/user/products/search', method: 'GET', params: { keyword, cursor: cursor || undefined, limit }, signal,
  }
  return request<ProductSearchPage>(config)
}

export function getSearchHistory() {
  return request<string[]>({ url: '/user/search-history', method: 'GET' })
}

export function recordSearchHistory(keyword: string) {
  return request<void>({ url: '/user/search-history', method: 'POST', data: { keyword } })
}

export function clearSearchHistory() {
  return request<void>({ url: '/user/search-history', method: 'DELETE' })
}
