import { ChevronDown, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { buildSections, type RoomSectionData } from '../lib/sections'
import { useWorkspace } from '../model/context'
import { RoomRow } from './RoomRow'

const sectionIcon = 'rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200'

/** 묶음 접기 상태 (이 화면에서만 기억) */
type Collapse = { isCollapsed: (key: string) => boolean; toggle: (key: string) => void }

function useCollapse(): Collapse {
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  return {
    isCollapsed: (key) => collapsed.has(key),
    toggle: (key) =>
      setCollapsed((cur) => {
        const next = new Set(cur)
        if (next.has(key)) next.delete(key)
        else next.add(key)
        return next
      }),
  }
}

/** 묶음 제목 옆 버튼: 채널 더하기 · (카테고리면) 이름 바꾸기 · 지우기 — 워크스페이스 OWNER·ADMIN */
function SectionActions({ section }: { section: RoomSectionData }) {
  const { t } = useTranslation()
  const ws = useWorkspace()
  if (section.addTo === undefined) return null
  const category = section.category
  return (
    <span className="flex shrink-0 gap-0.5 opacity-100 sm:opacity-0 sm:group-focus-within:opacity-100 sm:group-hover:opacity-100">
      <button type="button" className={sectionIcon} aria-label={t('chat.addChannel')} title={t('chat.addChannel')} onClick={() => ws.dialogs.channel.show(section.addTo ?? null)}>
        <Plus className="size-3.5" />
      </button>
      {category && (
        <>
          <button type="button" className={sectionIcon} aria-label={t('chat.renameCategory')} title={t('chat.renameCategory')} onClick={() => ws.dialogs.category.show(category)}>
            <Pencil className="size-3.5" />
          </button>
          <button type="button" className={sectionIcon} aria-label={t('chat.deleteCategory')} title={t('chat.deleteCategory')} onClick={() => ws.deleteCategory(category)}>
            <Trash2 className="size-3.5" />
          </button>
        </>
      )}
    </span>
  )
}

/** 묶음 하나: 제목(누르면 접기) + 방 목록 */
function RoomSection({ section, collapse }: { section: RoomSectionData; collapse: Collapse }) {
  const collapsed = collapse.isCollapsed(section.key)
  const unread = section.rooms.reduce((n, r) => n + r.unreadCount, 0)
  return (
    <div>
      <div className="group flex items-center gap-1 px-2 pt-3 pb-1">
        <button
          type="button"
          onClick={() => collapse.toggle(section.key)}
          aria-expanded={!collapsed}
          className="flex min-w-0 flex-1 items-center gap-1 rounded-md px-1 py-0.5 text-left text-xs font-semibold tracking-wide text-gray-500 uppercase hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
        >
          <ChevronDown className={cn('size-3.5 shrink-0 transition-transform', collapsed && '-rotate-90')} />
          <span className="truncate">{section.title}</span>
          {collapsed && unread > 0 && <span className="ml-1 size-2 shrink-0 rounded-full bg-brand-600" />}
        </button>
        <SectionActions section={section} />
      </div>
      {!collapsed &&
        (section.rooms.length ? (
          <ul className="flex flex-col gap-0.5 px-2">
            {section.rooms.map((room) => (
              <RoomRow key={room.id} room={room} />
            ))}
          </ul>
        ) : (
          section.emptyHint && <p className="px-5 py-1.5 text-xs text-gray-400">{section.emptyHint}</p>
        ))}
    </div>
  )
}

/**
 * 워크스페이스 사이드바 (디스코드처럼): 카테고리(부서) → 채널, 카테고리 없는 채널, 1:1 대화.
 * 카테고리·채널은 워크스페이스 OWNER·ADMIN 이 만들고 고친다.
 */
export function RoomSidebar() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const collapse = useCollapse()
  if (!ws.rooms.length && !ws.categories.length) return <p className="px-4 py-10 text-center text-sm text-gray-400">{t('chat.noRooms')}</p>
  return (
    <div className="pb-3">
      {buildSections(ws.rooms, ws.categories, ws.canManage, t).map((section) => (
        <RoomSection key={section.key} section={section} collapse={collapse} />
      ))}
    </div>
  )
}
