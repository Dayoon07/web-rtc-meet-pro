import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Modal } from '@/shared/ui'
import type { Dialog } from '@/shared/lib/use-dialog'
import { CATEGORIES, type EmojiCategory, loadRecent, saveRecent, TAB_ICON } from '../lib/emoji'

/** 이모지 고르기 창: 분류 탭 + 최근 쓴 것. 고르면 dialog.done(이모지) 으로 넘기고 닫힌다 */
export function EmojiPickerModal({ dialog }: { dialog: Dialog<undefined, string> }) {
  const { t } = useTranslation()
  const [recent, setRecent] = useState(loadRecent)
  const [tab, setTab] = useState<EmojiCategory>(() => (loadRecent().length ? 'recent' : 'smileys'))
  const list = tab === 'recent' ? recent : CATEGORIES[tab].split(' ')

  const pick = (emoji: string) => {
    saveRecent(emoji)
    setRecent(loadRecent())
    dialog.done(emoji)
  }

  return (
    <Modal open={dialog.open} onClose={dialog.close} title={t('chat.emoji.title')} size="md">
      <div className="flex flex-col gap-3">
        <div role="tablist" aria-label={t('chat.emoji.title')} className="flex gap-1 overflow-x-auto rounded-xl bg-gray-100 p-1 dark:bg-gray-800">
          {(Object.keys(TAB_ICON) as EmojiCategory[]).map((c) => (
            <button
              key={c}
              type="button"
              role="tab"
              aria-selected={tab === c}
              title={t(`chat.emoji.${c}`)}
              aria-label={t(`chat.emoji.${c}`)}
              onClick={() => setTab(c)}
              className={cn(
                'flex h-9 min-w-9 flex-1 items-center justify-center rounded-lg text-lg transition',
                tab === c ? 'bg-white shadow-sm dark:bg-gray-950' : 'opacity-60 hover:opacity-100',
              )}
            >
              {TAB_ICON[c]}
            </button>
          ))}
        </div>
        <p className="text-xs font-semibold text-gray-500 dark:text-gray-400">{t(`chat.emoji.${tab}`)}</p>
        {list.length ? (
          <div className="grid max-h-72 grid-cols-8 gap-1 overflow-y-auto sm:grid-cols-10">
            {list.map((e, i) => (
              <button
                key={e + i}
                type="button"
                onClick={() => pick(e)}
                aria-label={e}
                className="flex aspect-square items-center justify-center rounded-lg text-2xl transition hover:scale-110 hover:bg-gray-100 dark:hover:bg-gray-800"
              >
                {e}
              </button>
            ))}
          </div>
        ) : (
          <p className="py-10 text-center text-sm text-gray-400">{t('chat.emoji.noRecent')}</p>
        )}
      </div>
    </Modal>
  )
}
