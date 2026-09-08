import { defineStore } from 'pinia'
import { addCartItem, cleanCart, getCart, subtractCartItem, type CartCommand, type ServerCartItem } from '@/api/cart'
import type { MenuProduct } from '@/api/menu'

const LOCAL_CART_KEY = 'sky-guest-cart'

export interface CartItem {
  key: string
  name: string
  dishId?: number
  setmealId?: number
  dishFlavor?: string
  number: number
  amount: number
  image: string | null
}

function keyOf(command: CartCommand) {
  return command.dishId ? `dish:${command.dishId}:${command.dishFlavor ?? ''}` : `setmeal:${command.setmealId}`
}

function readLocalCart(): CartItem[] {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]') as CartItem[]
  } catch {
    localStorage.removeItem(LOCAL_CART_KEY)
    return []
  }
}

function serverItemToCart(item: ServerCartItem): CartItem {
  const command: CartCommand = {
    dishId: item.dishId ?? undefined,
    setmealId: item.setmealId ?? undefined,
    dishFlavor: item.dishFlavor ?? undefined,
  }
  return { ...command, key: keyOf(command), name: item.name, number: item.number, amount: Number(item.amount), image: item.image }
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartItem[],
    authenticated: false,
    loading: false,
    syncing: false,
    error: '',
  }),
  getters: {
    totalCount: (state) => state.items.reduce((total, item) => total + item.number, 0),
    totalAmount: (state) => state.items.reduce((total, item) => total + item.amount * item.number, 0),
  },
  actions: {
    persistGuestCart() {
      localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(this.items))
    },
    commandFor(item: CartItem): CartCommand {
      return { dishId: item.dishId, setmealId: item.setmealId, dishFlavor: item.dishFlavor }
    },
    async initialize(authenticated: boolean) {
      this.authenticated = authenticated
      if (authenticated) await this.mergeAndLoad()
      else this.items = readLocalCart()
    },
    async mergeAndLoad() {
      this.authenticated = true
      this.syncing = true
      this.error = ''
      const guestItems = readLocalCart()
      try {
        for (const guestItem of guestItems) {
          while (guestItem.number > 0) {
            await addCartItem(this.commandFor(guestItem))
            guestItem.number -= 1
            localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(guestItems.filter((item) => item.number > 0)))
          }
        }
        localStorage.removeItem(LOCAL_CART_KEY)
        await this.loadRemote()
      } catch {
        this.error = '购物车同步失败，请稍后重试'
        await this.loadRemote().catch(() => undefined)
      } finally {
        this.syncing = false
      }
    },
    async loadRemote() {
      this.loading = true
      try {
        const items = await getCart()
        this.items = (items ?? []).map(serverItemToCart)
      } finally {
        this.loading = false
      }
    },
    async add(product: MenuProduct, dishFlavor?: string) {
      const command: CartCommand = product.productType === 'dish'
        ? { dishId: product.id, dishFlavor }
        : { setmealId: product.id }
      const key = keyOf(command)
      const existing = this.items.find((item) => item.key === key)
      if (this.authenticated) await addCartItem(command)
      if (existing) existing.number += 1
      else this.items.push({
        ...command,
        key,
        name: product.name,
        number: 1,
        amount: Number(product.price),
        image: product.image,
      })
      if (!this.authenticated) this.persistGuestCart()
    },
    async increment(item: CartItem) {
      if (this.authenticated) await addCartItem(this.commandFor(item))
      item.number += 1
      if (!this.authenticated) this.persistGuestCart()
    },
    async decrement(item: CartItem) {
      if (this.authenticated) await subtractCartItem(this.commandFor(item))
      if (item.number > 1) item.number -= 1
      else this.items = this.items.filter((candidate) => candidate.key !== item.key)
      if (!this.authenticated) this.persistGuestCart()
    },
    async clear() {
      if (this.authenticated) await cleanCart()
      this.items = []
      if (!this.authenticated) localStorage.removeItem(LOCAL_CART_KEY)
    },
    markSubmitted() {
      this.items = []
      localStorage.removeItem(LOCAL_CART_KEY)
    },
    switchToGuest() {
      this.authenticated = false
      this.items = readLocalCart()
      this.error = ''
    },
  },
})
