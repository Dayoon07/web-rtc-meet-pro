import { API, request } from '@/shared/api'
import type { PushConfig, PushSubscribeRequest, PushUnsubscribeRequest } from '../model/types'

export const pushKeys = { config: ['push', 'config'] as const }

export const pushApi = {
  config: () => request<PushConfig>(API.push.config),
  subscribe: (body: PushSubscribeRequest) => request<void>(API.push.subscriptions, { method: 'POST', json: body }),
  unsubscribe: (endpoint: string) => request<void>(API.push.subscriptions, { method: 'DELETE', json: { endpoint } satisfies PushUnsubscribeRequest }),
}
