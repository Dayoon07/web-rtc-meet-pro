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

export type MessageListItem = {
  message: ChatMessage
  grouped: boolean
  inThread?: boolean
}

const actionClass = 'rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'

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

export function MessageItem({ item }: { item: MessageListItem }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { myId, messages } = useRoom()
  const { message: m, grouped } = item
  const highlighted = messages.highlightId === m.id
  const mine = m.sender?.id === myId
  const gap = grouped ? 'mt-1' : 'mt-4'

  if (messages.editingId === m.id) {
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
