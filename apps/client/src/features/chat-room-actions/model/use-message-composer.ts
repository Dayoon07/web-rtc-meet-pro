import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type DragEvent, type KeyboardEvent, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, checkChatFiles, type ChatMessage } from '@/entities/chat'
import { toastError } from '@/shared/api'
import { useDialog } from '@/shared/lib/use-dialog'

/**
 * 메시지 입력창의 상태와 동작: 글·첨부파일·끌어다 놓기·이모지 넣기·보내기.
 * 보낸 메시지는 그 방 메시지 목록(캐시) 끝에 바로 붙인다 (실시간 이벤트가 늦게 와도 화면에 보이게)
 */
export function useMessageComposer(roomId: number) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [dragging, setDragging] = useState(false)
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
    requestAnimationFrame(() => {
      input.current?.focus()
      input.current?.setSelectionRange(pos, pos)
    })
  }
  const emoji = useDialog<undefined, string>(insertEmoji)

  const appendToRoom = (m: ChatMessage) =>
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(roomId), (cur = []) => (cur.some((x) => x.id === m.id) ? cur : [...cur, m]))

  const send = useMutation({
    mutationFn: (v: { content: string; files: File[] }) =>
      v.files.length ? chatApi.sendFiles(roomId, v.files, v.content) : chatApi.send(roomId, v.content),
    onSuccess: (m, v) => {
      // 파일은 올리는 동안 목록에 남겨 두고(무엇을 보내는 중인지 보이게) 성공하면 비운다
      if (v.files.length) {
        setFiles([])
        setText('')
      }
      appendToRoom(m)
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

  return {
    text,
    files,
    dragging,
    picker,
    input,
    emoji,
    sending: send.isPending,
    canSend: !!text.trim() || files.length > 0,
    submit,
    addFiles,
    rememberCaret,
    setText,
    /** 첨부 하나 빼기 (보내는 중이면 null → 버튼을 막는다) */
    removeFile: (index: number) => (send.isPending ? null : () => setFiles(files.filter((_, j) => j !== index))),
    onKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
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
