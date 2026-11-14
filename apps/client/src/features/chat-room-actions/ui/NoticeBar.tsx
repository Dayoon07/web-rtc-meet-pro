import { ChevronDown, Megaphone, Pencil, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatRoom } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { LinkifiedText } from '@/shared/ui'
import { readHidden, saveHidden } from '../lib/notice-hidden'
import { barIcon } from '../lib/styles'

export function NoticeBar({ room, onEdit }: { room: ChatRoom; onEdit?: () => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const notice = room.notice
  const [open, setOpen] = useState(false)
  const [hiddenAt, setHiddenAt] = useState(() => readHidden(room.id))
  if (!notice) return null
  const stamp = notice.at ?? notice.content

  const hide = (value: string | null) => {
    setHiddenAt(value)
    saveHidden(room.id, value)
  }

  if (hiddenAt === stamp) {
    return (
      <div className="flex shrink-0 justify-end border-b border-gray-200 px-4 py-1 dark:border-gray-800">
        <button
          type="button"
          onClick={() => hide(null)}
          className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10"
        >
          <Megaphone className="size-3.5" />
          {t('chat.notice.show')}
        </button>
      </div>
    )
  }

  return (
    <div className="shrink-0 border-b border-gray-200 bg-brand-50/60 px-4 py-2 dark:border-gray-800 dark:bg-brand-500/10">
      <div className="flex items-start gap-2.5">
        <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white">
          <Megaphone className="size-3.5" />
        </span>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          title={t(open ? 'chat.notice.less' : 'chat.notice.more')}
          className="min-w-0 flex-1 text-left"
        >
          <span className="text-xs font-semibold text-brand-700 dark:text-brand-300">{t('chat.notice.label')}</span>
          <p className={cn('text-sm break-words text-gray-800 dark:text-gray-100', open ? 'whitespace-pre-wrap' : 'truncate')}>
            {open ? <LinkifiedText text={notice.content} /> : notice.content.replace(/\s+/g, ' ')}
          </p>
          {open && (
            <span className="mt-1 block text-xs text-gray-500 dark:text-gray-400">
              {[notice.by?.nickname, notice.at && fmt.dateTime(notice.at)].filter(Boolean).join(' · ')}
            </span>
          )}
        </button>
        <span className="flex shrink-0 items-center gap-0.5">
          {onEdit && (
            <button type="button" onClick={onEdit} aria-label={t('chat.notice.edit')} title={t('chat.notice.edit')} className={barIcon}>
              <Pencil className="size-3.5" />
            </button>
          )}
          <button type="button" onClick={() => setOpen((v) => !v)} aria-label={t(open ? 'chat.notice.less' : 'chat.notice.more')} className={barIcon}>
            <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} />
          </button>
          <button type="button" onClick={() => hide(stamp)} aria-label={t('chat.notice.hide')} title={t('chat.notice.hide')} className={barIcon}>
            <X className="size-4" />
          </button>
        </span>
      </div>
    </div>
  )
}
