/** 验证省市区级联选择会提交代码与名称，并在上级变化时清空下级。 */
// @vitest-environment jsdom
import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RegionSelect from '@/components/RegionSelect.vue'
import { emptyRegion } from '@/data/china-regions'

describe('地址三级联动', () => {
  it('更换省份后清空原城市和区县', async () => {
    const Host = defineComponent({
      components: { RegionSelect },
      setup() { return { region: ref(emptyRegion()) } },
      template: '<RegionSelect v-model="region" />',
    })
    const wrapper = mount(Host)

    await wrapper.get('[data-test=province]').setValue('110000')
    await wrapper.get('[data-test=city]').setValue('110100')
    await wrapper.get('[data-test=district]').setValue('110108')
    expect((wrapper.vm as unknown as { region: { districtName: string } }).region.districtName).toBe('海淀区')

    await wrapper.get('[data-test=province]').setValue('310000')
    const region = (wrapper.vm as unknown as { region: { provinceName: string; cityCode: string; districtCode: string } }).region
    expect(region.provinceName).toBe('上海市')
    expect(region.cityCode).toBe('')
    expect(region.districtCode).toBe('')
    expect((wrapper.get('[data-test=city]').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('[data-test=district]').element as HTMLSelectElement).value).toBe('')
  })
})
