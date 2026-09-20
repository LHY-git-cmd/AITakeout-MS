/**
 * 地址三级选择数据适配层。
 * 数据来源：area-data 5.0.6（MIT），本地固定版本，集成日期 2026-09-18。
 * 仅用于表单选择与编码提交，不作为实时行政区划法律效力依据。
 */
import rawRegions from 'area-data/pcaa'

export interface RegionOption {
  code: string
  name: string
}

export interface RegionValue {
  provinceCode: string
  provinceName: string
  cityCode: string
  cityName: string
  districtCode: string
  districtName: string
}

const directMunicipalities = new Set(['110000', '120000', '310000', '500000'])

function options(parentCode: string): RegionOption[] {
  return Object.entries(rawRegions[parentCode] ?? {}).map(([code, name]) => ({ code, name }))
}

export const provinceOptions = options('86')

export function cityOptions(provinceCode: string): RegionOption[] {
  const province = provinceOptions.find((item) => item.code === provinceCode)
  return options(provinceCode).map((item) => directMunicipalities.has(provinceCode) && province
    ? { ...item, name: province.name }
    : item)
}

export function districtOptions(cityCode: string): RegionOption[] {
  return options(cityCode)
}

export function emptyRegion(): RegionValue {
  return { provinceCode: '', provinceName: '', cityCode: '', cityName: '', districtCode: '', districtName: '' }
}
