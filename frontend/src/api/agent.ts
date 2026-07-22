import http, { getErrorMessage } from './http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface AgentSummary {
  agentId: number
  agentName: string
  agentType: 'SYSTEM_PRESET' | 'USER_CREATED'
  deleteMode?: 'REMOVE_FROM_USER_LIBRARY' | 'DELETE_USER_AGENT' | 'ADMIN_GLOBAL_DELETE'
  deletable: boolean
  inUserLibrary: boolean
}

export interface AgentDeleteCheck {
  agentId: number
  agentName: string
  deleteMode: 'REMOVE_FROM_USER_LIBRARY' | 'DELETE_USER_AGENT' | 'ADMIN_GLOBAL_DELETE'
  canDelete: boolean
  reasonCode?: string
  reasonMessage?: string
  referencedConversations: Array<{ conversationId: number; title: string }>
  confirmationMessage: string
}

export interface AgentInvokeResponse {
  conversationId: number
  messageId: number
  agentId: number
  agentName: string
  content: string
  invokedAt: string
}

export interface ConversationMessage {
  messageId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  agentId?: number
  createTime?: string
}

interface ConversationDetail {
  messages?: Array<{
    id: number
    role: 'USER' | 'ASSISTANT'
    content: string
    createdAt?: string
  }>
}

async function withAgentError<T>(operation: () => Promise<T>, fallback: string) {
  try {
    return await operation()
  } catch (error) {
    throw new Error(getErrorMessage(error, fallback))
  }
}

export function createAgent(name: string, prompt: string) {
  return withAgentError(async () => {
    const response = await http.post<ApiResponse<{ id: number; name: string }>>(
      '/api/v1/agents',
      { name, prompt }
    )
    return response.data.data
  }, 'Agent创建失败')
}

export function listAgents(keyword = '') {
  return withAgentError(async () => {
    const response = await http.get<ApiResponse<AgentSummary[]>>('/api/v1/agents', {
      params: keyword ? { q: keyword } : undefined
    })
    return response.data.data || []
  }, 'Agent列表加载失败')
}

export function listAgentCatalog(keyword = '') {
  return withAgentError(async () => {
    const response = await http.get<ApiResponse<AgentSummary[]>>('/api/v1/agents/catalog', {
      params: keyword ? { q: keyword } : undefined
    })
    return response.data.data || []
  }, '系统Agent库加载失败')
}

export function addAgentToLibrary(agentId: number) {
  return withAgentError(async () => {
    await http.post(`/api/v1/agents/${agentId}/library`)
  }, 'Agent添加失败')
}

export function getAgentDeleteCheck(agentId: number) {
  return withAgentError(async () => {
    const response = await http.get<ApiResponse<AgentDeleteCheck>>(
      `/api/v1/agents/${agentId}/delete-check`
    )
    return response.data.data
  }, '无法检查Agent删除条件')
}

export function deleteAgent(agentId: number) {
  return withAgentError(async () => {
    await http.delete(`/api/v1/agents/${agentId}`)
  }, 'Agent删除失败')
}

export function invokeAgent(agentId: number, message: string, conversationId?: number) {
  return withAgentError(async () => {
    const response = await http.post<ApiResponse<AgentInvokeResponse>>(
      '/api/v1/conversations/invoke-agent',
      { agentId, message, conversationId }
    )
    return response.data.data
  }, 'Agent调用失败')
}

export function listConversationMessages(conversationId: number) {
  return withAgentError(async () => {
    const response = await http.get<ApiResponse<ConversationDetail>>(
      `/api/v1/conversations/${conversationId}`
    )
    return (response.data.data?.messages || []).map(message => ({
      messageId: message.id,
      role: message.role,
      content: message.content,
      createTime: message.createdAt
    }))
  }, '对话消息加载失败')
}
