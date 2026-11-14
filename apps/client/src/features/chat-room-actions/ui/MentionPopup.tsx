import { Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui'
import type { MentionOption, MentionPicker } from '../model/use-mention-picker'

export function MentionPopup({ picker, onPick }: { picker: MentionPicker; onPick: (option: MentionOption) => void }) {
  const { t } = useTranslation()
  if (!picker.open) return null
  return (
    <ul
      role="listbox"
      aria-label={t('chat.mention.title')}
      onMouseDown={(e) => e.preventDefault()}
      className="absolute bottom-full left-2 z-20 mb-2 max-h-64 w-64 overflow-y-auto rounded-xl border border-gray-200 bg-white p-1 shadow-lg dark:border-gray-700 dark:bg-gray-900"
    >
      {picker.options.map((o, i) => (
        <li
          key={o.kind === 'all' ? 'all' : o.user.id}
          role="option"
          aria-selected={i === picker.active}
          onClick={() => onPick(o)}
          className={cn(
            'flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm text-gray-800 dark:text-gray-100',
            i === picker.active && 'bg-gray-100 dark:bg-gray-800',
          )}
        >
          {o.kind === 'all' ? (
            <>
              <span className="flex size-7 items-center justify-center rounded-full bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">
                <Users className="size-4" />
              </span>
              <span className="font-medium">@{o.label}</span>
              <span className="ml-auto text-xs text-gray-400">{t('chat.mention.allHint')}</span>
            </>
          ) : (
            <>
              <Avatar src={o.user.profileImageUrl} name={o.user.nickname} size="xs" />
              <span className="truncate font-medium">{o.user.nickname}</span>
            </>
          )}
        </li>
      ))}
    </ul>
  )
}
