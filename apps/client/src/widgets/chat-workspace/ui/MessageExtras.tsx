import { CornerUpLeft, SmilePlus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatMessage } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Avatar } from '@/shared/ui'
import { useRoom } from '../model/context'

/** 자주 쓰는 반응 (빠르게 고르기) */
export const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏']

/** 답장한 원래 메시지: 보낸 사람 · 한 줄 미리보기. 누르면 그 메시지로 간다 */
export function ReplyQuote({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const { myId, messages } = useRoom()
  const reply = message.replyTo
  if (!reply) return null
  const mine = message.sender?.id === myId
  const preview = reply.deleted
    ? t('chat.deletedMessage')
    : reply.content || (reply.fileCount > 1 ? t('chat.file.many', { count: reply.fileCount }) : reply.fileCount ? t('chat.file.one') : '')
  return (
    <button
      type="button"
      onClick={() => void messages.jumpTo(reply.id)}
      title={t('chat.goToOriginal')}
      className={cn(
        'mb-1 flex max-w-full items-start gap-1.5 rounded-xl border-l-2 px-2.5 py-1.5 text-left text-xs',
        mine ? 'border-white/60 bg-white/15 text-white/90' : 'border-brand-400 bg-gray-50 text-gray-600 dark:bg-gray-900/60 dark:text-gray-300',
      )}
    >
      <CornerUpLeft className="mt-0.5 size-3 shrink-0 opacity-70" />
      <span className="min-w-0">
        <span className="block font-semibold">{reply.sender?.nickname ?? t('chat.unknown')}</span>
        <span className={cn('line-clamp-2 break-words', reply.deleted && 'italic opacity-70')}>{preview}</span>
      </span>
    </button>
  )
}

/** 메시지 아래 반응: 👍 3 처럼 이모지·개수. 내가 단 것은 진하게, 누르면 달기·떼기 */
export function ReactionBar({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const { myId, room, messages } = useRoom()
  const reactions = message.reactions ?? []
  if (!reactions.length || message.deleted) return null
  const names = new Map(room.members.map((m) => [m.id, m.nickname]))
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {reactions.map((r) => {
        const mine = !!myId && r.userIds.includes(myId)
        const who = r.userIds.map((id) => names.get(id) ?? t('chat.unknown')).join(', ')
        return (
          <button
            key={r.emoji}
            type="button"
            title={who}
            aria-pressed={mine}
            onClick={() => messages.react(message, r.emoji)}
            className={cn(
              'flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs transition',
              mine
                ? 'border-brand-300 bg-brand-50 font-semibold text-brand-700 dark:border-brand-500/50 dark:bg-brand-500/15 dark:text-brand-200'
                : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300',
            )}
          >
            <span className="text-sm leading-none">{r.emoji}</span>
            {r.count}
          </button>
        )
      })}
    </div>
  )
}

/** 반응 버튼: 누르면 자주 쓰는 이모지 6개 + "다른 이모지" */
export function QuickReactions({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const { messages, dialogs } = useRoom()
  const [open, setOpen] = useState(false)
  return (
    <span className="relative">
      <button
        type="button"
        title={t('chat.react')}
        aria-label={t('chat.react')}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        onBlur={(e) => !e.currentTarget.parentElement?.contains(e.relatedTarget) && setOpen(false)}
        className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
      >
        <SmilePlus className="size-3.5" />
      </button>
      {open && (
        <span className="absolute bottom-full left-1/2 z-20 mb-1 flex -translate-x-1/2 gap-0.5 rounded-full border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-900">
          {QUICK_REACTIONS.map((e) => (
            <button
              key={e}
              type="button"
              onMouseDown={(ev) => ev.preventDefault()}
              onClick={() => {
                setOpen(false)
                messages.react(message, e)
              }}
              className="flex size-8 items-center justify-center rounded-full text-lg transition hover:scale-110 hover:bg-gray-100 dark:hover:bg-gray-800"
            >
              {e}
            </button>
          ))}
          <button
            type="button"
            title={t('chat.moreEmoji')}
            onMouseDown={(ev) => ev.preventDefault()}
            onClick={() => {
              setOpen(false)
              dialogs.reaction.show(message)
            }}
            className="flex size-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800"
          >
            <SmilePlus className="size-4" />
          </button>
        </span>
      )}
    </span>
  )
}

/** 이 메시지를 아직 안 읽은 방 사람 수 (카카오톡처럼 시간 옆 숫자). 0 이면 안 그린다 */
export function UnreadCount({ message }: { message: ChatMessage }) {
  const { room } = useRoom()
  // 스레드 답글은 읽음 위치를 따지지 않는다 (채널 본문만 읽음 처리)
  if (message.threadRootId) return null
  if (message.type === 'SYSTEM' || message.deleted) return null
  const reads = room.reads ?? []
  const count = reads.filter((r) => r.userId !== message.sender?.id && (r.lastReadMessageId ?? 0) < message.id).length
  if (!count) return null
  return <span className="mr-1 font-semibold text-brand-600 dark:text-brand-300">{count}</span>
}

/** 시작 글 아래 "답글 N개 · 마지막 답글" (답글 단 사람 얼굴 몇 개). 누르면 스레드 창 */
export function ThreadSummary({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { thread } = useRoom()
  const count = message.replyCount ?? 0
  if (!count) return null
  return (
    <button
      type="button"
      onClick={() => thread.open(message)}
      className="flex items-center gap-1.5 rounded-lg px-1.5 py-1 text-xs whitespace-nowrap transition-colors hover:bg-gray-100 dark:hover:bg-gray-800"
    >
      <span className="flex -space-x-1.5">
        {(message.threadUsers ?? []).slice(0, 3).map((u) => (
          <Avatar key={u.id} src={u.profileImageUrl} name={u.nickname} size="xs" className="ring-2 ring-white dark:ring-gray-950" />
        ))}
      </span>
      <span className="font-semibold text-brand-700 dark:text-brand-300">{t('chat.thread.replies', { count })}</span>
      {message.lastReplyAt && <span className="text-gray-400">{t('chat.thread.lastReply', { when: fmt.relative(message.lastReplyAt) })}</span>}
    </button>
  )
}
