import { BellRing, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui'
import { usePushToggle } from '../model/use-push-toggle'

const DISMISSED_KEY = 'mq.pushPromptDismissed'

const wasDismissed = () => {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * 채팅 화면 위 "크롬 알림 켜기" 안내. 아직 브라우저에 알림을 허용할지 묻지 않았을 때만 보인다.
 * 켜면 이 브라우저를 푸시 구독까지 한다 (탭을 닫아도 알림). 닫으면 다시 보이지 않는다 (설정 화면에서 켤 수 있음)
 */
export function PushPromptBanner() {
  const { t } = useTranslation()
  const push = usePushToggle()
  const [dismissed, setDismissed] = useState(wasDismissed)
  const asked = 'Notification' in window && Notification.permission !== 'default'
  if (dismissed || asked || !push.ready || push.state !== 'off') return null

  const dismiss = () => {
    setDismissed(true)
    try {
      localStorage.setItem(DISMISSED_KEY, '1')
    } catch {
      // 이번 화면에서만 숨긴다
    }
  }

  return (
    <div className="flex shrink-0 items-center gap-3 border-b border-brand-100 bg-brand-50 px-4 py-2 text-sm text-brand-800 dark:border-brand-500/20 dark:bg-brand-500/10 dark:text-brand-200">
      <BellRing className="size-4 shrink-0" />
      <span className="min-w-0 flex-1">{t('notifySettings.prompt.text')}</span>
      <Button size="sm" loading={push.busy} onClick={() => void push.toggle()}>
        {t('notifySettings.prompt.enable')}
      </Button>
      <button type="button" onClick={dismiss} aria-label={t('notifySettings.prompt.dismiss')} title={t('notifySettings.prompt.dismiss')} className="rounded p-1 hover:bg-brand-100 dark:hover:bg-brand-500/20">
        <X className="size-4" />
      </button>
    </div>
  )
}
