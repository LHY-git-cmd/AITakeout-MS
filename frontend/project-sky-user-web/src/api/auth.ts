import { request } from './http'

export interface UserProfile {
  id: number
  name: string | null
  phone: string | null
  avatar: string | null
}

interface LoginResponse extends UserProfile {
  token: string
}

export interface RegisterPayload {
  name: string
  phone: string
  sex: '' | '0' | '1'
  idNumber: string
  avatar: string
}

export function loginWithPassword(phone: string, password: string) {
  return request<LoginResponse>({
    url: '/user/user/login/web',
    method: 'POST',
    data: { phone, password },
  })
}

export function registerUser(data: RegisterPayload) {
  return request<LoginResponse>({
    url: '/user/user/register/web',
    method: 'POST',
    data,
  })
}

export function getUserProfile() {
  return request<UserProfile>({
    url: '/user/user/profile',
    method: 'GET',
  })
}
