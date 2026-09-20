<script setup lang="ts">
import { computed } from 'vue'
import { cityOptions, districtOptions, provinceOptions, type RegionValue } from '@/data/china-regions'

const props = defineProps<{ modelValue: RegionValue; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: RegionValue] }>()

const cities = computed(() => cityOptions(props.modelValue.provinceCode))
const districts = computed(() => districtOptions(props.modelValue.cityCode))

function codeFrom(event: Event) {
  return (event.target as HTMLSelectElement).value
}

function selectProvince(event: Event) {
  const provinceCode = codeFrom(event)
  const provinceName = provinceOptions.find((item) => item.code === provinceCode)?.name ?? ''
  emit('update:modelValue', { provinceCode, provinceName, cityCode: '', cityName: '', districtCode: '', districtName: '' })
}

function selectCity(event: Event) {
  const cityCode = codeFrom(event)
  const cityName = cities.value.find((item) => item.code === cityCode)?.name ?? ''
  emit('update:modelValue', { ...props.modelValue, cityCode, cityName, districtCode: '', districtName: '' })
}

function selectDistrict(event: Event) {
  const districtCode = codeFrom(event)
  const districtName = districts.value.find((item) => item.code === districtCode)?.name ?? ''
  emit('update:modelValue', { ...props.modelValue, districtCode, districtName })
}
</script>

<template>
  <fieldset class="region-select" :disabled="disabled">
    <legend>所在地区</legend>
    <label for="address-province">省份
      <select id="address-province" data-test="province" :value="modelValue.provinceCode" required @change="selectProvince">
        <option value="">请选择省份</option>
        <option v-for="item in provinceOptions" :key="item.code" :value="item.code">{{ item.name }}</option>
      </select>
    </label>
    <label for="address-city">城市
      <select id="address-city" data-test="city" :value="modelValue.cityCode" :disabled="!modelValue.provinceCode" required @change="selectCity">
        <option value="">请选择城市</option>
        <option v-for="item in cities" :key="item.code" :value="item.code">{{ item.name }}</option>
      </select>
    </label>
    <label for="address-district">区县
      <select id="address-district" data-test="district" :value="modelValue.districtCode" :disabled="!modelValue.cityCode" required @change="selectDistrict">
        <option value="">请选择区县</option>
        <option v-for="item in districts" :key="item.code" :value="item.code">{{ item.name }}</option>
      </select>
    </label>
  </fieldset>
</template>
