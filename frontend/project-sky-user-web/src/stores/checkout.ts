import { defineStore } from 'pinia'
import { previewCheckout, submitCheckout, type CheckoutQuote, type CheckoutRequest, type SubmitCheckoutPayload } from '@/api/checkout'
import { ApiError } from '@/api/http'

let activePreview: AbortController | null = null

export const useCheckoutStore = defineStore('checkout', {
  state: () => ({
    quote: null as CheckoutQuote | null,
    loading: false,
    submitting: false,
    error: '',
    submissionKey: '',
  }),
  actions: {
    async preview(payload: CheckoutRequest) {
      activePreview?.abort()
      const controller = new AbortController()
      activePreview = controller
      this.quote = null
      this.loading = true
      this.error = ''
      this.submissionKey = ''
      try {
        this.quote = await previewCheckout(payload, controller.signal)
      } catch (cause) {
        if (controller.signal.aborted) return
        this.error = cause instanceof ApiError ? cause.message : '结算试算失败，请重试'
      } finally {
        if (activePreview === controller) {
          this.loading = false
          activePreview = null
        }
      }
    },
    async submit(payload: Omit<SubmitCheckoutPayload, 'previewToken'>) {
      if (!this.quote) throw new Error('请先完成订单试算')
      this.submitting = true
      this.error = ''
      this.submissionKey ||= crypto.randomUUID()
      try {
        return await submitCheckout({ ...payload, previewToken: this.quote.previewToken }, this.submissionKey)
      } catch (cause) {
        this.error = cause instanceof ApiError ? cause.message : '提交订单失败，请稍后重试'
        throw cause
      } finally {
        this.submitting = false
      }
    },
    reset() {
      activePreview?.abort()
      activePreview = null
      this.$reset()
    },
  },
})
