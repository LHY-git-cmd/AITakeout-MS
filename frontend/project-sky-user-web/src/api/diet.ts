import { request } from './http'

export interface DietConstraint {
  type: 'ALLERGEN' | 'EXCLUDED_INGREDIENT' | 'DOCTOR_RESTRICTION' | 'DISLIKE'
  code: string
  severity?: string
  sourceType?: string
}

export interface DietGoal {
  code: string
  priority?: number
}

export interface DietProfile {
  regionCode?: string
  dietaryPattern?: string
  consentVersion: string
  consentedAt?: string
  constraints: DietConstraint[]
  goals: DietGoal[]
}

export function getDietProfile() {
  return request<DietProfile | null>({ url: '/user/diet/profile', method: 'GET' })
}

export function saveDietProfile(profile: DietProfile) {
  return request<DietProfile>({ url: '/user/diet/profile', method: 'PUT', data: profile })
}

export function deleteDietProfile() {
  return request<void>({ url: '/user/diet/profile', method: 'DELETE' })
}
