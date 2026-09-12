import request from '@/utils/request'

export const listAgentSessions = (params: any) => request({ url: '/agent/sessions', method: 'get', params })
export const getAgentSession = (sessionId: string) => request({ url: `/agent/sessions/${sessionId}`, method: 'get' })
export const updateAgentSession = (data: any) => request({ url: '/agent/sessions', method: 'put', data })
export const submitAgentTask = (data: any) => request({ url: '/agent/tasks/submit', method: 'post', data })
export const cancelAgentTask = (taskId: string) => request({ url: `/agent/tasks/${taskId}/cancel`, method: 'post' })
