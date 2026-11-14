import { MicOff } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui'

export function SpeakingAvatar({ person }: { person: { name: string; src: string | null; speaking: boolean; muted: boolean } }) {
  const { name, src, speaking, muted } = person
  return (
    <span className="relative inline-flex">
      <span className={cn('rounded-full ring-2 transition-shadow', speaking ? 'ring-green-500' : 'ring-transparent')}>
        <Avatar src={src} name={name} size="sm" />
      </span>
      {muted && (
        <span className="absolute -right-1 -bottom-1 flex size-4 items-center justify-center rounded-full bg-gray-700 text-white ring-2 ring-gray-50 dark:ring-gray-900">
          <MicOff className="size-2.5" />
        </span>
      )}
    </span>
  )
}
