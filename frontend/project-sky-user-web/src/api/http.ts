import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
}

interface RefreshedSession {
  accessToken: string
  user: {
    id: number
    name: string | null
    phone: string | null
    avatar: string | null
  }
}

interface RetryableRequest extends InternalAxiosRequestConfig {
  _authRetried?: boolean
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly code?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const baseConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
}

export const http = axios.create(baseConfig)
const refreshHttp = axios.create(baseConfig)
let accessToken: string | null = null
let refreshPromise: Promise<RefreshedSession> | null = null

/** Access Token 仅驻留当前页面内存，由认证状态统一写入。 */
export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

function apiError(error: AxiosError<ApiResult<unknown>>) {
  const message = error.response?.data?.msg
    || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接异常')
  return new ApiError(message, error.response?.status, error.response?.data?.code)
}

function unwrap<T>(result: ApiResult<T>, status: number) {
  if (typeof result?.code === 'number' && result.code !== 1) {
    throw new ApiError(result.msg || '请求失败', status, result.code)
  }
  return result.data
}

/** 使用独立客户端刷新，避免刷新接口自身再次进入 401 拦截循环。 */
export async function requestSessionRefresh<T = RefreshedSession>(): Promise<T> {
  try {
    const response = await refreshHttp.post<ApiResult<T>>('/user/auth/refresh')
    return unwrap(response.data, response.status)
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw apiError(error as AxiosError<ApiResult<unknown>>)
  }
}

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = requestSessionRefresh<RefreshedSession>()
      .catch((error) => {
        window.dispatchEvent(new CustomEvent('sky:unauthorized'))
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.authentication = accessToken
  return config
})

http.interceptors.response.use(
  (response) => {
    unwrap(response.data as ApiResult<unknown>, response.status)
    return response
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    const request = error.config as RetryableRequest | undefined
    const canRefresh = error.response?.status === 401
      && request
      && !request._authRetried
      && !request.url?.includes('/user/auth/refresh')
    if (!canRefresh) throw apiError(error)

    setAccessToken(null)
    try {
      const session = await refreshAccessToken()
      setAccessToken(session.accessToken)
      window.dispatchEvent(new CustomEvent<RefreshedSession>('sky:session-refreshed', { detail: session }))
      request._authRetried = true
      request.headers.authentication = session.accessToken
      return await http.request(request)
    } catch (refreshError) {
      throw refreshError instanceof ApiError ? refreshError : apiError(error)
    }
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}
