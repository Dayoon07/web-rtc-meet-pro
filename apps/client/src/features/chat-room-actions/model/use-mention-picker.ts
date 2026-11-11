import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatRoom, ChatUser } from '@/entities/chat'

/** 고를 수 있는 멘션: 방 사람 한 명, 또는 @모두 */
export type MentionOption = { kind: 'user'; user: ChatUser } | { kind: 'all'; label: string }

/** 커서 바로 앞이 "@글자" 이면 그 글자 (멘션 고르는 중) */
const TOKEN = /(^|\s)@([^\s@]{0,20})$/

/**
 * 입력창의 @멘션: "@" 를 치면 방 사람 목록이 뜨고, 고르면 "@닉네임 " 으로 바뀐다.
 * 보낼 때 글에 아직 남아 있는 멘션만 서버에 보낸다 (지우면 멘션도 빠진다)
 */
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
    /** 글이나 커서가 바뀔 때: 커서 앞이 "@..." 이면 목록을 연다 */
    track: (text: string, caret: number) => {
      const m = TOKEN.exec(text.slice(0, caret))
      setQuery(m ? m[2] : null)
      setActive(0)
    },
    close: () => setQuery(null),
    move: (dir: 1 | -1) => setActive((a) => (options.length ? (a + dir + options.length) % options.length : 0)),
    /** 고른 멘션으로 "@글자" 를 바꾼 새 글과 커서 위치 */
    apply: (option: MentionOption, text: string, caret: number) => {
      const before = text.slice(0, caret).replace(/@([^\s@]{0,20})$/, '')
      const name = option.kind === 'all' ? option.label : option.user.nickname
      if (option.kind === 'user') setPicked((p) => new Map(p).set(option.user.id, name))
      const next = `${before}@${name} ${text.slice(caret)}`
      setQuery(null)
      return { text: next, caret: before.length + name.length + 2 }
    },
    /** 보낼 때: 글에 남아 있는 멘션 */
    collect: (text: string) => ({
      mentionIds: [...picked].filter(([, name]) => text.includes(`@${name}`)).map(([id]) => id),
      mentionAll: room.type === 'GROUP' && text.includes(`@${allLabel}`),
    }),
    reset: () => setPicked(new Map()),
  }
}

export type MentionPicker = ReturnType<typeof useMentionPicker>
