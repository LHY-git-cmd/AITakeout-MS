import request from '@/utils/request'

const root = '/diet'

export const getDishNutrition = (dishId: number) => request({ url: `${root}/dishes/${dishId}/nutrition`, method: 'get' })
export const saveDishNutrition = (dishId: number, data: any) => request({ url: `${root}/dishes/${dishId}/nutrition`, method: 'put', data })
export const verifyDishNutrition = (dishId: number, version: number) => request({ url: `${root}/dishes/${dishId}/nutrition/${version}/verify`, method: 'post' })
export const listDietRuleSets = () => request({ url: `${root}/rule-sets`, method: 'get' })
export const createDietRuleSet = (data: any) => request({ url: `${root}/rule-sets`, method: 'post', data })
export const validateDietRuleSet = (id: string) => request({ url: `${root}/rule-sets/${id}/validate`, method: 'post' })
export const publishDietRuleSet = (id: string) => request({ url: `${root}/rule-sets/${id}/publish`, method: 'post' })
export const offlineDietRuleSet = (id: string) => request({ url: `${root}/rule-sets/${id}/offline`, method: 'post' })
export const rollbackDietRuleSet = (id: string) => request({ url: `${root}/rule-sets/${id}/rollback`, method: 'post' })
