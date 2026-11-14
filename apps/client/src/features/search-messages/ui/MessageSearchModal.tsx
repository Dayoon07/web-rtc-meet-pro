import { Search } from 'lucide-react'
import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import { type ChatMessage, filesSummary, roomTitle, useChatRoomsQuery } from '@/entities/chat'
import { useMeQuery } from '@/entities/user'
import type { Dialog } from '@/shared/lib/use-dialog'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Modal, Spinner } from '@/shared/ui'
import { useMessageSearch } from '../model/use-message-search'

function Highlight({ text, word }: { text: string; word: string }) {
  if (!word) return <>{text}</>
  const lower = text.toLowerCase()
  const w = word.toLowerCase()
  const parts: { s: string; hit: boolean }[] = []
  let i = 0
  while (i < text.length) {
    const at = lower.indexOf(w, i)
    if (at < 0) {
      parts.push({ s: text.slice(i), hit: false })
      break
    }
    if (at > i) parts.push({ s: text.slice(i, at), hit: false })
    parts.push({ s: text.slice(at, at + w.length), hit: true })
    i = at + w.length
  }
  return (
    <>
      {parts.map((p, k) =>
        p.hit ? (
          <mark key={k} className="rounded bg-amber-200 px-0.5 text-gray-900 dark:bg-amber-400/40 dark:text-white">
            {p.s}
          </mark>
        ) : (
          <Fragment key={k}>{p.s}</Fragment>
        ),
      )}
    </>
  )
}

export function MessageSearchModal({ teamId, dialog }: { teamId: number; dialog: Dialog<undefined, ChatMessage> }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { data: me } = useMeQuery()
  const { data: rooms = [] } = useChatRoomsQuery(teamId)
  const search = useMessageSearch(teamId, dialog.open)
  const roomName = (roomId: number) => {
    const room = rooms.find((r) => r.id === roomId)
    return room ? (room.type === 'GROUP' ? `# ${roomTitle(room, me?.id)}` : roomTitle(room, me?.id)) : ''
  }

  return (
    <Modal open={dialog.open} onClose={dialog.close} title={t('chat.search.title')} size="lg">
      <div className="flex flex-col gap-3">
        <label className="flex items-center gap-2 rounded-xl border border-gray-300 px-3 focus-within:border-brand-500 focus-within:ring-3 focus-within:ring-brand-500/15 dark:border-gray-700">
          <Search className="size-4 text-gray-400" />
          <input
            autoFocus
            value={search.query}
            maxLength={100}
            onChange={(e) => search.setQuery(e.target.value)}
            placeholder={t('chat.search.placeholder')}
            className="h-11 flex-1 bg-transparent text-[15px] text-gray-900 outline-none placeholder:text-gray-400 dark:text-gray-100"
          />
          {search.loading && <Spinner className="size-4 text-gray-400" />}
        </label>
        <div className="max-h-[60dvh] min-h-40 overflow-y-auto">
          {!search.searched ? (
            <p className="py-10 text-center text-sm text-gray-400">{t('chat.search.hint')}</p>
          ) : !search.results.length && !search.loading ? (
            <p className="py-10 text-center text-sm text-gray-400">{t('chat.search.noResults', { query: search.searched })}</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {search.results.map((m) => (
                <li key={m.id}>
                  <button
                    type="button"
                    onClick={() => dialog.done(m)}
                    className="flex w-full gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-gray-50 dark:hover:bg-gray-800/70"
                  >
                    <Avatar src={m.sender?.profileImageUrl} name={m.sender?.nickname ?? '?'} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2 text-xs text-gray-500">
                        <span className="font-semibold text-gray-900 dark:text-white">{m.sender?.nickname ?? t('chat.unknown')}</span>
                        <span className="truncate">{roomName(m.roomId)}</span>
                        <span className="ml-auto shrink-0">{fmt.dateTime(m.createdAt)}</span>
                      </span>
                      <span className="mt-0.5 line-clamp-2 text-sm break-words text-gray-700 dark:text-gray-300">
                        <Highlight
                          text={[m.content, m.files.map((f) => f.name).join(', ')].filter(Boolean).join(' · ') || filesSummary(m.files, t)}
                          word={search.searched}
                        />
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  )
}
