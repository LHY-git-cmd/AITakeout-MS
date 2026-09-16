type SearchableProduct = {
  name: string
  description?: string | null
}

/** 将 AI 侧栏宽度限制在界面可用范围内。 */
export function clampAiPanelWidth(width: number, minimum: number, maximum: number) {
  return Math.min(Math.max(width, minimum), maximum)
}

/** 按商品名称或描述过滤当前分类中的商品。 */
export function filterMenuProducts<T extends SearchableProduct>(products: T[], keyword: string) {
  const normalizedKeyword = keyword.trim().toLocaleLowerCase()
  if (!normalizedKeyword) return products

  return products.filter((product) => {
    const content = `${product.name} ${product.description ?? ''}`.toLocaleLowerCase()
    return content.includes(normalizedKeyword)
  })
}
