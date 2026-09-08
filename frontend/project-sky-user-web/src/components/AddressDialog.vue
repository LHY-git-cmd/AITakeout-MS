<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { MapPin, X } from '@lucide/vue'
import type { Address, AddressPayload } from '@/api/address'

const props = defineProps<{ address: Address | null; open: boolean; saving?: boolean; error?: string }>()
const emit = defineEmits<{ close: []; save: [payload: AddressPayload] }>()

const form = reactive<AddressPayload>({
  consignee: '', phone: '', sex: '1', provinceCode: '', provinceName: '', cityCode: '', cityName: '',
  districtCode: '', districtName: '', detail: '', label: '家',
})

watch(() => [props.open, props.address] as const, () => {
  if (!props.open) return
  Object.assign(form, props.address ? {
    id: props.address.id,
    consignee: props.address.consignee,
    phone: props.address.phone,
    sex: props.address.sex,
    provinceCode: props.address.provinceCode || '',
    provinceName: props.address.provinceName || '',
    cityCode: props.address.cityCode || '',
    cityName: props.address.cityName || '',
    districtCode: props.address.districtCode || '',
    districtName: props.address.districtName || '',
    detail: props.address.detail,
    label: props.address.label || '',
  } : {
    id: undefined, consignee: '', phone: '', sex: '1', provinceCode: '', provinceName: '', cityCode: '',
    cityName: '', districtCode: '', districtName: '', detail: '', label: '家',
  })
}, { immediate: true })

const valid = computed(() => form.consignee.trim().length > 0
  && /^1\d{10}$/.test(form.phone)
  && form.provinceName?.trim()
  && form.cityName?.trim()
  && form.districtName?.trim()
  && form.detail.trim().length >= 3)

function submit() {
  if (valid.value) emit('save', { ...form })
}
</script>

<template>
  <Transition name="dialog">
    <div v-if="open" class="dialog-layer" @click.self="$emit('close')">
      <section class="address-dialog" role="dialog" aria-modal="true" aria-labelledby="address-dialog-title">
        <header>
          <div><MapPin :size="21" /><h2 id="address-dialog-title">{{ address ? '编辑地址' : '新增地址' }}</h2></div>
          <button type="button" aria-label="关闭地址表单" @click="$emit('close')"><X :size="21" /></button>
        </header>
        <form @submit.prevent="submit">
          <div class="form-grid form-grid--contact">
            <label>收货人<input v-model.trim="form.consignee" maxlength="20" autocomplete="name" /></label>
            <label>手机号<input v-model.trim="form.phone" type="tel" maxlength="11" inputmode="numeric" autocomplete="tel" /></label>
          </div>
          <fieldset class="segment-field">
            <legend>性别</legend>
            <label :class="{ 'is-selected': form.sex === '1' }"><input v-model="form.sex" type="radio" value="1" />先生</label>
            <label :class="{ 'is-selected': form.sex === '0' }"><input v-model="form.sex" type="radio" value="0" />女士</label>
          </fieldset>
          <div class="form-grid form-grid--region">
            <label>省份<input v-model.trim="form.provinceName" placeholder="北京市" /></label>
            <label>城市<input v-model.trim="form.cityName" placeholder="北京市" /></label>
            <label>区县<input v-model.trim="form.districtName" placeholder="海淀区" /></label>
          </div>
          <label>详细地址<textarea v-model.trim="form.detail" rows="3" maxlength="100" placeholder="街道、门牌号、楼层房间号" /></label>
          <label>地址标签<input v-model.trim="form.label" maxlength="10" placeholder="家、公司或学校" /></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="form-submit" type="submit" :disabled="!valid || saving">{{ saving ? '保存中...' : '保存地址' }}</button>
        </form>
      </section>
    </div>
  </Transition>
</template>
