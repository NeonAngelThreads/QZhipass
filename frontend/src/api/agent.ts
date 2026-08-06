import http from './http'
import type { ApiResponse, ConversationTurnPayload } from './conversation'

export interface AgentPayload {
  id: string
  name: string
  prompt: string
  source: 'USER' | 'TEMPLATE'
  createdAt?: string | null
}

export interface AgentStreamEvent {
  type: 'agent_start' | 'tool_start' | 'tool_end' | 'answer_start' | 'content' | 'complete' | 'error'
  message?: string | null
  content?: string | null
  agentName?: string | null
  turn?: ConversationTurnPayload | null
}

export interface InvokeAgentPayload {
  conversationId: string
  prompt: string
  modelKey: string
  requestId: string
}

export async function listAgents() {
  const response = await http.get<ApiResponse<AgentPayload[]>>('/v1/agents')
  return response.data.data ?? []
}

export async function createAgent(name: string, prompt: string) {
  const response = await http.post<ApiResponse<AgentPayload>>('/v1/agents', { name, prompt })
  if (!response.data.data) throw new Error(response.data.message || 'Agent创建失败')
  return response.data.data
}

export async function deleteAgent(agentId: string) {
  const response = await http.delete<ApiResponse<null>>(`/v1/agents/${agentId}`)
  if (response.data.success === false) throw new Error(response.data.message || 'Agent删除失败')
}

export async function invokeAgentStream(
    agentId: string,
    payload: InvokeAgentPayload,
    onEvent: (event: AgentStreamEvent) => void,
    signal?: AbortSignal,
) {
  void agentId
  void payload
  void onEvent
  void signal
  throw new Error('后端尚未提供 Agent 调用接口，当前无法完成真实调用。')
}
