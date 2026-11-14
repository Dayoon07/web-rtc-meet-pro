import { currentLanguage } from '@/shared/i18n'
import { pushApi } from '../api/push-api'

const SW_URL = '/sw.js'

export const pushSupported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

const keyBytes = (base64url: string) => {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4)
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null
  const reg = await navigator.serviceWorker.getRegistration('/')
  return (await reg?.pushManager.getSubscription()) ?? null
}

async function register(sub: PushSubscription) {
  const json = sub.toJSON()
  await pushApi.subscribe({ endpoint: sub.endpoint, p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '', lang: currentLanguage() })
}

export async function enablePush(publicKey: string): Promise<'ok' | 'denied'> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return 'denied'
  const reg = await navigator.serviceWorker.register(SW_URL)
  await navigator.serviceWorker.ready
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) }))
  await register(sub)
  return 'ok'
}

export async function disablePush() {
  const sub = await currentSubscription()
  if (!sub) return
  await pushApi.unsubscribe(sub.endpoint).catch(() => undefined)
  await sub.unsubscribe()
}

export async function syncPush() {
  const sub = await currentSubscription()
  if (sub && Notification.permission === 'granted') await register(sub).catch(() => undefined)
}

export async function forgetPushOnLogout() {
  const sub = await currentSubscription().catch(() => null)
  if (sub) await pushApi.unsubscribe(sub.endpoint).catch(() => undefined)
}

export function onPushNavigate(go: (link: string) => void) {
  if (!pushSupported()) return () => undefined
  const handler = (e: MessageEvent) => {
    const data = e.data as { type?: string; link?: string } | null
    if (data?.type === 'mq:navigate' && data.link?.startsWith('/')) go(data.link)
  }
  navigator.serviceWorker.addEventListener('message', handler)
  return () => navigator.serviceWorker.removeEventListener('message', handler)
}
