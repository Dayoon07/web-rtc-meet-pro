import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatRoom, ChatUser } from '@/entities/chat'

export type MentionOption = { kind: 'user'; user: ChatUser } | { kind: 'all'; label: string }

const TOKEN = /(^|\s)@([^\s@]{0,20})$/

export function useMentionPicker(room: ChatRoom, myId: number | undefined) {
  const { t } = useTranslation()
  const allLabel = t('chat.mention.all')
  const [query, setQuery] = useState<string | null>(null)
  const [active, setActive] = useState(0)
  const [picked, setPicked] = useState<Map<number, string>>(new Map())

  const others = room.members.filter((m) => m.id !== myId)
  const q = query?.toLowerCase() ?? ''
  const options: MentionOption[] =
    query == null
      ? []
      : [
          ...(room.type === 'GROUP' && allLabel.toLowerCase().startsWith(q) ? [{ kind: 'all' as const, label: allLabel }] : []),
          ...others.filter((m) => m.nickname.toLowerCase().includes(q)).slice(0, 8).map((user) => ({ kind: 'user' as const, user })),
        ]

  return {
    open: options.length > 0,
    options,
    active: Math.min(active, Math.max(0, options.length - 1)),
    track: (text: string, caret: number) => {
      const m = TOKEN.exec(text.slice(0, caret))
      setQuery(m ? m[2] : null)
      setActive(0)
    },
    close: () => setQuery(null),
    move: (dir: 1 | -1) => setActive((a) => (options.length ? (a + dir + options.length) % options.length : 0)),
    apply: (option: MentionOption, text: string, caret: number) => {
      const before = text.slice(0, caret).replace(/@([^\s@]{0,20})$/, '')
      const name = option.kind === 'all' ? option.label : option.user.nickname
      if (option.kind === 'user') setPicked((p) => new Map(p).set(option.user.id, name))
      const next = `${before}@${name} ${text.slice(caret)}`
      setQuery(null)
      return { text: next, caret: before.length + name.length + 2 }
    },
    collect: (text: string) => ({
      mentionIds: [...picked].filter(([, name]) => text.includes(`@${name}`)).map(([id]) => id),
      mentionAll: room.type === 'GROUP' && text.includes(`@${allLabel}`),
    }),
    reset: () => setPicked(new Map()),
  }
}

export type MentionPicker = ReturnType<typeof useMentionPicker>
