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
