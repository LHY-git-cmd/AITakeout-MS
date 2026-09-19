/** 判断当前角色集合是否满足路由声明的角色要求。 */
export function canAccessRoute(route: any, roles: string[]) {
  const required = route && route.meta && route.meta.roles
  if (!required || required.length === 0) return true
  const normalized = (roles || []).map(role => String(role).toUpperCase())
  return required.some((role: string) => normalized.includes(role.toUpperCase()))
}
