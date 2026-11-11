import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { currentSubscription, disablePush, enablePush, pushApi, pushKeys, pushSupported } from '@/entities/push'
import { toastError } from '@/shared/api'

export type PushState = 'unsupported' | 'unavailable' | 'blocked' | 'on' | 'off'

/**
 * 이 브라우저의 푸시 알림 켜기·끄기.
 * state: unsupported(브라우저가 못 함) · unavailable(서버 키 없음) · blocked(브라우저에서 막음) · on · off
 */
export function usePushToggle() {
  const { t } = useTranslation()
  const supported = pushSupported()
  const { data: config } = useQuery({ queryKey: pushKeys.config, queryFn: pushApi.config, staleTime: Infinity, enabled: supported })
  const [subscribed, setSubscribed] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!supported) return
    void currentSubscription().then((s) => setSubscribed(!!s && Notification.permission === 'granted'))
  }, [supported])

  const state: PushState = !supported
    ? 'unsupported'
    : config && !config.enabled
      ? 'unavailable'
      : Notification.permission === 'denied'
        ? 'blocked'
        : subscribed
          ? 'on'
          : 'off'

  const toggle = async () => {
    if (busy || !config?.publicKey) return
    setBusy(true)
    try {
      if (subscribed) {
        await disablePush()
        setSubscribed(false)
        toast(t('notifySettings.push.turnedOff'))
      } else if ((await enablePush(config.publicKey)) === 'ok') {
        setSubscribed(true)
        toast.success(t('notifySettings.push.turnedOn'))
      } else {
        toast.error(t('notifySettings.push.blocked'))
      }
    } catch (e) {
      toastError(e)
    } finally {
      setBusy(false)
    }
  }

  return { state, busy, ready: subscribed !== null || !supported, toggle }
}
