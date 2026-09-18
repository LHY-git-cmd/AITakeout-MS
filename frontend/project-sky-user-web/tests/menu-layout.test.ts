/**
 * 验证菜单搜索与 AI 侧栏拖拽宽度的核心计算，防止界面改动后出现越界或错误筛选。
 */
// @ts-ignore
import test from 'node:test'
// @ts-ignore
import assert from 'node:assert/strict'
// @ts-ignore
import { clampAiPanelWidth, filterMenuProducts } from '../src/utils/menuLayout.ts'

const products = [
  { name: '香辣烤鱼', description: '麻辣鲜香' },
  { name: '番茄牛腩', description: '酸甜浓郁' },
  { name: '清炒时蔬', description: '' },
]

test('AI 侧栏宽度始终限制在可用范围内', () => {
  assert.equal(clampAiPanelWidth(120, 240, 520), 240)
  assert.equal(clampAiPanelWidth(360, 240, 520), 360)
  assert.equal(clampAiPanelWidth(680, 240, 520), 520)
})

test('菜单搜索同时匹配名称和描述并忽略首尾空格', () => {
  assert.deepEqual(filterMenuProducts(products, '  烤鱼  '), [products[0]])
  assert.deepEqual(filterMenuProducts(products, '浓郁'), [products[1]])
  assert.deepEqual(filterMenuProducts(products, ''), products)
})
