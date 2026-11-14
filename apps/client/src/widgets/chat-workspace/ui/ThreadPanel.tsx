import { MessagesSquare, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { type ChatMessage, roomTitle } from '@/entities/chat'
import { MessageComposer } from '@/features/chat-room-actions'
import { IconButton, Spinner } from '@/shared/ui'
import { useRoom } from '../model/context'
import { useThread } from '../model/use-thread'
import { MessageItem } from './MessageItem'

const groupedWith = (prev: ChatMessage | undefined, m: ChatMessage) =>
  !!prev && prev.sender?.id === m.sender?.id && new Date(m.createdAt).getTime() - new Date(prev.createdAt).getTime() < 5 * 60_000

export function ThreadPanel({ rootId }: { rootId: number }) {
  const { t } = useTranslation()
  const { room, myId, thread: control } = useRoom()
  const { thread, isPending, isError, scroller } = useThread(rootId, control.replaceRoot)
  const channel = room.type === 'GROUP' ? `# ${room.name ?? ''}` : roomTitle(room, myId)

  return (
    <aside
      aria-label={t('chat.thread.title')}
      className="absolute inset-0 z-20 flex min-h-0 flex-col bg-white xl:static xl:z-auto xl:w-[400px] xl:shrink-0 xl:border-l xl:border-gray-200 2xl:w-[440px] dark:bg-gray-950 xl:dark:border-gray-800"
    >
      <div className="flex h-16 shrink-0 items-center gap-3 border-b border-gray-200 px-4 dark:border-gray-800">
        <MessagesSquare className="size-5 shrink-0 text-brand-600 dark:text-brand-300" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-gray-900 dark:text-white">{t('chat.thread.title')}</p>
          <p className="truncate text-xs text-gray-500">{channel}</p>
        </div>
        <IconButton label={t('chat.thread.close')} title={t('chat.thread.close')} onClick={control.close}>
          <X className="size-5" />
        </IconButton>
      </div>

      <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
        {isPending && (
          <div className="flex justify-center py-10">
            <Spinner className="size-6 text-gray-400" />
          </div>
        )}
        {isError && <p className="py-16 text-center text-sm text-gray-400">{t('chat.thread.notFound')}</p>}
        {thread && (
          <>
            <MessageItem item={{ message: thread.root, grouped: false, inThread: true }} />
            <div className="my-4 flex items-center gap-3 text-xs text-gray-400">
              <span>{thread.root.replyCount ? t('chat.thread.replies', { count: thread.root.replyCount }) : t('chat.thread.noReplies')}</span>
              <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
            </div>
            {thread.replies.map((m, i) => (
              <MessageItem key={m.id} item={{ message: m, grouped: groupedWith(thread.replies[i - 1], m), inThread: true }} />
            ))}
          </>
        )}
      </div>

      {thread && !thread.root.deleted && (
        <div className="shrink-0 border-t border-gray-200 p-3 dark:border-gray-800">
          <MessageComposer key={thread.root.id} target={{ room, threadRootId: thread.root.id }} />
        </div>
      )}
    </aside>
  )
}
