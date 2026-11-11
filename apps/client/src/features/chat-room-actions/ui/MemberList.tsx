import { Check } from 'lucide-react'
import type { ReactNode } from 'react'
import type { TeamMember } from '@/entities/team'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui'
import type { MemberSelection } from '../model/types'

const rowClass = 'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors disabled:opacity-40'

/** 팀원 한 줄: 프로필 사진 · 닉네임 · 이메일 (+ 오른쪽에 붙일 것) */
function MemberInfo({ member, children }: { member: TeamMember; children?: ReactNode }) {
  return (
    <>
      <Avatar src={member.profileImageUrl} name={member.nickname} size="sm" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-gray-900 dark:text-white">{member.nickname}</span>
        <span className="block truncate text-xs text-gray-500">{member.email}</span>
      </span>
      {children}
    </>
  )
}

/** 팀원 한 명 고르기 (누르면 바로 onPick) */
export function MemberPicker({ members, onPick }: { members: TeamMember[]; onPick: (member: TeamMember) => void }) {
  return (
    <ul className="flex max-h-80 flex-col gap-1 overflow-y-auto">
      {members.map((m) => (
        <li key={m.userId}>
          <button type="button" onClick={() => onPick(m)} className={cn(rowClass, 'hover:bg-gray-100 dark:hover:bg-gray-800')}>
            <MemberInfo member={m} />
          </button>
        </li>
      ))}
    </ul>
  )
}

/** 팀원 여러 명 고르기 (체크 표시) */
export function MemberChecklist({ members, selection }: { members: TeamMember[]; selection: MemberSelection }) {
  return (
    <ul className="flex max-h-80 flex-col gap-1 overflow-y-auto">
      {members.map((m) => {
        const checked = selection.selected.has(m.userId)
        return (
          <li key={m.userId}>
            <button
              type="button"
              onClick={() => selection.toggle(m.userId)}
              className={cn(rowClass, checked ? 'bg-brand-50 dark:bg-brand-500/15' : 'hover:bg-gray-100 dark:hover:bg-gray-800')}
            >
              <MemberInfo member={m}>
                <span
                  className={cn(
                    'flex size-5 items-center justify-center rounded-md border',
                    checked ? 'border-brand-600 bg-brand-600 text-white' : 'border-gray-300 dark:border-gray-600',
                  )}
                >
                  {checked && <Check className="size-3.5" strokeWidth={3} />}
                </span>
              </MemberInfo>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
