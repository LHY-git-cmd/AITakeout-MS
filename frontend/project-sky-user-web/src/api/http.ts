import axios, { AxiosError } from 'axios'

export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
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

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('sky-user-token')
  if (token) {
    config.headers.authentication = token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (typeof result?.code === 'number' && result.code !== 1) {
      return Promise.reject(new ApiError(result.msg || '请求失败', response.status, result.code))
    }
    return response
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('sky-user-token')
      window.dispatchEvent(new CustomEvent('sky:unauthorized'))
    }
    const message = error.response?.data?.msg || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络连接异常')
    return Promise.reject(new ApiError(message, error.response?.status, error.response?.data?.code))
  },
)

export async function request<T>(config: Parameters<typeof http.request>[0]): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}
