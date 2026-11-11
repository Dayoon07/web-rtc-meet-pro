import { Download, FileText, NotebookPen } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { ChatMessage, MeetingNotesCard } from '@/entities/chat'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Button } from '@/shared/ui'
import { useRoom } from '../model/context'

/**
 * 첨부파일: 이미지는 미리보기(누르면 새 탭에서 원본), 나머지는 이름·크기 + 내려받기.
 * 주소는 서버가 서명해 준 것이라 <img src> 로 바로 쓴다.
 */
export function MessageFiles({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { myId, messages } = useRoom()
  const mine = message.sender?.id === myId
  const images = message.files.filter((f) => f.image)
  const others = message.files.filter((f) => !f.image)
  return (
    <div className={cn('flex flex-col gap-1.5', mine ? 'items-end' : 'items-start')}>
      {images.length > 0 && (
        <div className={cn(images.length === 1 ? 'flex' : 'grid gap-1.5', images.length === 2 || images.length === 4 ? 'grid-cols-2' : 'grid-cols-3')}>
          {images.map((f) => (
            <a
              key={f.id}
              href={f.url}
              target="_blank"
              rel="noopener noreferrer"
              title={t('chat.openImage') + ' · ' + f.name}
              className="block overflow-hidden rounded-xl bg-gray-100 ring-1 ring-gray-200 dark:bg-gray-800 dark:ring-gray-700"
            >
              <img
                src={f.url}
                alt={f.name}
                loading="lazy"
                onLoad={messages.followBottom}
                // 한 장은 원래 비율 그대로 (말풍선 영역은 너비가 내용에 맞춰지므로 % 대신 화면 기준 최대 너비)
                className={images.length === 1 ? 'block h-auto max-h-72 w-auto max-w-[min(24rem,60vw)] object-contain' : 'size-28 object-cover sm:size-32'}
              />
            </a>
          ))}
        </div>
      )}
      {others.map((f) => (
        <a
          key={f.id}
          href={f.url + '&download=true'}
          download={f.name}
          title={t('chat.download') + ' · ' + f.name}
          className="flex max-w-72 items-center gap-3 rounded-2xl bg-white px-3 py-2.5 text-left shadow-xs ring-1 ring-gray-200 hover:bg-gray-50 dark:bg-gray-800 dark:ring-gray-700 dark:hover:bg-gray-700/70"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
            <FileText className="size-4.5" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-medium text-gray-900 dark:text-gray-100">{f.name}</span>
            <span className="block text-xs text-gray-500 dark:text-gray-400">{fmt.bytes(f.size)}</span>
          </span>
          <Download className="size-4 shrink-0 text-gray-400" />
        </a>
      ))}
    </div>
  )
}

/** 내 글 메시지를 그 자리에서 고치는 칸 (Enter 저장, Shift+Enter 줄바꿈, Esc 취소) */
export function EditBubble({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const { messages } = useRoom()
  const [value, setValue] = useState(message.content ?? '')
  const [saving, setSaving] = useState(false)
  const save = async () => {
    const content = value.trim()
    if (!content || content === message.content) return messages.cancelEdit()
    setSaving(true)
    try {
      await messages.saveEdit(message, content)
    } finally {
      setSaving(false)
    }
  }
  return (
    <div className="flex w-full max-w-md flex-col gap-1.5">
      <textarea
        autoFocus
        value={value}
        maxLength={4000}
        rows={Math.min(6, Math.max(2, value.split('\n').length))}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') messages.cancelEdit()
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            void save()
          }
        }}
        className="w-full resize-none rounded-2xl border border-brand-300 bg-white px-3.5 py-2 text-[15px] leading-relaxed text-gray-900 outline-none focus:ring-3 focus:ring-brand-500/15 dark:border-brand-700 dark:bg-gray-900 dark:text-gray-100"
      />
      <div className="flex justify-end gap-1.5">
        <Button size="sm" variant="ghost" onClick={messages.cancelEdit}>
          {t('common.cancel')}
        </Button>
        <Button size="sm" loading={saving} onClick={() => void save()}>
          {t('common.save')}
        </Button>
      </div>
    </div>
  )
}

/** 채널에 올라온 AI 회의록: 요약 몇 줄 + 결정·할 일 개수, 누르면 회의 상세 */
export function NotesCard({ card }: { card: MeetingNotesCard }) {
  const { t } = useTranslation()
  return (
    <Link
      to={ROUTES.meeting(card.meetingId)}
      className="my-4 block max-w-xl rounded-2xl border border-brand-200 bg-brand-50/60 px-4 py-3 transition hover:border-brand-400 dark:border-brand-500/30 dark:bg-brand-500/10"
    >
      <p className="flex items-center gap-1.5 text-xs font-semibold text-brand-700 dark:text-brand-300">
        <NotebookPen className="size-3.5" />
        {t('notes.cardLabel')}
      </p>
      <p className="mt-1 font-semibold text-gray-900 dark:text-white">{card.title}</p>
      {card.summary.length > 0 && (
        <ul className="mt-1.5 flex list-disc flex-col gap-0.5 pl-5 text-sm text-gray-700 dark:text-gray-300">
          {card.summary.map((s, i) => (
            <li key={i} className="line-clamp-2">
              {s}
            </li>
          ))}
        </ul>
      )}
      <p className="mt-2 text-xs text-gray-500">
        {t('notes.cardCounts', { decisions: card.decisions, actions: card.actions })} · {t('notes.cardOpen')}
      </p>
    </Link>
  )
}
