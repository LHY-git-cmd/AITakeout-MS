import { request } from './http'

export interface Address {
  id: number
  consignee: string
  phone: string
  sex: '0' | '1'
  provinceCode: string | null
  provinceName: string | null
  cityCode: string | null
  cityName: string | null
  districtCode: string | null
  districtName: string | null
  detail: string
  label: string | null
  isDefault: 0 | 1
}

export type AddressPayload = Omit<Address, 'id' | 'isDefault'> & { id?: number }

export function getAddresses() {
  return request<Address[]>({ url: '/user/addressBook/list', method: 'GET' })
}

export function createAddress(payload: AddressPayload) {
  return request<void>({ url: '/user/addressBook', method: 'POST', data: payload })
}

export function updateAddress(payload: AddressPayload) {
  return request<void>({ url: '/user/addressBook', method: 'PUT', data: payload })
}

export function deleteAddress(id: number) {
  return request<void>({ url: '/user/addressBook', method: 'DELETE', params: { id } })
}

export function setDefaultAddress(id: number) {
  return request<void>({ url: '/user/addressBook/default', method: 'PUT', data: { id } })
}

export function fullAddress(address: Address) {
  return [address.provinceName, address.cityName, address.districtName, address.detail].filter(Boolean).join('')
}
