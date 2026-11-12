import { CornerUpLeft, Megaphone, MessagesSquare, Pencil, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { type ChatMessage, meetingNotesCard, systemText } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { startReply } from '@/features/chat-room-actions'
import { Avatar } from '@/shared/ui'
import { bubbleClass } from '../lib/sections'
import { useRoom } from '../model/context'
import { QuickReactions, ReactionBar, ReplyQuote, ThreadSummary, UnreadCount } from './MessageExtras'
import { EditBubble, MessageFiles, NotesCard } from './MessageParts'
import { MessageText } from './MessageText'

/** 목록 안의 메시지 한 개와 그 앞 메시지와의 관계 */
export type MessageListItem = {
  message: ChatMessage
  /** 같은 사람이 5분 안에 이어서 보냄 → 이름·사진을 다시 안 그린다 */
  grouped: boolean
  /** 스레드 창 안에 그림 (답장·공지·스레드 버튼과 "답글 N개"를 빼고) */
  inThread?: boolean
}

const actionClass = 'rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'

/**
 * 마우스를 올리면 나오는 버튼: 반응 · 스레드 · 답장 · 공지로 올리기 · 고치기 · 지우기 (스레드 창 안에서는 반응·고치기·지우기만).
 * 넓은 화면: 말풍선 위에 떠 있다가 마우스를 올리면 보인다 (자리를 차지하지 않음). 좁은 화면: 말풍선 아래에 늘 보인다
 */
function MessageActions({ item }: { item: MessageListItem }) {
  const { t } = useTranslation()
  const { myId, canModerate, canNotice, makeNotice, messages, thread } = useRoom()
  const { message, inThread } = item
  const mine = message.sender?.id === myId
  const noticeable = !inThread && canNotice && message.type === 'TEXT' && !!message.content
  if (message.deleted || messages.editingId === message.id) return null
  return (
    <span
      className={cn(
        'flex shrink-0 gap-0.5 sm:absolute sm:-top-4 sm:z-10 sm:rounded-lg sm:border sm:border-gray-200 sm:bg-white sm:p-0.5 sm:opacity-0 sm:shadow-sm sm:transition-opacity sm:group-focus-within:opacity-100 sm:group-hover:opacity-100 dark:sm:border-gray-700 dark:sm:bg-gray-900',
        mine ? 'sm:right-0' : 'sm:left-0',
      )}
    >
      <QuickReactions message={message} />
      {!inThread && (
        <>
          <button type="button" title={t('chat.thread.open')} aria-label={t('chat.thread.open')} onClick={() => thread.open(message)} className={cn(actionClass, 'hover:text-brand-700 dark:hover:text-brand-300')}>
            <MessagesSquare className="size-3.5" />
          </button>
          <button type="button" title={t('chat.reply')} aria-label={t('chat.reply')} onClick={() => startReply(message)} className={cn(actionClass, 'hover:text-gray-700 dark:hover:text-gray-200')}>
            <CornerUpLeft className="size-3.5" />
          </button>
        </>
      )}
      {noticeable && (
        <button type="button" title={t('chat.notice.fromMessage')} aria-label={t('chat.notice.fromMessage')} onClick={() => makeNotice(message)} className={cn(actionClass, 'hover:text-brand-700 dark:hover:text-brand-300')}>
          <Megaphone className="size-3.5" />
        </button>
      )}
      {mine && message.type === 'TEXT' && (
        <button type="button" title={t('chat.edit')} aria-label={t('chat.edit')} onClick={() => messages.startEdit(message)} className={cn(actionClass, 'hover:text-gray-700 dark:hover:text-gray-200')}>
          <Pencil className="size-3.5" />
        </button>
      )}
      {(mine || canModerate) && (
        <button type="button" title={t('chat.delete')} aria-label={t('chat.delete')} onClick={() => messages.remove(message)} className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10">
          <Trash2 className="size-3.5" />
        </button>
      )}
    </span>
  )
}

/** 메시지 한 개: 고치는 중 / AI 회의록 카드 / 안내 문장 / 말풍선 */
export function MessageItem({ item }: { item: MessageListItem }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { myId, messages } = useRoom()
  const { message: m, grouped } = item
  const highlighted = messages.highlightId === m.id
  const mine = m.sender?.id === myId
  const gap = grouped ? 'mt-1' : 'mt-4'

  if (messages.editingId === m.id) {
    // 고치는 중: 말풍선 대신 방 너비에 맞춘 입력칸 (좁은 화면에서도 잘리지 않게)
    return (
      <div className={cn('flex justify-end', gap)}>
        <EditBubble message={m} />
      </div>
    )
  }
  const card = meetingNotesCard(m)
  if (card) return <NotesCard card={card} />
  if (m.type === 'SYSTEM') {
    return (
      <p className="my-3 text-center text-xs text-gray-500 dark:text-gray-400">
        <span className="rounded-full bg-gray-100 px-3 py-1 dark:bg-gray-800">{systemText(m, t, fmt.list)}</span>
      </p>
    )
  }

  return (
    <div
      data-message-id={m.id}
      className={cn('flex items-end gap-2 rounded-2xl transition-colors duration-700', mine && 'flex-row-reverse', gap, highlighted && 'bg-amber-100/70 dark:bg-amber-400/15')}
    >
      {!mine && (grouped ? <span className="w-8 shrink-0" /> : <Avatar src={m.sender?.profileImageUrl} name={m.sender?.nickname ?? '?'} size="sm" />)}
      <div className={cn('flex max-w-[75%] flex-col', mine ? 'items-end' : 'items-start')}>
        {!mine && !grouped && <span className="mb-1 ml-1 text-xs font-medium text-gray-600 dark:text-gray-400">{m.sender?.nickname ?? t('chat.unknown')}</span>}
        <div className={cn('group flex items-end gap-1.5', mine && 'flex-row-reverse')}>
          <div className={cn('relative flex min-w-0 flex-col gap-1.5', mine ? 'items-end' : 'items-start')}>
            {m.deleted ? (
              <p className="rounded-2xl border border-dashed border-gray-300 px-3.5 py-2 text-sm text-gray-400 italic dark:border-gray-700">{t('chat.deletedMessage')}</p>
            ) : (
              <>
                {m.files.length > 0 && <MessageFiles message={m} />}
                {m.content && (
                  <div className={bubbleClass(mine)}>
                    <ReplyQuote message={m} />
                    <MessageText message={m} />
                  </div>
                )}
                {!m.content && m.replyTo && <ReplyQuote message={m} />}
                <ReactionBar message={m} />
                <MessageActions item={item} />
              </>
            )}
            {!item.inThread && <ThreadSummary message={m} />}
          </div>
          <span className="shrink-0 pb-0.5 text-[10px] text-gray-400">
            <UnreadCount message={m} />
            {m.edited && !m.deleted && <span className="mr-1">{t('chat.edited')}</span>}
            {fmt.time(m.createdAt)}
          </span>
        </div>
      </div>
    </div>
  )
}
