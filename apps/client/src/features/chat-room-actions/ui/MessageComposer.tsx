import { Paperclip, SendHorizonal, Smile } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CHAT_FILE_LIMITS } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'
import { toolClass } from '../lib/styles'
import { useMessageComposer } from '../model/use-message-composer'
import { EmojiPickerModal } from './EmojiPickerModal'
import { PendingFile } from './PendingFile'

/**
 * 메시지 입력창. Enter 보내기, Shift+Enter 줄바꿈, 한글 조합 중 Enter 무시.
 * 파일은 클립 버튼 · 붙여넣기(스크린샷) · 끌어다 놓기로 첨부하고, 함께 쓴 글과 한 메시지로 보낸다.
 * tools: 입력칸 아래 버튼 줄에 더 붙일 버튼 (예: 공지)
 */
export function MessageComposer({ roomId, tools }: { roomId: number; tools?: ReactNode }) {
  const { t } = useTranslation()
  const c = useMessageComposer(roomId)
  const { maxFiles, maxFileMb } = CHAT_FILE_LIMITS
  const attachTitle = t('chat.attach') + ' · ' + t('chat.attachHint', { max: maxFiles, size: maxFileMb })

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        c.submit()
      }}
      {...c.drop}
      className={cn(
        'relative flex flex-col gap-2 rounded-2xl border bg-white p-2 shadow-xs focus-within:border-brand-500 focus-within:ring-3 focus-within:ring-brand-500/15 dark:bg-gray-900',
        c.dragging ? 'border-brand-500 ring-3 ring-brand-500/15' : 'border-gray-300 dark:border-gray-700',
      )}
    >
      {c.dragging && (
        <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-2xl bg-brand-50/90 text-sm font-medium text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">
          {t('chat.dropHere')}
        </div>
      )}
      {c.files.length > 0 && (
        <ul className="flex flex-wrap gap-2 px-1 pt-1">
          {c.files.map((f, i) => (
            <PendingFile key={f.name + ':' + f.size + ':' + f.lastModified + ':' + i} file={f} onRemove={c.removeFile(i)} />
          ))}
        </ul>
      )}
      <div className="flex items-end gap-2">
        <input
          ref={c.picker}
          type="file"
          multiple
          hidden
          onChange={(e) => {
            c.addFiles(Array.from(e.target.files ?? []))
            e.target.value = ''
          }}
        />
        <textarea
          ref={c.input}
          rows={1}
          value={c.text}
          maxLength={4000}
          placeholder={t('chat.placeholder')}
          aria-label={t('chat.placeholder')}
          title={t('chat.sendHint')}
          onChange={(e) => c.setText(e.target.value)}
          onKeyDown={c.onKeyDown}
          onSelect={c.rememberCaret}
          onBlur={c.rememberCaret}
          onPaste={(e) => {
            const pasted = Array.from(e.clipboardData.files)
            if (!pasted.length) return
            e.preventDefault()
            c.addFiles(pasted)
          }}
          className="max-h-40 min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-[15px] text-gray-900 outline-none field-sizing-content placeholder:text-gray-400 dark:text-gray-100"
        />
        <Button type="submit" size="sm" className="h-9 w-9 shrink-0 rounded-xl p-0" aria-label={t('common.send')} loading={c.sending} disabled={!c.canSend}>
          {!c.sending && <SendHorizonal className="size-4.5" />}
        </Button>
      </div>
      {/* 기능 버튼 줄: 첨부 · 이모지 · (방에 따라) 공지 */}
      <div className="flex items-center gap-0.5 border-t border-gray-100 px-0.5 pt-1.5 dark:border-gray-800">
        <button type="button" disabled={c.sending} onClick={() => c.picker.current?.click()} aria-label={t('chat.attach')} title={attachTitle} className={toolClass}>
          <Paperclip className="size-4" />
          <span className="hidden sm:inline">{t('chat.attachShort')}</span>
        </button>
        <button type="button" onClick={() => c.emoji.show()} aria-label={t('chat.emoji.open')} title={t('chat.emoji.open')} className={toolClass}>
          <Smile className="size-4" />
          <span className="hidden sm:inline">{t('chat.emoji.short')}</span>
        </button>
        {tools}
        <span className="ml-auto hidden pr-1 text-[11px] text-gray-400 md:inline">{t('chat.sendHint')}</span>
      </div>
      <EmojiPickerModal dialog={c.emoji} />
    </form>
  )
}
