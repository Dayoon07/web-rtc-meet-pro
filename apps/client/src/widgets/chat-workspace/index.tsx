import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, MessageSquarePlus, MessagesSquare, Plus, UserPlus, Users } from 'lucide-react'
import { Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  chatApi,
  chatKeys,
  type ChatEvent,
  type ChatMessage,
  type ChatRoom,
  previewText,
  roomPeer,
  roomTitle,
  systemText,
  useChatRoomsQuery,
} from '@/entities/chat'
import { useTeamQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { CreateGroupRoomModal, InviteToRoomModal, LeaveRoomButton, MessageComposer, StartDirectChatModal } from '@/features/chat-room-actions'
import { realtime } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { isSameDay, useFormat } from '@/shared/lib/format'
import { Avatar, Button, EmptyState, IconButton, LinkButton, Select, Spinner } from '@/shared/ui'

const PAGE = 50

function RoomAvatar({ room, myId }: { room: ChatRoom; myId?: number }) {
  const peer = roomPeer(room, myId)
  if (peer) return <Avatar src={peer.profileImageUrl} name={peer.nickname} size="md" />
  return (
    <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-400 to-brand-600 text-white">
      <Users className="size-5" />
    </span>
  )
}

function RoomList({
  rooms,
  activeId,
  myId,
  onSelect,
}: {
  rooms: ChatRoom[]
  activeId?: number
  myId?: number
  onSelect: (room: ChatRoom) => void
}) {
  const { t } = useTranslation()
  const fmt = useFormat()
  if (!rooms.length) return <p className="px-4 py-10 text-center text-sm text-gray-400">{t('chat.noRooms')}</p>
  return (
    <ul className="flex flex-col gap-0.5 p-2">
      {rooms.map((room) => {
        const when = room.lastMessage?.createdAt
        const today = when && isSameDay(new Date(when), new Date())
        return (
          <li key={room.id}>
            <button
              type="button"
              onClick={() => onSelect(room)}
              className={cn(
                'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
                room.id === activeId ? 'bg-brand-50 dark:bg-brand-500/15' : 'hover:bg-gray-100 dark:hover:bg-gray-800/70',
              )}
            >
              <RoomAvatar room={room} myId={myId} />
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-2">
                  <span className="truncate text-sm font-semibold text-gray-900 dark:text-white">{roomTitle(room, myId)}</span>
                  {room.type === 'GROUP' && <span className="text-xs text-gray-400">{room.members.length}</span>}
                  {when && <span className="ml-auto shrink-0 text-[11px] text-gray-400">{today ? fmt.time(when) : fmt.date(when)}</span>}
                </span>
                <span className="mt-0.5 flex items-center gap-2">
                  <span className="truncate text-xs text-gray-500 dark:text-gray-400">{previewText(room.lastMessage, t, fmt.list)}</span>
                  {room.unreadCount > 0 && (
                    <span className="ml-auto min-w-5 shrink-0 rounded-full bg-brand-600 px-1.5 text-center text-[11px] leading-5 font-bold text-white">
                      {room.unreadCount > 99 ? '99+' : room.unreadCount}
                    </span>
                  )}
                </span>
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function MessageList({ messages, myId, hasMore, onLoadOlder, loadingOlder }: {
  messages: ChatMessage[]
  myId?: number
  hasMore: boolean
  onLoadOlder: () => void
  loadingOlder: boolean
}) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const scroller = useRef<HTMLDivElement>(null)
  const lastId = messages.at(-1)?.id
  const stickToBottom = useRef(true)

  // 새 메시지가 오면 맨 아래에 있을 때만 따라 내려간다 (위로 올려 읽는 중이면 그대로)
  useLayoutEffect(() => {
    const el = scroller.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [lastId])

  return (
    <div
      ref={scroller}
      onScroll={(e) => {
        const el = e.currentTarget
        stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
      }}
      className="flex-1 overflow-y-auto px-4 py-4 sm:px-6"
    >
      {hasMore && (
        <div className="mb-4 flex justify-center">
          <Button variant="ghost" size="sm" loading={loadingOlder} onClick={onLoadOlder}>
            {t('chat.loadOlder')}
          </Button>
        </div>
      )}
      {!messages.length && <p className="py-16 text-center text-sm text-gray-400">{t('chat.emptyRoom')}</p>}
      {messages.map((m, i) => {
        const prev = messages[i - 1]
        const at = new Date(m.createdAt)
        const newDay = !prev || !isSameDay(new Date(prev.createdAt), at)
        const grouped =
          !newDay && prev?.type === 'TEXT' && m.type === 'TEXT' && prev.sender?.id === m.sender?.id && at.getTime() - new Date(prev.createdAt).getTime() < 5 * 60_000
        const mine = m.sender?.id === myId

        return (
          <Fragment key={m.id}>
            {newDay && (
              <div className="my-4 flex items-center gap-3 text-xs text-gray-400">
                <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
                {isSameDay(at, new Date()) ? t('chat.today') : fmt.dayHeading(at)}
                <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
              </div>
            )}
            {m.type === 'SYSTEM' ? (
              <p className="my-3 text-center text-xs text-gray-500 dark:text-gray-400">
                <span className="rounded-full bg-gray-100 px-3 py-1 dark:bg-gray-800">{systemText(m, t, fmt.list)}</span>
              </p>
            ) : (
              <div className={cn('flex items-end gap-2', mine && 'flex-row-reverse', grouped ? 'mt-1' : 'mt-4')}>
                {!mine && (grouped ? <span className="w-8 shrink-0" /> : <Avatar src={m.sender?.profileImageUrl} name={m.sender?.nickname ?? '?'} size="sm" />)}
                <div className={cn('flex max-w-[75%] flex-col', mine ? 'items-end' : 'items-start')}>
                  {!mine && !grouped && <span className="mb-1 ml-1 text-xs font-medium text-gray-600 dark:text-gray-400">{m.sender?.nickname ?? t('chat.unknown')}</span>}
                  <div className={cn('flex items-end gap-1.5', mine && 'flex-row-reverse')}>
                    <p
                      className={cn(
                        'rounded-2xl px-3.5 py-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
                        mine
                          ? 'rounded-br-md bg-brand-600 text-white dark:bg-brand-500'
                          : 'rounded-bl-md bg-white text-gray-900 shadow-xs ring-1 ring-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-700',
                      )}
                    >
                      {m.content}
                    </p>
                    <span className="shrink-0 pb-0.5 text-[10px] text-gray-400">{fmt.time(m.createdAt)}</span>
                  </div>
                </div>
              </div>
            )}
          </Fragment>
        )
      })}
    </div>
  )
}

function RoomView({ room, onBack, onLeft }: { room: ChatRoom; onBack: () => void; onLeft: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: team } = useTeamQuery(room.teamId)
  const [inviting, setInviting] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)

  const { data: messages = [], isPending } = useQuery({
    queryKey: chatKeys.messages(room.id),
    queryFn: async () => {
      const page = await chatApi.messages(room.id)
      setHasMore(page.length === PAGE)
      return page
    },
  })

  // 마지막 메시지까지 읽음 처리 → 방 목록 배지·사이드바 배지 갱신
  const lastId = messages.at(-1)?.id
  useEffect(() => {
    if (!lastId) return
    void chatApi.read(room.id, lastId).then(() => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
      queryClient.setQueryData<ChatRoom[]>(chatKeys.rooms(room.teamId), (rooms) =>
        rooms?.map((r) => (r.id === room.id ? { ...r, unreadCount: 0 } : r)),
      )
    })
  }, [lastId, room.id, room.teamId, queryClient])

  const loadOlder = async () => {
    const first = messages[0]
    if (!first) return
    setLoadingOlder(true)
    try {
      const older = await chatApi.messages(room.id, first.id)
      setHasMore(older.length === PAGE)
      queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), (cur = []) => [...older, ...cur])
    } finally {
      setLoadingOlder(false)
    }
  }

  const appendMine = (m: ChatMessage) =>
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), (cur = []) => (cur.some((x) => x.id === m.id) ? cur : [...cur, m]))

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex h-16 shrink-0 items-center gap-3 border-b border-gray-200 px-4 dark:border-gray-800">
        <IconButton label={t('common.back')} className="md:hidden" onClick={onBack}>
          <ArrowLeft className="size-5" />
        </IconButton>
        <RoomAvatar room={room} myId={me?.id} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-gray-900 dark:text-white">{roomTitle(room, me?.id)}</p>
          <p className="truncate text-xs text-gray-500">
            {room.type === 'GROUP' ? t('chat.participants', { count: room.members.length }) : t('chat.direct')}
          </p>
        </div>
        {room.type === 'GROUP' && (
          <>
            <Button variant="outline" size="sm" icon={<UserPlus className="size-4" />} onClick={() => setInviting(true)}>
              <span className="hidden lg:inline">{t('chat.inviteMembers')}</span>
            </Button>
            <LeaveRoomButton room={room} onLeft={onLeft} />
          </>
        )}
      </div>

      {isPending ? (
        <div className="flex flex-1 items-center justify-center">
          <Spinner className="size-6 text-gray-400" />
        </div>
      ) : (
        <MessageList messages={messages} myId={me?.id} hasMore={hasMore} onLoadOlder={loadOlder} loadingOlder={loadingOlder} />
      )}

      <div className="shrink-0 border-t border-gray-200 p-3 sm:p-4 dark:border-gray-800">
        <MessageComposer roomId={room.id} onSent={appendMine} />
      </div>

      {team && <InviteToRoomModal room={room} teamMembers={team.members} open={inviting} onClose={() => setInviting(false)} />}
    </div>
  )
}

/**
 * 채팅 화면: 왼쪽 방 목록 (팀 선택 + 1:1/그룹 만들기), 오른쪽 대화.
 * 실시간 메시지는 /user/queue/chat 으로 받는다.
 */
export function ChatWorkspace({
  teamId,
  roomId,
  onNavigate,
}: {
  teamId?: number
  roomId?: number
  onNavigate: (teamId: number, roomId?: number) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: teams, isPending: teamsPending } = useTeamsQuery()
  const activeTeamId = teamId ?? teams?.[0]?.id ?? 0
  const { data: team } = useTeamQuery(activeTeamId)
  const { data: rooms = [], isPending: roomsPending } = useChatRoomsQuery(activeTeamId)
  const [modal, setModal] = useState<'direct' | 'group' | null>(null)

  const activeRoom = rooms.find((r) => r.id === roomId)
  const isOwner = team?.team.ownerId === me?.id
  const groupCount = rooms.filter((r) => r.type === 'GROUP').length

  const onEvent = useCallback(
    (payload: unknown) => {
      const ev = payload as ChatEvent
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(ev.teamId) })
      if (ev.message) {
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) =>
          cur && !cur.some((m) => m.id === ev.message!.id) ? [...cur, ev.message!] : cur,
        )
      }
    },
    [queryClient],
  )
  useEffect(() => realtime.on('chat', onEvent), [onEvent])

  if (teamsPending) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Spinner className="size-6 text-gray-400" />
      </div>
    )
  }

  if (!teams?.length) {
    return (
      <div className="mx-auto w-full max-w-lg p-6">
        <EmptyState
          icon={<MessagesSquare />}
          title={t('chat.noTeams')}
          description={t('chat.noTeamsHint')}
          action={<LinkButton to="/app/teams">{t('chat.goTeams')}</LinkButton>}
        />
      </div>
    )
  }

  return (
    <div className="flex min-h-0 flex-1 overflow-hidden border-gray-200 bg-white md:rounded-2xl md:border dark:border-gray-800 dark:bg-gray-900">
      <aside className={cn('flex w-full flex-col border-gray-200 md:w-80 md:border-r dark:border-gray-800', activeRoom && 'hidden md:flex')}>
        <div className="flex flex-col gap-3 border-b border-gray-200 p-3 dark:border-gray-800">
          {teams.length > 1 && (
            <Select aria-label={t('chat.team')} value={activeTeamId} onChange={(e) => onNavigate(Number(e.target.value))}>
              {teams.map((tm) => (
                <option key={tm.id} value={tm.id}>
                  {tm.name}
                </option>
              ))}
            </Select>
          )}
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" className="flex-1" icon={<MessageSquarePlus className="size-4" />} onClick={() => setModal('direct')}>
              {t('chat.newDirect')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="flex-1"
              icon={<Plus className="size-4" />}
              disabled={!isOwner}
              title={isOwner ? undefined : t('chat.ownerOnlyGroup')}
              onClick={() => setModal('group')}
            >
              {t('chat.group')}
            </Button>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {roomsPending ? (
            <div className="flex justify-center py-10">
              <Spinner className="size-5 text-gray-400" />
            </div>
          ) : (
            <RoomList rooms={rooms} activeId={roomId} myId={me?.id} onSelect={(r) => onNavigate(r.teamId, r.id)} />
          )}
        </div>
      </aside>

      <section className={cn('flex min-w-0 flex-1 flex-col', !activeRoom && 'hidden md:flex')}>
        {activeRoom ? (
          <RoomView key={activeRoom.id} room={activeRoom} onBack={() => onNavigate(activeTeamId)} onLeft={() => onNavigate(activeTeamId)} />
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
            <span className="flex size-14 items-center justify-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
              <MessagesSquare className="size-7" />
            </span>
            <p className="font-semibold text-gray-900 dark:text-white">{t('chat.selectRoom')}</p>
            <p className="max-w-xs text-sm text-gray-500 dark:text-gray-400">{t('chat.selectRoomHint')}</p>
          </div>
        )}
      </section>

      {team && me && (
        <>
          <StartDirectChatModal
            teamId={activeTeamId}
            members={team.members}
            myId={me.id}
            open={modal === 'direct'}
            onClose={() => setModal(null)}
            onOpened={(room) => onNavigate(room.teamId, room.id)}
          />
          <CreateGroupRoomModal
            teamId={activeTeamId}
            members={team.members}
            myId={me.id}
            usage={{ used: groupCount, max: team.limits.maxGroupRooms }}
            open={modal === 'group'}
            onClose={() => setModal(null)}
            onCreated={(room) => onNavigate(room.teamId, room.id)}
          />
        </>
      )}
    </div>
  )
}
