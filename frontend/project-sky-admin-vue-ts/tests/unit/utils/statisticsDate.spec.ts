/**
 * 回归验证统计页日期范围包含当天，确保当天新增业务数据能够进入图表。
 */
import { past7Day, past30Day, pastWeek, pastMonth } from '@/utils/formValidate'

describe('statistics date ranges', () => {
  beforeEach(() => {
    jest.useFakeTimers('modern')
    jest.setSystemTime(new Date(2026, 9, 1, 12, 0, 0))
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  it('includes today in rolling ranges', () => {
    expect(past7Day()).toEqual(['2026-09-25', '2026-10-01'])
    expect(past30Day()).toEqual(['2026-09-02', '2026-10-01'])
  })

  it('stops current week and month at today instead of a future date', () => {
    expect(pastWeek()).toEqual(['2026-09-28', '2026-10-01'])
    expect(pastMonth()).toEqual(['2026-10-01', '2026-10-01'])
  })
})
