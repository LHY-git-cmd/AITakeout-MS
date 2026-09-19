import { request, requestSessionRefresh } from './http'

export interface UserProfile {
  id: number
  name: string | null
  phone: string | null
  avatar: string | null
}

export interface AuthSession {
  user: UserProfile
  accessToken: string
}

export interface RegisterPayload {
  name: string
  phone: string
  code: string
  password: string
}

const DEVICE_KEY = 'sky-device-id'

function deviceId() {
  const existing = localStorage.getItem(DEVICE_KEY)
  if (existing) return existing
  const generated = globalThis.crypto?.randomUUID?.()
    ?? `web-${Date.now()}-${Math.random().toString(36).slice(2)}`
  localStorage.setItem(DEVICE_KEY, generated)
  return generated
}

export function sendRegistrationCode(phone: string) {
  return request<void>({
    url: '/user/auth/sms/send',
    method: 'POST',
    data: { phone, purpose: 'register' },
  })
}

export function loginWithPassword(phone: string, password: string) {
  return request<AuthSession>({
    url: '/user/auth/login',
    method: 'POST',
    headers: { 'X-Device-Id': deviceId() },
    data: { phone, password },
  })
}

export function registerUser(data: RegisterPayload) {
  return request<AuthSession>({
    url: '/user/auth/register',
    method: 'POST',
    headers: { 'X-Device-Id': deviceId() },
    data,
  })
}

export function refreshSession() {
  return requestSessionRefresh<AuthSession>()
}

export function logoutCurrentDevice() {
  return request<void>({ url: '/user/auth/logout', method: 'POST' })
}

export function logoutAllDevices() {
  return request<void>({ url: '/user/auth/logout-all', method: 'POST' })
}
