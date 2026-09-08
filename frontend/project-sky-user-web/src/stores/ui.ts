import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', {
  state: () => ({
    cartOpen: false,
    loginOpen: false,
  }),
  actions: {
    openCart() {
      this.cartOpen = true
    },
    closeCart() {
      this.cartOpen = false
    },
    openLogin() {
      this.loginOpen = true
    },
    closeLogin() {
      this.loginOpen = false
    },
  },
})
