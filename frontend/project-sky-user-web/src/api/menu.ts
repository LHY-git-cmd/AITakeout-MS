import { request } from './http'

export interface Category {
  id: number
  type: 1 | 2
  name: string
  sort: number
}

export interface DishFlavor {
  id: number
  dishId: number
  name: string
  value: string
}

export interface Dish {
  id: number
  categoryId: number
  name: string
  price: number
  image: string | null
  description: string | null
  flavors: DishFlavor[]
}

export interface Setmeal {
  id: number
  categoryId: number
  name: string
  price: number
  image: string | null
  description: string | null
}

export interface SetmealDish {
  id: number
  dishId: number
  name: string
  price: number
  copies: number
}

export interface SetmealDetail extends Setmeal {
  setmealDishes: SetmealDish[]
}

export type MenuProduct = (Dish & { productType: 'dish' }) | (Setmeal & { productType: 'setmeal' })

export function getShopStatus() {
  return request<number>({ url: '/user/shop/status', method: 'GET' })
}

export async function getCategories() {
  const [dishCategories, setmealCategories] = await Promise.all([
    request<Category[]>({ url: '/user/category/list', method: 'GET', params: { type: 1 } }),
    request<Category[]>({ url: '/user/category/list', method: 'GET', params: { type: 2 } }),
  ])
  return [...(dishCategories ?? []), ...(setmealCategories ?? [])].sort((a, b) => a.sort - b.sort)
}

export async function getProducts(category: Category): Promise<MenuProduct[]> {
  if (category.type === 2) {
    const products = await request<Setmeal[]>({
      url: '/user/setmeal/list',
      method: 'GET',
      params: { categoryId: category.id },
    })
    return (products ?? []).map((product) => ({ ...product, productType: 'setmeal' as const }))
  }
  const products = await request<Dish[]>({
    url: '/user/dish/list',
    method: 'GET',
    params: { categoryId: category.id },
  })
  return (products ?? []).map((product) => ({ ...product, flavors: product.flavors ?? [], productType: 'dish' as const }))
}

export function getSetmealDetail(id: number) {
  return request<SetmealDetail>({ url: `/user/setmeal/dish/${id}`, method: 'GET' })
}
