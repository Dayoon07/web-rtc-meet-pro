import { CornerUpLeft, Paperclip, SendHorizonal, Smile, X } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { CHAT_FILE_LIMITS, filesSummary } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui'
import { toolClass } from '../lib/styles'
import { type ComposerTarget, useMessageComposer } from '../model/use-message-composer'
import { EmojiPickerModal } from './EmojiPickerModal'
import { MentionPopup } from './MentionPopup'
import { PendingFile } from './PendingFile'

export function MessageComposer({ target, tools }: { target: ComposerTarget; tools?: ReactNode }) {
  const { t } = useTranslation()
  const c = useMessageComposer(target)
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
      {c.replyTo && (
        <div className="flex items-center gap-2 rounded-xl bg-gray-50 px-3 py-2 text-xs dark:bg-gray-800">
          <CornerUpLeft className="size-3.5 shrink-0 text-brand-600 dark:text-brand-300" />
          <span className="min-w-0 flex-1 truncate text-gray-600 dark:text-gray-300">
            <span className="font-semibold text-gray-900 dark:text-white">{t('chat.replyTo', { name: c.replyTo.sender?.nickname ?? t('chat.unknown') })}</span>
            {' · '}
            {c.replyTo.content || filesSummary(c.replyTo.files, t)}
          </span>
          <button type="button" onClick={c.cancelReply} aria-label={t('chat.cancelReply')} title={t('chat.cancelReply')} className="rounded p-0.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
            <X className="size-3.5" />
          </button>
        </div>
      )}
      {c.files.length > 0 && (
        <ul className="flex flex-wrap gap-2 px-1 pt-1">
          {c.files.map((f, i) => (
            <PendingFile key={f.name + ':' + f.size + ':' + f.lastModified + ':' + i} file={f} onRemove={c.removeFile(i)} />
          ))}
        </ul>
      )}
      <div className="relative flex items-end gap-2">
        <MentionPopup picker={c.mention} onPick={c.pickMention} />
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
          placeholder={t(target.threadRootId ? 'chat.thread.placeholder' : 'chat.placeholder')}
          aria-label={t(target.threadRootId ? 'chat.thread.placeholder' : 'chat.placeholder')}
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
