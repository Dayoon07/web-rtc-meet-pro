import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type DragEvent, type KeyboardEvent, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, checkChatFiles, type ChatMessage, type ChatRoom, type ChatThread } from '@/entities/chat'
import { useMeQuery } from '@/entities/user'
import { toastError } from '@/shared/api'
import { useDialog } from '@/shared/lib/use-dialog'
import { clearReply, useReplyTarget } from './reply-store'
import { type MentionOption, useMentionPicker } from './use-mention-picker'

export type ComposerTarget = { room: ChatRoom; threadRootId?: number }

export function useMessageComposer({ room, threadRootId }: ComposerTarget) {
  const roomId = room.id
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const [text, setTextState] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [dragging, setDragging] = useState(false)
  const picker = useRef<HTMLInputElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const roomReply = useReplyTarget(roomId)
  const replyTo = threadRootId ? null : roomReply
  const mention = useMentionPicker(room, me?.id)
  const caret = useRef<{ start: number; end: number } | null>(null)

  const placeCaret = (pos: number) =>
    requestAnimationFrame(() => {
      input.current?.focus()
      input.current?.setSelectionRange(pos, pos)
    })

  const lastTyping = useRef(0)
  const setText = (value: string) => {
    setTextState(value)
    if (value.trim() && Date.now() - lastTyping.current > 3000) {
      lastTyping.current = Date.now()
      chatApi.typing(roomId).catch(() => undefined)
    }
    const el = input.current
    mention.track(value, el?.selectionStart ?? value.length)
  }

  const rememberCaret = () => {
    const el = input.current
    if (!el) return
    caret.current = { start: el.selectionStart, end: el.selectionEnd }
    mention.track(el.value, el.selectionStart)
  }

  const insertEmoji = (emoji: string) => {
    const at = caret.current ?? { start: text.length, end: text.length }
    const next = (text.slice(0, at.start) + emoji + text.slice(at.end)).slice(0, 4000)
    const pos = Math.min(at.start + emoji.length, next.length)
    setTextState(next)
    caret.current = { start: pos, end: pos }
    placeCaret(pos)
  }
  const emoji = useDialog<undefined, string>(insertEmoji)

  const pickMention = (option: MentionOption) => {
    const at = input.current?.selectionStart ?? text.length
    const next = mention.apply(option, text, at)
    setTextState(next.text)
    placeCaret(next.caret)
  }

  const appendToRoom = (m: ChatMessage) => {
    if (threadRootId) {
      queryClient.setQueryData<ChatThread>(chatKeys.thread(threadRootId), (cur) =>
        cur && !cur.replies.some((x) => x.id === m.id) ? { ...cur, replies: [...cur.replies, m] } : cur,
      )
      return
    }
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(roomId), (cur = []) => (cur.some((x) => x.id === m.id) ? cur : [...cur, m]))
  }

  const send = useMutation({
    mutationFn: (v: { content: string; files: File[] }) =>
      v.files.length
        ? chatApi.sendFiles(roomId, v.files, v.content, threadRootId)
        : chatApi.send(roomId, v.content, { replyToId: replyTo?.id ?? null, threadRootId: threadRootId ?? null, ...mention.collect(v.content) }),
    onSuccess: (m, v) => {
      if (v.files.length) {
        setFiles([])
        setTextState('')
      }
      if (!threadRootId) clearReply(roomId)
      mention.reset()
      appendToRoom(m)
    },
    onError: (e, v) => {
      setTextState(v.content)
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
    if (!files.length) setTextState('')
    send.mutate({ content, files })
  }

  return {
    text,
    files,
    dragging,
    picker,
    input,
    emoji,
    mention,
    pickMention,
    replyTo,
    cancelReply: () => clearReply(roomId),
    sending: send.isPending,
    canSend: !!text.trim() || files.length > 0,
    submit,
    addFiles,
    rememberCaret,
    setText,
    removeFile: (index: number) => (send.isPending ? null : () => setFiles(files.filter((_, j) => j !== index))),
    onKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.nativeEvent.isComposing) return
      if (mention.open) {
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault()
          mention.move(e.key === 'ArrowDown' ? 1 : -1)
          return
        }
        if (e.key === 'Enter' || e.key === 'Tab') {
          e.preventDefault()
          pickMention(mention.options[mention.active])
          return
        }
        if (e.key === 'Escape') {
          e.preventDefault()
          mention.close()
          return
        }
      }
      if (e.key === 'Escape' && replyTo) {
        clearReply(roomId)
        return
      }
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        submit()
      }
    },
    drop: {
      onDragOver: (e: DragEvent) => {
        if (!e.dataTransfer.types.includes('Files')) return
        e.preventDefault()
        setDragging(true)
      },
      onDragLeave: (e: DragEvent) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
      },
      onDrop: (e: DragEvent) => {
        if (!e.dataTransfer.files.length) return
        e.preventDefault()
        setDragging(false)
        addFiles(Array.from(e.dataTransfer.files))
      },
    },
  }
}
