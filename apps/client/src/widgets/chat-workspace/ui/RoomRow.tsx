import { BellOff, Hash, Users, Volume2 } from 'lucide-react'
import { useContext } from 'react'
import { useTranslation } from 'react-i18next'
import { type ChatRoom, previewText, roomPeer, roomTitle } from '@/entities/chat'
import { PresenceDot, usePresence } from '@/entities/presence'
import { useMeQuery } from '@/entities/user'
import { useVoice } from '@/features/voice-chat'
import { cn } from '@/shared/lib/cn'
import { isSameDay, useFormat } from '@/shared/lib/format'
import { Avatar } from '@/shared/ui'
import { RoomListStyleContext, useWorkspace } from '../model/context'
import { useTypingUsers } from '../model/typing-store'

/** 1:1 방은 상대방 사진, 채널은 # 아이콘 */
export function RoomAvatar({ room }: { room: ChatRoom }) {
  const { data: me } = useMeQuery()
  const statusOf = usePresence(room.teamId)
  const peer = roomPeer(room, me?.id)
  if (peer) {
    return (
      <span className="relative shrink-0">
        <Avatar src={peer.profileImageUrl} name={peer.nickname} size="md" />
        <PresenceDot status={statusOf(peer.id)} />
      </span>
    )
  }
  return (
    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-white">
      {room.type === 'GROUP' ? <Hash className="size-5" /> : <Users className="size-5" />}
    </span>
  )
}

/** 채널 음성방에 들어와 있는 사람 (디스코드처럼 채널 아래에) */
function VoicePresence({ room }: { room: ChatRoom }) {
  const { t } = useTranslation()
  // 내가 이 채널 음성에 들어가 있으면 지금 말하는 사람에게 초록 테두리
  const voice = useVoice()
  const participants = room.voice?.participants ?? []
  if (!participants.length) return null
  const speaking = new Set(voice.roomId === room.id ? voice.peers.filter((p) => p.speaking).map((p) => p.userId) : [])
  return (
    <span className="mt-1.5 flex items-center gap-1.5 text-xs font-medium text-green-700 dark:text-green-400">
      <Volume2 className="size-3.5 shrink-0" />
      <span className="flex -space-x-1.5">
        {participants.slice(0, 4).map((p) => (
          <Avatar key={p.id} src={p.profileImageUrl} name={p.nickname} size="xs" ring className={speaking.has(p.id) ? 'ring-green-500' : 'ring-white dark:ring-gray-950'} />
        ))}
      </span>
      <span className="truncate">{t('chat.voiceNow', { count: participants.length })}</span>
    </span>
  )
}

/** 안 읽은 개수 (알림 끈 방은 회색) */
function UnreadBadge({ room }: { room: ChatRoom }) {
  if (room.unreadCount <= 0) return null
  return (
    <span
      className={cn(
        'ml-auto min-w-5 shrink-0 rounded-full px-1.5 text-center text-[11px] leading-5 font-bold text-white',
        room.muted ? 'bg-gray-400 dark:bg-gray-600' : 'bg-brand-600',
      )}
    >
      {room.unreadCount > 99 ? '99+' : room.unreadCount}
    </span>
  )
}

/** 앱 사이드바의 짧은 줄: # 이름 (1:1 은 작은 사진 + 접속 점) · 알림 끔 · 음성 중 · 안 읽은 개수 */
function CompactRoomRow({ room }: { room: ChatRoom }) {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const statusOf = usePresence(room.teamId)
  const active = room.id === ws.activeRoomId
  const peer = roomPeer(room, ws.myId)
  const typing = useTypingUsers(room.id).length > 0
  const bold = room.unreadCount > 0 && !room.muted
  return (
    <li>
      <button
        type="button"
        onClick={() => ws.openRoom(room)}
        className={cn(
          'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors',
          active
            ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
            : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white',
        )}
      >
        {peer ? (
          <span className="relative shrink-0">
            <Avatar src={peer.profileImageUrl} name={peer.nickname} size="xs" />
            <PresenceDot status={statusOf(peer.id)} />
          </span>
        ) : (
          <Hash className="size-4 shrink-0 text-gray-400" />
        )}
        <span className={cn('min-w-0 flex-1 truncate', bold && 'font-semibold text-gray-900 dark:text-white')}>{roomTitle(room, ws.myId)}</span>
        {typing && <span className="shrink-0 text-[11px] text-brand-600 dark:text-brand-300">{t('chat.typing.short')}</span>}
        {!!room.voice?.participants.length && (
          <span className="flex shrink-0 items-center gap-0.5 text-[11px] font-medium text-green-600 dark:text-green-400" title={t('chat.voiceNow', { count: room.voice.participants.length })}>
            <Volume2 className="size-3.5" />
            {room.voice.participants.length}
          </span>
        )}
        {room.muted && <BellOff aria-label={t('chat.muted')} className="size-3 shrink-0 text-gray-400" />}
        <UnreadBadge room={room} />
      </button>
    </li>
  )
}

/** 방 목록 한 줄: 이름·마지막 메시지·시간·안 읽은 개수, 누르면 그 방으로 (사이드바에서는 짧은 줄) */
export function RoomRow({ room }: { room: ChatRoom }) {
  const compact = useContext(RoomListStyleContext) === 'compact'
  return compact ? <CompactRoomRow room={room} /> : <FullRoomRow room={room} />
}

function FullRoomRow({ room }: { room: ChatRoom }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const ws = useWorkspace()
  const active = room.id === ws.activeRoomId
  const typingUsers = useTypingUsers(room.id)
  const when = room.lastMessage?.createdAt
  const today = when && isSameDay(new Date(when), new Date())
  return (
    <li>
      <button
        type="button"
        onClick={() => ws.openRoom(room)}
        className={cn(
          'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
          active ? 'bg-brand-50 dark:bg-brand-500/15' : 'hover:bg-gray-100 dark:hover:bg-gray-800/70',
        )}
      >
        <RoomAvatar room={room} />
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2">
            <span className="truncate text-sm font-semibold text-gray-900 dark:text-white">{roomTitle(room, ws.myId)}</span>
            {room.type === 'GROUP' && <span className="text-xs text-gray-400">{room.members.length}</span>}
            {room.muted && <BellOff aria-label={t('chat.muted')} className="size-3 shrink-0 text-gray-400" />}
            {when && <span className="ml-auto shrink-0 text-[11px] text-gray-400">{today ? fmt.time(when) : fmt.date(when)}</span>}
          </span>
          <span className="mt-0.5 flex items-center gap-2">
            <span className="truncate text-xs text-gray-500 dark:text-gray-400">
              {typingUsers.length ? <span className="text-brand-600 dark:text-brand-300">{t('chat.typing.short')}</span> : previewText(room.lastMessage, t, fmt.list)}
            </span>
            <UnreadBadge room={room} />
          </span>
          <VoicePresence room={room} />
        </span>
      </button>
    </li>
  )
}
