import request from '@/utils/request'

export const listUserAccounts = () => request({ url: '/admin/mock-account', method: 'get' })
export const getUserAccount = (userId: number) => request({ url: `/admin/mock-account/${userId}`, method: 'get' })
export const adjustAccount = (data: any) => request({ url: '/admin/mock-account/adjustments', method: 'post', data })
