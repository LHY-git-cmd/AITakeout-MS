/** 验证创建员工要求独立密码，编辑资料保持兼容；不调用真实业务接口。 */
import { shallowMount } from '@vue/test-utils'
import EmployeeForm from '@/views/employee/addEmployee.vue'

jest.mock('@/api/employee', () => ({
  queryEmployeeById: jest.fn(() => Promise.resolve({ data: { code: 1, data: { id: 7, sex: '1' } } })),
  addEmployee: jest.fn(),
  editEmployee: jest.fn(),
}))
jest.mock('@/components/HeadLable/index.vue', () => ({ render: (h: any) => h('div') }))

describe('employee initial password', () => {
  function form(query: Record<string, string> = {}) {
    return shallowMount(EmployeeForm, {
      mocks: { $route: { query }, $router: { push: jest.fn() } },
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-radio-group', 'el-radio', 'el-button'],
    })
  }

  it('rejects an empty or short initial password when creating an employee', () => {
    const wrapper = form()
    const validate = (wrapper.vm as any).rules.initialPassword[0].validator
    const callback = jest.fn()
    validate({}, '', callback)
    expect(callback).toHaveBeenLastCalledWith(expect.any(Error))
    validate({}, 'short', callback)
    expect(callback).toHaveBeenLastCalledWith(expect.any(Error))
    wrapper.destroy()
  })

  it('accepts the same password format as the server', () => {
    const wrapper = form()
    const callback = jest.fn()
    ;(wrapper.vm as any).rules.initialPassword[0].validator({}, 'Test-only-password-2026!', callback)
    expect(callback).toHaveBeenCalledWith()
    wrapper.destroy()
  })

  it('does not require a new password when editing employee details', async () => {
    const wrapper = form({ id: '7' })
    await wrapper.vm.$nextTick()
    const callback = jest.fn()
    ;(wrapper.vm as any).rules.initialPassword[0].validator({}, '', callback)
    expect(callback).toHaveBeenCalledWith()
    wrapper.destroy()
  })
})
