import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import { isSameDay, useFormat } from '@/shared/lib/format'
import { Button } from '@/shared/ui'
import { useRoom } from '../model/context'
import { MessageItem } from './MessageItem'

/** 날짜가 바뀌는 곳의 가는 줄 ("오늘" / "11월 7일 토요일") */
function DayDivider({ date }: { date: Date }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  return (
    <div className="my-4 flex items-center gap-3 text-xs text-gray-400">
      <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
      {isSameDay(date, new Date()) ? t('chat.today') : fmt.dayHeading(date)}
      <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
    </div>
  )
}

/** 방의 메시지 목록: 위로 "이전 메시지 더 보기", 날짜 줄, 같은 사람의 연속 메시지는 묶어서 */
export function MessageList() {
  const { t } = useTranslation()
  const { messages: list } = useRoom()
  const { messages } = list

  return (
    <div ref={list.scroller} onScroll={(e) => list.onScroll(e.currentTarget)} className="flex-1 overflow-y-auto px-4 py-4 sm:px-6">
      {list.hasMore && (
        <div className="mb-4 flex justify-center">
          <Button variant="ghost" size="sm" loading={list.loadingOlder} onClick={() => void list.loadOlder()}>
            {t('chat.loadOlder')}
          </Button>
        </div>
      )}
      {!messages.length && <p className="py-16 text-center text-sm text-gray-400">{t('chat.emptyRoom')}</p>}
      {messages.map((m, i) => {
        const prev = messages[i - 1]
        const at = new Date(m.createdAt)
        const newDay = !prev || !isSameDay(new Date(prev.createdAt), at)
        const grouped =
          !newDay && prev?.type !== 'SYSTEM' && m.type !== 'SYSTEM' && prev.sender?.id === m.sender?.id && at.getTime() - new Date(prev.createdAt).getTime() < 5 * 60_000
        return (
          <Fragment key={m.id}>
            {newDay && <DayDivider date={at} />}
            <MessageItem item={{ message: m, grouped }} />
          </Fragment>
        )
      })}
    </div>
  )
}
