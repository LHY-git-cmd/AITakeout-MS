/**
 * 验证菜单搜索与 AI 侧栏拖拽宽度的核心计算，防止界面改动后出现越界或错误筛选。
 */
import { describe, expect, it } from 'vitest'
import { clampAiPanelWidth, filterMenuProducts } from '../src/utils/menuLayout.ts'

const products = [
  { name: '香辣烤鱼', description: '麻辣鲜香' },
  { name: '番茄牛腩', description: '酸甜浓郁' },
  { name: '清炒时蔬', description: '' },
]

describe('菜单布局工具', () => {
  it('AI 侧栏宽度始终限制在可用范围内', () => {
    expect(clampAiPanelWidth(120, 240, 520)).toBe(240)
    expect(clampAiPanelWidth(360, 240, 520)).toBe(360)
    expect(clampAiPanelWidth(680, 240, 520)).toBe(520)
  })

  it('菜单搜索同时匹配名称和描述并忽略首尾空格', () => {
    expect(filterMenuProducts(products, '  烤鱼  ')).toEqual([products[0]])
    expect(filterMenuProducts(products, '浓郁')).toEqual([products[1]])
    expect(filterMenuProducts(products, '')).toEqual(products)
  })
})
