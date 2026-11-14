import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type ChatRoom } from '@/entities/chat'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, Modal } from '@/shared/ui'
import { useSetNotice } from '../model/use-set-notice'

export function NoticeModal({ room, dialog }: { room: ChatRoom; dialog: Dialog }) {
  const { open, close: onClose } = dialog
  const { t } = useTranslation()
  const setNotice = useSetNotice(room)
  const [text, setText] = useState(room.notice?.content ?? '')
  const [wasOpen, setWasOpen] = useState(open)
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
