import http from './http'

export interface ApiResponse<T> {
  success?: boolean
  message?: string
  data?: T
}

export interface ConversationPayload {
  id: number
  title: string
  modelKey?: string | null
  createdAt: string
  lastMessageAt: string
}

export interface ConversationMessagePayload {
  id: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  createdAt: string
}

export interface ConversationDetailPayload {
  conversation: ConversationPayload
  messages: ConversationMessagePayload[]
}

export interface ConversationTurnPayload {
  conversation: ConversationPayload
  userMessage: ConversationMessagePayload
  assistantMessage: ConversationMessagePayload
  contextTokens: number
}

export async function listConversations(page = 0, limit = 20) {
  const response = await http.get<ApiResponse<ConversationPayload[]>>('/v1/conversations', {
    params: { page, limit }
  })
  return response.data.data ?? []
}

export async function getConversation(conversationId: number) {
  const response = await http.get<ApiResponse<ConversationDetailPayload>>(`/v1/conversations/${conversationId}`)
  if (!response.data.data) throw new Error(response.data.message || '读取对话失败')
  return response.data.data
}

export async function createConversation(modelKey?: string) {
  const response = await http.post<ApiResponse<ConversationPayload>>('/v1/conversations', { modelKey })
  if (!response.data.data) throw new Error(response.data.message || '新建对话失败')
  return response.data.data
}

export async function sendConversationTurn(
  conversationId: number | null,
  prompt: string,
  modelKey: string,
  requestId: string
) {
  const response = await http.post<ApiResponse<ConversationTurnPayload>>(
    conversationId === null ? '/v1/conversations/turns' : `/v1/conversations/${conversationId}/turns`,
    { prompt, modelKey, requestId }
  )
  if (!response.data.data) throw new Error(response.data.message || 'AI 回复失败')
  return response.data.data
}
