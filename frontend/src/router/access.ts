import type {UserRole} from '../api/session'

export const USER_HOME_PATH = '/'
export const ADMIN_ENTRY_PATH = '/admin/alerts'

const USER_ROUTE_PATHS = new Set([USER_HOME_PATH, '/chat'])

function readPathname(target: string) {
  if (!target.startsWith('/') || target.startsWith('//')) return ''

  try {
    return new URL(target, 'http://qzhipass.local').pathname
  } catch {
    return ''
  }
}

export function isAdminTarget(target: string) {
  const pathname = readPathname(target)
  return pathname === '/admin' || pathname.startsWith('/admin/') || pathname === '/token'
}

function isUserTarget(target: string) {
  return USER_ROUTE_PATHS.has(readPathname(target))
}

export function getDefaultPathForRole(role: UserRole) {
  return role === 'ADMIN' ? ADMIN_ENTRY_PATH : USER_HOME_PATH
}

export function resolvePostLoginPath(role: UserRole, redirect?: string) {
  if (redirect && (isUserTarget(redirect) || (role === 'ADMIN' && isAdminTarget(redirect)))) {
    return redirect
  }

  return getDefaultPathForRole(role)
}
