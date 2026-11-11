import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Check, FileText, LogOut, Paperclip, SendHorizonal, Smile, X } from 'lucide-react'
import { type FormEvent, type KeyboardEvent, type ReactNode, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { CHAT_FILE_LIMITS, chatApi, chatKeys, checkChatFiles, type ChatCategory, type ChatMessage, type ChatRoom } from '@/entities/chat'
import type { TeamMember } from '@/entities/team'
import { errorMessage, toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Button, Field, FieldError, Input, Modal, Select } from '@/shared/ui'
import { EmojiPickerModal } from './emoji-picker'

export { NoticeBar, NoticeModal, useSetNotice } from './notice'

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
    onError: (e) => toastError(e),
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

/** 채널(그룹 채팅방) 만들기 — 워크스페이스 OWNER·ADMIN. 카테고리(부서)를 고르면 그 안에 */
export function CreateGroupRoomModal({
  teamId,
  members,
  myId,
  categories,
  defaultCategoryId,
  open,
  onClose,
  onCreated,
}: {
  teamId: number
  members: TeamMember[]
  myId: number
  categories: ChatCategory[]
  /** 카테고리 옆 + 로 열면 그 카테고리가 미리 골라져 있다 */
  defaultCategoryId?: number | null
  open: boolean
  onClose: () => void
  onCreated: (room: ChatRoom) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [categoryId, setCategoryId] = useState<number | null>(defaultCategoryId ?? null)
  useEffect(() => {
    if (open) setCategoryId(defaultCategoryId ?? null)
  }, [open, defaultCategoryId])

  const create = useMutation({
    mutationFn: () => chatApi.createGroup(teamId, name.trim(), [...selected], categoryId),
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
        <Field label={t('chat.groupName')} htmlFor="group-name" required>
          <Input id="group-name" autoFocus maxLength={100} placeholder={t('chat.groupNamePlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <CategorySelect id="group-category" categories={categories} value={categoryId} onChange={setCategoryId} />
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
    onError: (e) => toastError(e),
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

/** 보내기 전 첨부파일 한 개: 이미지는 작은 미리보기, 나머지는 아이콘 */
function PendingFile({ file, disabled, onRemove }: { file: File; disabled: boolean; onRemove: () => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const [preview, setPreview] = useState<string | null>(null)
  useEffect(() => {
    if (!file.type.startsWith('image/')) return
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  return (
    <li className="flex max-w-56 items-center gap-2 rounded-xl border border-gray-200 bg-gray-50 py-1.5 pr-1.5 pl-2 dark:border-gray-700 dark:bg-gray-800">
      {preview ? (
        <img src={preview} alt="" className="size-8 shrink-0 rounded-md object-cover" />
      ) : (
        <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-white text-gray-500 dark:bg-gray-900 dark:text-gray-400">
          <FileText className="size-4" />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-medium text-gray-800 dark:text-gray-100">{file.name}</span>
        <span className="block text-[11px] text-gray-500 dark:text-gray-400">{fmt.bytes(file.size)}</span>
      </span>
      <button
        type="button"
        disabled={disabled}
        onClick={onRemove}
        aria-label={t('chat.removeFile', { name: file.name })}
        title={t('chat.removeFile', { name: file.name })}
        className="rounded-md p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700 disabled:opacity-40 dark:hover:bg-gray-700 dark:hover:text-gray-200"
      >
        <X className="size-3.5" />
      </button>
    </li>
  )
}

/** 입력칸 아래 기능 버튼 모양 (공지 버튼처럼 밖에서 붙이는 버튼도 같은 모양) */
export const toolClass =
  'flex h-8 items-center gap-1.5 rounded-lg px-2 text-xs font-medium text-gray-500 hover:bg-gray-100 hover:text-gray-800 disabled:opacity-40 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100'

/**
 * 메시지 입력창. Enter 보내기, Shift+Enter 줄바꿈, 한글 조합 중 Enter 무시.
 * 파일은 클립 버튼 · 붙여넣기(스크린샷) · 끌어다 놓기로 첨부하고, 함께 쓴 글과 한 메시지로 보낸다.
 */
export function MessageComposer({
  roomId,
  onSent,
  tools,
}: {
  roomId: number
  onSent: (m: ChatMessage) => void
  /** 입력칸 아래 버튼 줄에 더 붙일 버튼 (예: 공지) */
  tools?: ReactNode
}) {
  const { t } = useTranslation()
  const [text, setText] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [dragging, setDragging] = useState(false)
  const [emojiOpen, setEmojiOpen] = useState(false)
  const picker = useRef<HTMLInputElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  // 이모지 창을 열면 입력칸 포커스가 빠지므로, 마지막 커서 위치를 기억해 그 자리에 넣는다
  const caret = useRef<{ start: number; end: number } | null>(null)
  const rememberCaret = () => {
    const el = input.current
    if (el) caret.current = { start: el.selectionStart, end: el.selectionEnd }
  }
  const insertEmoji = (emoji: string) => {
    const at = caret.current ?? { start: text.length, end: text.length }
    const next = (text.slice(0, at.start) + emoji + text.slice(at.end)).slice(0, 4000)
    const pos = Math.min(at.start + emoji.length, next.length)
    setText(next)
    caret.current = { start: pos, end: pos }
    setEmojiOpen(false)
    requestAnimationFrame(() => {
      input.current?.focus()
      input.current?.setSelectionRange(pos, pos)
    })
  }

  const send = useMutation({
    mutationFn: (v: { content: string; files: File[] }) =>
      v.files.length ? chatApi.sendFiles(roomId, v.files, v.content) : chatApi.send(roomId, v.content),
    onSuccess: (m, v) => {
      // 파일은 올리는 동안 목록에 남겨 두고(무엇을 보내는 중인지 보이게) 성공하면 비운다
      if (v.files.length) {
        setFiles([])
        setText('')
      }
      onSent(m)
    },
    onError: (e, v) => {
      setText(v.content)
      toastError(e, t('chat.sendFailed'))
    },
  })

  const addFiles = (incoming: File[]) => {
    if (!incoming.length || send.isPending) return
    const next = [...files, ...incoming]
    const problem = checkChatFiles(next, t)
    if (problem) return void toast.error(problem)
    setFiles(next)
  }

  const submit = () => {
    const content = text.trim()
    if ((!content && !files.length) || send.isPending) return
    if (!files.length) setText('')
    send.mutate({ content, files })
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  const { maxFiles, maxFileMb } = CHAT_FILE_LIMITS
  const attachTitle = t('chat.attach') + ' · ' + t('chat.attachHint', { max: maxFiles, size: maxFileMb })
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      onDragOver={(e) => {
        if (!e.dataTransfer.types.includes('Files')) return
        e.preventDefault()
        setDragging(true)
      }}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
      }}
      onDrop={(e) => {
        if (!e.dataTransfer.files.length) return
        e.preventDefault()
        setDragging(false)
        addFiles(Array.from(e.dataTransfer.files))
      }}
      className={cn(
        'relative flex flex-col gap-2 rounded-2xl border bg-white p-2 shadow-xs focus-within:border-brand-500 focus-within:ring-3 focus-within:ring-brand-500/15 dark:bg-gray-900',
        dragging ? 'border-brand-500 ring-3 ring-brand-500/15' : 'border-gray-300 dark:border-gray-700',
      )}
    >
      {dragging && (
        <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-2xl bg-brand-50/90 text-sm font-medium text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">
          {t('chat.dropHere')}
        </div>
      )}
      {files.length > 0 && (
        <ul className="flex flex-wrap gap-2 px-1 pt-1">
          {files.map((f, i) => (
            <PendingFile
              key={f.name + ':' + f.size + ':' + f.lastModified + ':' + i}
              file={f}
              disabled={send.isPending}
              onRemove={() => setFiles(files.filter((_, j) => j !== i))}
            />
          ))}
        </ul>
      )}
      <div className="flex items-end gap-2">
        <input
          ref={picker}
          type="file"
          multiple
          hidden
          onChange={(e) => {
            addFiles(Array.from(e.target.files ?? []))
            e.target.value = ''
          }}
        />
        <textarea
          ref={input}
          rows={1}
          value={text}
          maxLength={4000}
          placeholder={t('chat.placeholder')}
          aria-label={t('chat.placeholder')}
          title={t('chat.sendHint')}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          onSelect={rememberCaret}
          onBlur={rememberCaret}
          onPaste={(e) => {
            const pasted = Array.from(e.clipboardData.files)
            if (!pasted.length) return
            e.preventDefault()
            addFiles(pasted)
          }}
          className="max-h-40 min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-[15px] text-gray-900 outline-none field-sizing-content placeholder:text-gray-400 dark:text-gray-100"
        />
        <Button
          type="submit"
          size="sm"
          className="h-9 w-9 shrink-0 rounded-xl p-0"
          aria-label={t('common.send')}
          loading={send.isPending}
          disabled={!text.trim() && !files.length}
        >
          {!send.isPending && <SendHorizonal className="size-4.5" />}
        </Button>
      </div>
      {/* 기능 버튼 줄: 첨부 · 이모지 · (방에 따라) 공지 */}
      <div className="flex items-center gap-0.5 border-t border-gray-100 px-0.5 pt-1.5 dark:border-gray-800">
        <button type="button" disabled={send.isPending} onClick={() => picker.current?.click()} aria-label={t('chat.attach')} title={attachTitle} className={toolClass}>
          <Paperclip className="size-4" />
          <span className="hidden sm:inline">{t('chat.attachShort')}</span>
        </button>
        <button type="button" onClick={() => setEmojiOpen(true)} aria-label={t('chat.emoji.open')} title={t('chat.emoji.open')} className={toolClass}>
          <Smile className="size-4" />
          <span className="hidden sm:inline">{t('chat.emoji.short')}</span>
        </button>
        {tools}
        <span className="ml-auto hidden pr-1 text-[11px] text-gray-400 md:inline">{t('chat.sendHint')}</span>
      </div>
      <EmojiPickerModal open={emojiOpen} onClose={() => setEmojiOpen(false)} onPick={insertEmoji} />
    </form>
  )
}

/** 카테고리(부서) 고르기. 비우면 카테고리 밖 */
function CategorySelect({
  id,
  categories,
  value,
  onChange,
}: {
  id: string
  categories: ChatCategory[]
  value: number | null
  onChange: (v: number | null) => void
}) {
  const { t } = useTranslation()
  return (
    <Field label={t('chat.category')} htmlFor={id} hint={t('chat.categoryHint')}>
      <Select id={id} value={value ?? ''} onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}>
        <option value="">{t('chat.noCategory')}</option>
        {categories.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </Select>
    </Field>
  )
}

/** 카테고리(부서) 만들기 / 이름 바꾸기 — 워크스페이스 OWNER·ADMIN */
export function CategoryModal({
  teamId,
  category,
  open,
  onClose,
}: {
  teamId: number
  /** 있으면 이름 바꾸기, 없으면 새로 만들기 */
  category?: ChatCategory | null
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  useEffect(() => {
    if (open) setName(category?.name ?? '')
  }, [open, category])

  const save = useMutation({
    mutationFn: () => (category ? chatApi.renameCategory(category.id, name.trim()) : chatApi.createCategory(teamId, name.trim())),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.categories(teamId) })
      onClose()
    },
  })
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) save.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={category ? t('chat.renameCategory') : t('chat.newCategory')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="category-form" loading={save.isPending} disabled={!name.trim()}>
            {category ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <form id="category-form" onSubmit={onSubmit} className="flex flex-col gap-3">
        <Field label={t('chat.categoryName')} htmlFor="category-name" required hint={t('chat.categoryNameHint')}>
          <Input id="category-name" autoFocus maxLength={50} placeholder={t('chat.categoryPlaceholder')} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <FieldError>{errorMessage(save.error)}</FieldError>
      </form>
    </Modal>
  )
}

/** 채널 설정: 이름 바꾸기, 다른 카테고리로 옮기기 — 워크스페이스 OWNER·ADMIN */
export function ChannelSettingsModal({
  room,
  categories,
  open,
  onClose,
}: {
  room: ChatRoom
  categories: ChatCategory[]
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  useEffect(() => {
    if (open) {
      setName(room.name ?? '')
      setCategoryId(room.categoryId)
    }
  }, [open, room])

  const save = useMutation({
    mutationFn: () => chatApi.updateChannel(room.id, { name: name.trim(), categoryId, moveCategory: true }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      onClose()
    },
  })
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) save.mutate()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={t('chat.channelSettings')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="channel-form" loading={save.isPending} disabled={!name.trim()}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="channel-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <Field label={t('chat.groupName')} htmlFor="channel-name" required>
          <Input id="channel-name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <CategorySelect id="channel-category" categories={categories} value={categoryId} onChange={setCategoryId} />
        <FieldError>{errorMessage(save.error)}</FieldError>
      </form>
    </Modal>
  )
}
