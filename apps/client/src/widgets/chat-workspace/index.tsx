import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ChevronDown, Download, FileText, FolderPlus, Hash, MessageSquarePlus, MessagesSquare, Pencil, Plus, Settings2, Trash2, UserPlus, Users } from 'lucide-react'
import { Fragment, type ReactNode, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  chatApi,
  chatKeys,
  type ChatCategory,
  type ChatEvent,
  type ChatFile,
  type ChatMessage,
  type ChatRoom,
  previewText,
  roomPeer,
  roomTitle,
  systemText,
  useChatCategoriesQuery,
  useChatRoomsQuery,
} from '@/entities/chat'
import { hasTeamPermission, useTeamQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import {
  CategoryModal,
  ChannelSettingsModal,
  CreateGroupRoomModal,
  InviteToRoomModal,
  LeaveRoomButton,
  MessageComposer,
  StartDirectChatModal,
} from '@/features/chat-room-actions'
import { realtime, toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { isSameDay, useFormat } from '@/shared/lib/format'
import { Avatar, Button, EmptyState, IconButton, LinkButton, Select, Spinner } from '@/shared/ui'

const PAGE = 50

function RoomAvatar({ room, myId }: { room: ChatRoom; myId?: number }) {
  const peer = roomPeer(room, myId)
  if (peer) return <Avatar src={peer.profileImageUrl} name={peer.nickname} size="md" />
  return (
    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-white">
      {room.type === 'GROUP' ? <Hash className="size-5" /> : <Users className="size-5" />}
    </span>
  )
}

function RoomRow({ room, active, myId, onSelect }: { room: ChatRoom; active: boolean; myId?: number; onSelect: (room: ChatRoom) => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const when = room.lastMessage?.createdAt
  const today = when && isSameDay(new Date(when), new Date())
  return (
    <li>
      <button
        type="button"
        onClick={() => onSelect(room)}
        className={cn(
          'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
          active ? 'bg-brand-50 dark:bg-brand-500/15' : 'hover:bg-gray-100 dark:hover:bg-gray-800/70',
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
}

/** 사이드바 묶음 제목 (카테고리 / 채널 / 1:1). 접기, 관리자는 + · 고치기 · 지우기 */
function SectionHeader({
  title,
  collapsed,
  onToggle,
  unread,
  actions,
}: {
  title: string
  collapsed: boolean
  onToggle: () => void
  unread: number
  actions?: ReactNode
}) {
  return (
    <div className="group flex items-center gap-1 px-2 pt-3 pb-1">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={!collapsed}
        className="flex min-w-0 flex-1 items-center gap-1 rounded-md px-1 py-0.5 text-left text-xs font-semibold tracking-wide text-gray-500 uppercase hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
      >
        <ChevronDown className={cn('size-3.5 shrink-0 transition-transform', collapsed && '-rotate-90')} />
        <span className="truncate">{title}</span>
        {collapsed && unread > 0 && <span className="ml-1 size-2 shrink-0 rounded-full bg-brand-600" />}
      </button>
      {actions && <span className="flex shrink-0 gap-0.5 opacity-100 sm:opacity-0 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100">{actions}</span>}
    </div>
  )
}

const sectionIcon = 'rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200'

/**
 * 워크스페이스 사이드바 (디스코드처럼): 카테고리(부서) → 채널, 카테고리 없는 채널, 1:1 대화.
 * 카테고리·채널은 워크스페이스 OWNER·ADMIN 이 만들고 고친다.
 */
function RoomSidebar({
  rooms,
  categories,
  activeId,
  myId,
  canManage,
  onSelect,
  onAddChannel,
  onEditCategory,
  onDeleteCategory,
}: {
  rooms: ChatRoom[]
  categories: ChatCategory[]
  activeId?: number
  myId?: number
  canManage: boolean
  onSelect: (room: ChatRoom) => void
  onAddChannel: (categoryId: number | null) => void
  onEditCategory: (category: ChatCategory) => void
  onDeleteCategory: (category: ChatCategory) => void
}) {
  const { t } = useTranslation()
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const toggle = (key: string) => setCollapsed((cur) => {
    const next = new Set(cur)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    return next
  })
  if (!rooms.length && !categories.length) return <p className="px-4 py-10 text-center text-sm text-gray-400">{t('chat.noRooms')}</p>

  const channels = rooms.filter((r) => r.type === 'GROUP')
  const known = new Set(categories.map((c) => c.id))
  const loose = channels.filter((r) => r.categoryId == null || !known.has(r.categoryId))
  const directs = rooms.filter((r) => r.type === 'DIRECT')
  const unreadOf = (list: ChatRoom[]) => list.reduce((n, r) => n + r.unreadCount, 0)

  const section = (key: string, title: string, list: ChatRoom[], actions?: ReactNode, emptyHint?: string) => (
    <div key={key}>
      <SectionHeader title={title} collapsed={collapsed.has(key)} onToggle={() => toggle(key)} unread={unreadOf(list)} actions={actions} />
      {!collapsed.has(key) &&
        (list.length ? (
          <ul className="flex flex-col gap-0.5 px-2">
            {list.map((room) => (
              <RoomRow key={room.id} room={room} active={room.id === activeId} myId={myId} onSelect={onSelect} />
            ))}
          </ul>
        ) : (
          emptyHint && <p className="px-5 py-1.5 text-xs text-gray-400">{emptyHint}</p>
        ))}
    </div>
  )
  const addButton = (categoryId: number | null) =>
    canManage && (
      <button type="button" className={sectionIcon} aria-label={t('chat.addChannel')} title={t('chat.addChannel')} onClick={() => onAddChannel(categoryId)}>
        <Plus className="size-3.5" />
      </button>
    )

  return (
    <div className="pb-3">
      {categories.map((c) =>
        section(
          `c${c.id}`,
          c.name,
          channels.filter((r) => r.categoryId === c.id),
          canManage && (
            <>
              {addButton(c.id)}
              <button type="button" className={sectionIcon} aria-label={t('chat.renameCategory')} title={t('chat.renameCategory')} onClick={() => onEditCategory(c)}>
                <Pencil className="size-3.5" />
              </button>
              <button type="button" className={sectionIcon} aria-label={t('chat.deleteCategory')} title={t('chat.deleteCategory')} onClick={() => onDeleteCategory(c)}>
                <Trash2 className="size-3.5" />
              </button>
            </>
          ),
          t('chat.emptyCategory'),
        ),
      )}
      {(loose.length > 0 || canManage) && section('loose', t('chat.channels'), loose, addButton(null), canManage ? t('chat.noChannels') : undefined)}
      {section('direct', t('chat.directs'), directs, undefined, t('chat.noDirects'))}
    </div>
  )
}

const bubbleClass = (mine: boolean) =>
  cn(
    'rounded-2xl px-3.5 py-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
    mine
      ? 'rounded-br-md bg-brand-600 text-white dark:bg-brand-500'
      : 'rounded-bl-md bg-white text-gray-900 shadow-xs ring-1 ring-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-700',
  )

/**
 * 첨부파일: 이미지는 미리보기(누르면 새 탭에서 원본), 나머지는 이름·크기 + 내려받기.
 * 주소는 서버가 서명해 준 것이라 <img src> 로 바로 쓴다.
 */
function MessageFiles({ files, mine, onMediaLoad }: { files: ChatFile[]; mine: boolean; onMediaLoad: () => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const images = files.filter((f) => f.image)
  const others = files.filter((f) => !f.image)
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
                onLoad={onMediaLoad}
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
function EditBubble({ message, onSave, onCancel }: { message: ChatMessage; onSave: (content: string) => Promise<void>; onCancel: () => void }) {
  const { t } = useTranslation()
  const [value, setValue] = useState(message.content ?? '')
  const [saving, setSaving] = useState(false)
  const save = async () => {
    const content = value.trim()
    if (!content || content === message.content) return onCancel()
    setSaving(true)
    try {
      await onSave(content)
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
          if (e.key === 'Escape') onCancel()
          if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault()
            void save()
          }
        }}
        className="w-full resize-none rounded-2xl border border-brand-300 bg-white px-3.5 py-2 text-[15px] leading-relaxed text-gray-900 outline-none focus:ring-3 focus:ring-brand-500/15 dark:border-brand-700 dark:bg-gray-900 dark:text-gray-100"
      />
      <div className="flex justify-end gap-1.5">
        <Button size="sm" variant="ghost" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button size="sm" loading={saving} onClick={() => void save()}>
          {t('common.save')}
        </Button>
      </div>
    </div>
  )
}

function MessageList({ messages, myId, hasMore, onLoadOlder, loadingOlder, canModerate, onEdit, onDelete }: {
  messages: ChatMessage[]
  myId?: number
  hasMore: boolean
  onLoadOlder: () => void
  loadingOlder: boolean
  /** 팀 OWNER·ADMIN: 다른 사람 메시지도 지울 수 있다 */
  canModerate: boolean
  onEdit: (message: ChatMessage, content: string) => Promise<void>
  onDelete: (message: ChatMessage) => void
}) {
  const [editingId, setEditingId] = useState<number | null>(null)
  const { t } = useTranslation()
  const fmt = useFormat()
  const scroller = useRef<HTMLDivElement>(null)
  const lastId = messages.at(-1)?.id
  const stickToBottom = useRef(true)

  // 새 메시지가 오면 맨 아래에 있을 때만 따라 내려간다 (위로 올려 읽는 중이면 그대로)
  const followBottom = useCallback(() => {
    const el = scroller.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [])
  useLayoutEffect(followBottom, [lastId, followBottom])

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
          !newDay && prev?.type !== 'SYSTEM' && m.type !== 'SYSTEM' && prev.sender?.id === m.sender?.id && at.getTime() - new Date(prev.createdAt).getTime() < 5 * 60_000
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
            {editingId === m.id ? (
              // 고치는 중: 말풍선 대신 방 너비에 맞춘 입력칸 (좁은 화면에서도 잘리지 않게)
              <div className={cn('flex justify-end', grouped ? 'mt-1' : 'mt-4')}>
                <EditBubble
                  message={m}
                  onCancel={() => setEditingId(null)}
                  onSave={async (content) => {
                    await onEdit(m, content)
                    setEditingId(null)
                  }}
                />
              </div>
            ) : m.type === 'SYSTEM' ? (
              <p className="my-3 text-center text-xs text-gray-500 dark:text-gray-400">
                <span className="rounded-full bg-gray-100 px-3 py-1 dark:bg-gray-800">{systemText(m, t, fmt.list)}</span>
              </p>
            ) : (
              <div className={cn('flex items-end gap-2', mine && 'flex-row-reverse', grouped ? 'mt-1' : 'mt-4')}>
                {!mine && (grouped ? <span className="w-8 shrink-0" /> : <Avatar src={m.sender?.profileImageUrl} name={m.sender?.nickname ?? '?'} size="sm" />)}
                <div className={cn('flex max-w-[75%] flex-col', mine ? 'items-end' : 'items-start')}>
                  {!mine && !grouped && <span className="mb-1 ml-1 text-xs font-medium text-gray-600 dark:text-gray-400">{m.sender?.nickname ?? t('chat.unknown')}</span>}
                  <div className={cn('group flex items-end gap-1.5', mine && 'flex-row-reverse')}>
                    <div className={cn('flex min-w-0 flex-col gap-1.5', mine ? 'items-end' : 'items-start')}>
                      {m.deleted ? (
                        <p className="rounded-2xl border border-dashed border-gray-300 px-3.5 py-2 text-sm text-gray-400 italic dark:border-gray-700">
                          {t('chat.deletedMessage')}
                        </p>
                      ) : (
                        <>
                          {m.files.length > 0 && <MessageFiles files={m.files} mine={mine} onMediaLoad={followBottom} />}
                          {m.content && <p className={bubbleClass(mine)}>{m.content}</p>}
                        </>
                      )}
                    </div>
                    <span className="shrink-0 pb-0.5 text-[10px] text-gray-400">
                      {m.edited && !m.deleted && <span className="mr-1">{t('chat.edited')}</span>}
                      {fmt.time(m.createdAt)}
                    </span>
                    {/* 마우스를 올리면 고치기·지우기 (터치 화면은 늘 보인다) */}
                    {!m.deleted && editingId !== m.id && (mine || canModerate) && (
                      <span className="flex shrink-0 gap-0.5 pb-0.5 opacity-100 transition-opacity sm:opacity-0 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100">
                        {mine && m.type === 'TEXT' && (
                          <button type="button" title={t('chat.edit')} aria-label={t('chat.edit')} onClick={() => setEditingId(m.id)} className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200">
                            <Pencil className="size-3.5" />
                          </button>
                        )}
                        <button type="button" title={t('chat.delete')} aria-label={t('chat.delete')} onClick={() => onDelete(m)} className="rounded p-1 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10">
                          <Trash2 className="size-3.5" />
                        </button>
                      </span>
                    )}
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
  const [settings, setSettings] = useState(false)
  const { data: categories = [] } = useChatCategoriesQuery(room.teamId)
  const canManageChannel = room.type === 'GROUP' && hasTeamPermission(team?.team, 'CREATE_GROUP_ROOM')
  const category = categories.find((c) => c.id === room.categoryId)
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
  const replace = (m: ChatMessage) => {
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), (cur = []) => cur.map((x) => (x.id === m.id ? m : x)))
    void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
  }
  const canModerate = hasTeamPermission(team?.team, 'DELETE_ANY_MESSAGE')
  const editMessage = async (m: ChatMessage, content: string) => {
    try {
      replace(await chatApi.edit(m.id, content))
    } catch (e) {
      toastError(e)
      throw e
    }
  }
  const deleteMessage = (m: ChatMessage) => {
    if (!window.confirm(t(m.sender?.id === me?.id ? 'chat.deleteConfirm' : 'chat.deleteOthersConfirm'))) return
    chatApi.remove(m.id).then(replace, toastError)
  }

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
            {room.type === 'GROUP'
              ? [category?.name, t('chat.participants', { count: room.members.length })].filter(Boolean).join(' · ')
              : t('chat.direct')}
          </p>
        </div>
        {room.type === 'GROUP' && (
          <>
            {canManageChannel && (
              <IconButton label={t('chat.channelSettings')} onClick={() => setSettings(true)}>
                <Settings2 className="size-5" />
              </IconButton>
            )}
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
        <MessageList
          messages={messages}
          myId={me?.id}
          hasMore={hasMore}
          onLoadOlder={loadOlder}
          loadingOlder={loadingOlder}
          canModerate={canModerate}
          onEdit={editMessage}
          onDelete={deleteMessage}
        />
      )}

      <div className="shrink-0 border-t border-gray-200 p-3 sm:p-4 dark:border-gray-800">
        <MessageComposer roomId={room.id} onSent={appendMine} />
      </div>

      {team && <InviteToRoomModal room={room} teamMembers={team.members} open={inviting} onClose={() => setInviting(false)} />}
      {canManageChannel && <ChannelSettingsModal room={room} categories={categories} open={settings} onClose={() => setSettings(false)} />}
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
  const { data: categories = [] } = useChatCategoriesQuery(activeTeamId)
  const [modal, setModal] = useState<'direct' | 'group' | 'category' | null>(null)
  const [channelCategory, setChannelCategory] = useState<number | null>(null)
  const [editingCategory, setEditingCategory] = useState<ChatCategory | null>(null)

  const activeRoom = rooms.find((r) => r.id === roomId)
  const canCreateGroup = hasTeamPermission(team?.team, 'CREATE_GROUP_ROOM')
  const openChannel = (categoryId: number | null) => {
    setChannelCategory(categoryId)
    setModal('group')
  }
  const openCategory = (category: ChatCategory | null) => {
    setEditingCategory(category)
    setModal('category')
  }
  const deleteCategory = (category: ChatCategory) => {
    if (!window.confirm(t('chat.deleteCategoryConfirm', { name: category.name }))) return
    chatApi.deleteCategory(category.id).then(() => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.categories(activeTeamId) })
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(activeTeamId) })
    }, toastError)
  }

  const onEvent = useCallback(
    (payload: unknown) => {
      const ev = payload as ChatEvent
      if (ev.type === 'CATEGORIES') {
        void queryClient.invalidateQueries({ queryKey: chatKeys.categories(ev.teamId) })
        return
      }
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(ev.teamId) })
      const msg = ev.message
      if (msg && ev.type === 'MESSAGE_UPDATED') {
        // 누가 고치거나 지운 메시지: 같은 id 를 바꿔 끼운다
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => cur?.map((m) => (m.id === msg.id ? msg : m)))
      } else if (msg) {
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => (cur && !cur.some((m) => m.id === msg.id) ? [...cur, msg] : cur))
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
              disabled={!canCreateGroup}
              title={canCreateGroup ? undefined : t('chat.ownerOnlyGroup')}
              onClick={() => openChannel(null)}
            >
              {t('chat.group')}
            </Button>
            {canCreateGroup && (
              <IconButton label={t('chat.newCategory')} title={t('chat.newCategory')} onClick={() => openCategory(null)}>
                <FolderPlus className="size-5" />
              </IconButton>
            )}
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {roomsPending ? (
            <div className="flex justify-center py-10">
              <Spinner className="size-5 text-gray-400" />
            </div>
          ) : (
            <RoomSidebar
              rooms={rooms}
              categories={categories}
              activeId={roomId}
              myId={me?.id}
              canManage={canCreateGroup}
              onSelect={(r) => onNavigate(r.teamId, r.id)}
              onAddChannel={openChannel}
              onEditCategory={openCategory}
              onDeleteCategory={deleteCategory}
            />
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
            categories={categories}
            defaultCategoryId={channelCategory}
            open={modal === 'group'}
            onClose={() => setModal(null)}
            onCreated={(room) => onNavigate(room.teamId, room.id)}
          />
          <CategoryModal teamId={activeTeamId} category={editingCategory} open={modal === 'category'} onClose={() => setModal(null)} />
        </>
      )}
    </div>
  )
}
