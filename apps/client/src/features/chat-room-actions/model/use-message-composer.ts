import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type DragEvent, type KeyboardEvent, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, checkChatFiles, type ChatMessage, type ChatRoom } from '@/entities/chat'
import { useMeQuery } from '@/entities/user'
import { toastError } from '@/shared/api'
import { useDialog } from '@/shared/lib/use-dialog'
import { clearReply, useReplyTarget } from './reply-store'
import { type MentionOption, useMentionPicker } from './use-mention-picker'

/**
 * 메시지 입력창의 상태와 동작: 글·첨부파일·끌어다 놓기·이모지 넣기·@멘션·답장·보내기.
 * 보낸 메시지는 그 방 메시지 목록(캐시) 끝에 바로 붙인다 (실시간 이벤트가 늦게 와도 화면에 보이게)
 */
export function useMessageComposer(room: ChatRoom) {
  const roomId = room.id
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const [text, setTextState] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [dragging, setDragging] = useState(false)
  const picker = useRef<HTMLInputElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const replyTo = useReplyTarget(roomId)
  const mention = useMentionPicker(room, me?.id)
  // 이모지 창을 열면 입력칸 포커스가 빠지므로, 마지막 커서 위치를 기억해 그 자리에 넣는다
  const caret = useRef<{ start: number; end: number } | null>(null)

  const placeCaret = (pos: number) =>
    requestAnimationFrame(() => {
      input.current?.focus()
      input.current?.setSelectionRange(pos, pos)
    })

  // "입력 중..." 알리기: 글을 쓰는 동안 3초에 한 번
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

  const appendToRoom = (m: ChatMessage) =>
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(roomId), (cur = []) => (cur.some((x) => x.id === m.id) ? cur : [...cur, m]))

  const send = useMutation({
    mutationFn: (v: { content: string; files: File[] }) =>
      v.files.length
        ? chatApi.sendFiles(roomId, v.files, v.content)
        : chatApi.send(roomId, v.content, { replyToId: replyTo?.id ?? null, ...mention.collect(v.content) }),
    onSuccess: (m, v) => {
      // 파일은 올리는 동안 목록에 남겨 두고(무엇을 보내는 중인지 보이게) 성공하면 비운다
      if (v.files.length) {
        setFiles([])
        setTextState('')
      }
      clearReply(roomId)
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
    /** 첨부 하나 빼기 (보내는 중이면 null → 버튼을 막는다) */
    removeFile: (index: number) => (send.isPending ? null : () => setFiles(files.filter((_, j) => j !== index))),
    onKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.nativeEvent.isComposing) return
      // 멘션 목록이 열려 있으면 ↑↓ Enter Tab Esc 는 목록 조작
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
