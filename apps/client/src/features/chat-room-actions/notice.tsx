import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Megaphone, Pencil, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Button, LinkifiedText, Modal } from '@/shared/ui'

const hiddenKey = (roomId: number) => `mq.noticeHidden.${roomId}`
const readHidden = (roomId: number) => {
  try {
    return localStorage.getItem(hiddenKey(roomId))
  } catch {
    return null
  }
}

/** 공지 올리기·고치기·내리기 (서버에 저장하고 방 목록을 다시 받는다) */
export function useSetNotice(room: ChatRoom) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string | null) => chatApi.setNotice(room.id, content),
    onSuccess: (_, content) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      toast.success(t(content ? 'chat.notice.posted' : 'chat.notice.removed'))
    },
    onError: (e) => toastError(e),
  })
}

/**
 * 방 위쪽에 고정된 공지. 한 줄로 보이고 누르면 펼친다.
 * 숨기면 이 브라우저에서만 숨기고, 새 공지가 올라오면 다시 보인다.
 */
export function NoticeBar({ room, canEdit, onEdit }: { room: ChatRoom; canEdit: boolean; onEdit: () => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const notice = room.notice
  const [open, setOpen] = useState(false)
  const [hiddenAt, setHiddenAt] = useState(() => readHidden(room.id))
  if (!notice) return null
  const stamp = notice.at ?? notice.content

  if (hiddenAt === stamp) {
    return (
      <div className="flex shrink-0 justify-end border-b border-gray-200 px-4 py-1 dark:border-gray-800">
        <button
          type="button"
          onClick={() => {
            setHiddenAt(null)
            try {
              localStorage.removeItem(hiddenKey(room.id))
            } catch {
              // 저장이 막힌 브라우저면 이번 화면에서만
            }
          }}
          className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10"
        >
          <Megaphone className="size-3.5" />
          {t('chat.notice.show')}
        </button>
      </div>
    )
  }

  const hide = () => {
    setHiddenAt(stamp)
    try {
      localStorage.setItem(hiddenKey(room.id), stamp)
    } catch {
      // 위와 같음
    }
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
          {canEdit && (
            <button type="button" onClick={onEdit} aria-label={t('chat.notice.edit')} title={t('chat.notice.edit')} className={barIcon}>
              <Pencil className="size-3.5" />
            </button>
          )}
          <button type="button" onClick={() => setOpen((v) => !v)} aria-label={t(open ? 'chat.notice.less' : 'chat.notice.more')} className={barIcon}>
            <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} />
          </button>
          <button type="button" onClick={hide} aria-label={t('chat.notice.hide')} title={t('chat.notice.hide')} className={barIcon}>
            <X className="size-4" />
          </button>
        </span>
      </div>
    </div>
  )
}

const barIcon = 'rounded-md p-1 text-gray-400 hover:bg-white hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200'

/** 공지 쓰기·고치기 창 */
export function NoticeModal({ room, open, onClose }: { room: ChatRoom; open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const setNotice = useSetNotice(room)
  const [text, setText] = useState(room.notice?.content ?? '')
  const [wasOpen, setWasOpen] = useState(open)
  // 창을 열 때마다 지금 공지로 채운다
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) setText(room.notice?.content ?? '')
  }
  const save = () => setNotice.mutate(text.trim(), { onSuccess: onClose })
  const remove = () => {
    if (!window.confirm(t('chat.notice.removeConfirm'))) return
    setNotice.mutate(null, { onSuccess: onClose })
  }
  return (
    <Modal open={open} onClose={onClose} title={t('chat.notice.title')} size="md">
      <div className="flex flex-col gap-3">
        <textarea
          autoFocus
          value={text}
          maxLength={1000}
          rows={6}
          placeholder={t('chat.notice.placeholder')}
          onChange={(e) => setText(e.target.value)}
          className="w-full resize-y rounded-xl border border-gray-300 bg-white px-3.5 py-2.5 text-[15px] leading-relaxed text-gray-900 outline-none focus:border-brand-500 focus:ring-3 focus:ring-brand-500/15 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100"
        />
        <p className="flex justify-between text-xs text-gray-500 dark:text-gray-400">
          <span>{t('chat.notice.hint')}</span>
          <span>{text.length.toLocaleString()} / 1,000</span>
        </p>
        <div className="flex items-center gap-2">
          {room.notice && (
            <Button variant="ghost" onClick={remove} disabled={setNotice.isPending} className="text-red-600 dark:text-red-400">
              {t('chat.notice.remove')}
            </Button>
          )}
          <span className="flex-1" />
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={save} loading={setNotice.isPending} disabled={!text.trim() || text.trim() === room.notice?.content}>
            {t('chat.notice.save')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
