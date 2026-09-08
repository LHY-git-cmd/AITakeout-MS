import { request } from './http'

export interface CartCommand {
  dishId?: number
  setmealId?: number
  dishFlavor?: string
}

export interface ServerCartItem {
  id: number
  name: string
  dishId: number | null
  setmealId: number | null
  dishFlavor: string | null
  number: number
  amount: number
  image: string | null
}

export function getCart() {
  return request<ServerCartItem[]>({ url: '/user/shoppingCart/list', method: 'GET' })
}

export function addCartItem(command: CartCommand) {
  return request<void>({ url: '/user/shoppingCart/add', method: 'POST', data: command })
}

export function subtractCartItem(command: CartCommand) {
  return request<void>({ url: '/user/shoppingCart/sub', method: 'POST', data: command })
}

export function cleanCart() {
  return request<void>({ url: '/user/shoppingCart/clean', method: 'DELETE' })
}
