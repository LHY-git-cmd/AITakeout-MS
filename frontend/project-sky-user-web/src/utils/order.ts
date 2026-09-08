export const ORDER_STATUS: Record<number, { label: string; tone: string }> = {
  1: { label: '待付款', tone: 'warning' },
  2: { label: '待接单', tone: 'warning' },
  3: { label: '已接单', tone: 'info' },
  4: { label: '派送中', tone: 'info' },
  5: { label: '已完成', tone: 'success' },
  6: { label: '已取消', tone: 'muted' },
}

export function statusInfo(status: number) {
  return ORDER_STATUS[status] ?? { label: '未知状态', tone: 'muted' }
}

export function canCancel(status: number) {
  return status === 1 || status === 2
}

export function canRemind(status: number) {
  return status === 2
}
