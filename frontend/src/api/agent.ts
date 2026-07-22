import http, {getErrorMessage} from './http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T | null
}

export interface AgentDeleteCheck {
  agentId: string
  agentName: string
  canDelete: boolean
  referenced: boolean
  conversationTitle?: string
  action: 'REMOVE_FROM_MY_LIST' | 'DELETE_GLOBALLY'
  alreadyRemovedOrDeleted: boolean
  message: string
}

export interface AgentDeleteResult {
  agentId: string
  action: 'REMOVE_FROM_MY_LIST' | 'DELETE_GLOBALLY'
  alreadyRemovedOrDeleted: boolean
  message: string
}

export async function checkAgentDeletion(agentId: string) {
  try {
    const {data} = await http.get<ApiResponse<AgentDeleteCheck>>(
      `/v1/agent/${encodeURIComponent(agentId)}/delete-check`
    )
    if (!data.success || !data.data) {
      throw new Error(data.message || '无法检查 Agent 引用状态')
    }
    return data.data
  } catch (error) {
    throw new Error(getErrorMessage(error, '请求失败，请稍后重试'))
  }
}

export async function deleteAgent(agentId: string) {
  try {
    const {data} = await http.delete<ApiResponse<AgentDeleteResult>>(
      `/v1/agent/${encodeURIComponent(agentId)}`
    )
    if (!data.success || !data.data) {
      throw new Error(data.message || 'Agent 删除失败')
    }
    return data.data
  } catch (error) {
    throw new Error(getErrorMessage(error, '请求失败，请稍后重试'))
  }
}
