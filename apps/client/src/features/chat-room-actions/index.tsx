import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, LogOut, SendHorizonal } from 'lucide-react'
import { type FormEvent, type KeyboardEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom } from '@/entities/chat'
import type { TeamMember } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { Avatar, Button, Field, FieldError, Input, Modal } from '@/shared/ui'

/** 팀원 목록 (단일 선택: onPick / 다중 선택: selected+onToggle) */
function MemberList({
  members,
  selected,
  onToggle,
  onPick,
  disabledIds = [],
}: {
  members: TeamMember[]
  selected?: Set<number>
  onToggle?: (id: number) => void
  onPick?: (m: TeamMember) => void
  disabledIds?: number[]
}) {
  return (
    <ul className="flex max-h-80 flex-col gap-1 overflow-y-auto">
      {members.map((m) => {
        const checked = selected?.has(m.userId) ?? false
        const disabled = disabledIds.includes(m.userId)
        return (
          <li key={m.userId}>
            <button
              type="button"
              disabled={disabled}
              onClick={() => (onPick ? onPick(m) : onToggle?.(m.userId))}
              className={cn(
                'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors disabled:opacity-40',
                checked ? 'bg-brand-50 dark:bg-brand-500/15' : 'hover:bg-gray-100 dark:hover:bg-gray-800',
              )}
            >
              <Avatar src={m.profileImageUrl} name={m.nickname} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-gray-900 dark:text-white">{m.nickname}</span>
                <span className="block truncate text-xs text-gray-500">{m.email}</span>
              </span>
              {onToggle && (
                <span
                  className={cn(
                    'flex size-5 items-center justify-center rounded-md border',
                    checked ? 'border-brand-600 bg-brand-600 text-white' : 'border-gray-300 dark:border-gray-600',
                  )}
                >
                  {checked && <Check className="size-3.5" strokeWidth={3} />}
                </span>
              )}
            </button>
          </li>
        )
      })}
    </ul>
  )
}

const toggleIn = (set: Set<number>, id: number) => {
  const next = new Set(set)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  return next
}

/** 새 1:1 채팅: 팀원을 고르면 바로 방이 열린다 (이미 있으면 그 방) */
export function StartDirectChatModal({
  teamId,
  members,
  myId,
  open,
  onClose,
  onOpened,
}: {
  teamId: number
  members: TeamMember[]
  myId: number
  open: boolean
  onClose: () => void
  onOpened: (room: ChatRoom) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const others = members.filter((m) => m.userId !== myId)

  const openDirect = useMutation({
    mutationFn: (userId: number) => chatApi.openDirect(teamId, userId),
    onSuccess: (room) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
      onClose()
      onOpened(room)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Modal open={open} onClose={onClose} title={t('chat.newDirect')} size="sm">
      <p className="mb-3 text-sm text-gray-500 dark:text-gray-400">{t('chat.pickMember')}</p>
      {others.length ? (
        <MemberList members={others} onPick={(m) => openDirect.mutate(m.userId)} />
      ) : (
        <p className="py-6 text-center text-sm text-gray-400">{t('chat.noOtherMembers')}</p>
      )}
    </Modal>
  )
}

/** 그룹 채팅방 만들기 (팀 소유자 전용, 플랜 한도) */
export function CreateGroupRoomModal({
  teamId,
  members,
  myId,
  usage,
  open,
  onClose,
  onCreated,
}: {
  teamId: number
  members: TeamMember[]
  myId: number
  usage: { used: number; max: number }
  open: boolean
  onClose: () => void
  onCreated: (room: ChatRoom) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const create = useMutation({
    mutationFn: () => chatApi.createGroup(teamId, name.trim(), [...selected]),
    onSuccess: (room) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(teamId) })
      setName('')
      setSelected(new Set())
      onClose()
      onCreated(room)
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) create.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('chat.newGroup')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="group-room-form" loading={create.isPending} disabled={!name.trim()}>
            {t('common.create')}
          </Button>
        </>
      }
    >
      <form id="group-room-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <Field label={t('chat.groupName')} htmlFor="group-name" required hint={t('chat.groupUsage', usage)}>
          <Input id="group-name" autoFocus maxLength={100} placeholder={t('chat.groupNamePlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <div>
          <p className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
            {t('chat.groupMembers')} <span className="text-gray-400">({selected.size})</span>
          </p>
          <MemberList members={members.filter((m) => m.userId !== myId)} selected={selected} onToggle={(id) => setSelected((s) => toggleIn(s, id))} />
        </div>
        <FieldError>{errorMessage(create.error)}</FieldError>
      </form>
    </Modal>
  )
}

/** 그룹 방에 팀원 초대 (방 참여자 누구나) */
export function InviteToRoomModal({
  room,
  teamMembers,
  open,
  onClose,
}: {
  room: ChatRoom
  teamMembers: TeamMember[]
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const inRoom = new Set(room.members.map((m) => m.id))
  const candidates = teamMembers.filter((m) => !inRoom.has(m.userId))

  const invite = useMutation({
    mutationFn: () => chatApi.invite(room.id, [...selected]),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      void queryClient.invalidateQueries({ queryKey: chatKeys.messages(room.id) })
      setSelected(new Set())
      onClose()
    },
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('chat.inviteTitle')}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button loading={invite.isPending} disabled={!selected.size} onClick={() => invite.mutate()}>
            {t('common.invite')} {selected.size > 0 && `(${selected.size})`}
          </Button>
        </>
      }
    >
      {candidates.length ? (
        <MemberList members={candidates} selected={selected} onToggle={(id) => setSelected((s) => toggleIn(s, id))} />
      ) : (
        <p className="py-6 text-center text-sm text-gray-400">{t('chat.allInRoom')}</p>
      )}
      <FieldError>{errorMessage(invite.error)}</FieldError>
    </Modal>
  )
}

export function LeaveRoomButton({ room, onLeft }: { room: ChatRoom; onLeft: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const leave = useMutation({
    mutationFn: () => chatApi.leave(room.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      onLeft()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <Button
      variant="ghost"
      size="sm"
      icon={<LogOut className="size-4" />}
      loading={leave.isPending}
      onClick={() => window.confirm(t('chat.leaveConfirm')) && leave.mutate()}
    >
      <span className="hidden lg:inline">{t('chat.leave')}</span>
    </Button>
  )
}

/** 메시지 입력창. Enter 보내기, Shift+Enter 줄바꿈, 한글 조합 중 Enter 무시 */
export function MessageComposer({ roomId, onSent }: { roomId: number; onSent: (m: ChatMessage) => void }) {
  const { t } = useTranslation()
  const [text, setText] = useState('')
  const send = useMutation({
    mutationFn: (content: string) => chatApi.send(roomId, content),
    onSuccess: (m) => onSent(m),
    onError: (e, content) => {
      setText(content)
      toast.error(errorMessage(e) ?? t('chat.sendFailed'))
    },
  })

  const submit = () => {
    const content = text.trim()
    if (!content || send.isPending) return
    setText('')
    send.mutate(content)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      className="flex items-end gap-2 rounded-2xl border border-gray-300 bg-white p-2 shadow-xs focus-within:border-brand-500 focus-within:ring-3 focus-within:ring-brand-500/15 dark:border-gray-700 dark:bg-gray-900"
    >
      <textarea
        rows={1}
        value={text}
        maxLength={4000}
        placeholder={t('chat.placeholder')}
        aria-label={t('chat.placeholder')}
        title={t('chat.sendHint')}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        className="max-h-40 min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-[15px] text-gray-900 outline-none field-sizing-content placeholder:text-gray-400 dark:text-gray-100"
      />
      <Button type="submit" size="sm" className="h-9 w-9 shrink-0 rounded-xl p-0" aria-label={t('common.send')} disabled={!text.trim()}>
        <SendHorizonal className="size-4.5" />
      </Button>
    </form>
  )
}
