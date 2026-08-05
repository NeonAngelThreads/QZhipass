import http from './http'
import { readLoginInfo } from './session'
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
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  const accessToken = readLoginInfo()?.accessToken
  const response = await fetch(`${baseUrl}/v1/agents/${agentId}/invoke`, {
    method: 'POST',
    credentials: 'include',
    signal,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok || !response.body) {
    throw new Error('Agent调用失败，请稍后重试。')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let streamFailed = false

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      const data = block
        .split('\n')
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trimStart())
        .join('\n')
      if (!data) continue

      const event = JSON.parse(data) as AgentStreamEvent
      onEvent(event)
      if (event.type === 'error') {
        streamFailed = true
      }
    }

    if (done) break
  }

  if (streamFailed) {
    throw new Error('Agent调用失败，请稍后重试。')
  }
}
