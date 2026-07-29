export interface AgentHotkeyItem {
  label: string
  key: string
}

export interface CreateAgentPayload {
  agentName: string
  promptContent: string
}

export interface AgentItem {
  agentId: string
  agentName: string
  promptContent: string
  presetCategory: string
  createdAt: string
}
