import { ArrowLeft, Bell, BellOff, Headphones, Megaphone, PhoneOff, Settings2, UserPlus } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { type ChatRoom, roomTitle } from '@/entities/chat'
import {
  ChannelSettingsModal,
  EmojiPickerModal,
  InviteToRoomModal,
  LeaveRoomButton,
  MessageComposer,
  NoticeBar,
  NoticeModal,
  toolClass,
} from '@/features/chat-room-actions'
import { Button, IconButton, Spinner } from '@/shared/ui'
import { RoomContext, useRoom, useWorkspace } from '../model/context'
import { useTypingUsers } from '../model/typing-store'
import { type RoomViewModel, useRoomView } from '../model/use-room-view'
import { MessageList } from './MessageList'
import { RoomAvatar } from './RoomRow'

/** 방 머리: 뒤로(좁은 화면) · 이름 · (채널) 음성 참여 · 설정 · 초대 · 나가기 */
function RoomHeader() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const { room, category, voice, canManageChannel, dialogs, toggleMute } = useRoom()
  const live = room.voice?.participants.length ?? 0
  return (
    <div className="flex h-16 shrink-0 items-center gap-3 border-b border-gray-200 px-4 dark:border-gray-800">
      <IconButton label={t('common.back')} className="md:hidden" onClick={ws.backToList}>
        <ArrowLeft className="size-5" />
      </IconButton>
      <RoomAvatar room={room} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-gray-900 dark:text-white">{roomTitle(room, ws.myId)}</p>
        <p className="truncate text-xs text-gray-500">
          {room.type === 'GROUP' ? [category?.name, t('chat.participants', { count: room.members.length })].filter(Boolean).join(' · ') : t('chat.direct')}
        </p>
      </div>
      <IconButton label={t(room.muted ? 'chat.unmute' : 'chat.mute')} title={t(room.muted ? 'chat.unmute' : 'chat.mute')} onClick={() => void toggleMute()}>
        {room.muted ? <BellOff className="size-5 text-gray-400" /> : <Bell className="size-5" />}
      </IconButton>
      {room.type === 'GROUP' && (
        <>
          <Button
            size="sm"
            variant={voice.inThisVoice ? 'outline' : room.voice ? 'primary' : 'secondary'}
            icon={voice.inThisVoice ? <PhoneOff className="size-4" /> : <Headphones className="size-4" />}
            onClick={voice.toggle}
            loading={voice.connecting}
            title={t(voice.inThisVoice ? 'voice.leave' : 'chat.voiceHint')}
          >
            <span className="hidden xl:inline">
              {voice.inThisVoice ? t('voice.leave') : room.voice ? t('chat.voiceJoinLive', { count: live }) : t('chat.voiceJoin')}
            </span>
          </Button>
          {canManageChannel && (
            <IconButton label={t('chat.channelSettings')} onClick={() => dialogs.settings.show()}>
              <Settings2 className="size-5" />
            </IconButton>
          )}
          <Button variant="outline" size="sm" icon={<UserPlus className="size-4" />} onClick={() => dialogs.invite.show()}>
            <span className="hidden xl:inline">{t('chat.inviteMembers')}</span>
          </Button>
          <LeaveRoomButton room={room} onLeft={ws.backToList} />
        </>
      )}
    </div>
  )
}

/** 입력창 위 "○○님이 입력 중..." (점 세 개가 움직인다) */
function TypingIndicator() {
  const { t } = useTranslation()
  const { room } = useRoom()
  const users = useTypingUsers(room.id)
  return (
    <p aria-live="polite" className="h-5 shrink-0 px-5 text-xs text-gray-500 dark:text-gray-400">
      {users.length > 0 && (
        <span className="inline-flex items-center gap-1.5">
          <span className="flex gap-0.5">
            {[0, 150, 300].map((d) => (
              <span key={d} className="size-1 animate-bounce rounded-full bg-gray-400" style={{ animationDelay: `${d}ms` }} />
            ))}
          </span>
          {users.length === 1 ? t('chat.typing.one', { name: users[0].nickname }) : t('chat.typing.many', { name: users[0].nickname, count: users.length - 1 })}
        </span>
      )}
    </p>
  )
}

/** 입력칸 아래 "공지" 버튼 (공지를 올릴 수 있는 사람만) */
function NoticeTool() {
  const { t } = useTranslation()
  const { canNotice, dialogs } = useRoom()
  if (!canNotice) return null
  return (
    <button type="button" onClick={() => dialogs.notice.show()} aria-label={t('chat.notice.title')} title={t('chat.notice.title')} className={toolClass}>
      <Megaphone className="size-4" />
      <span className="hidden sm:inline">{t('chat.notice.button')}</span>
    </button>
  )
}

/** 주소에 ?msg=메시지 가 있으면 (검색 결과에서 왔을 때) 메시지를 다 불러온 뒤 그 메시지로 간다 */
function useJumpFromAddress(view: RoomViewModel) {
  const [params, setParams] = useSearchParams()
  const target = Number(params.get('msg')) || null
  const ready = !view.messages.isPending
  const { jumpTo } = view.messages
  useEffect(() => {
    if (!target || !ready) return
    void jumpTo(target).finally(() => {
      params.delete('msg')
      setParams(params, { replace: true })
    })
    // 주소의 msg 가 바뀔 때만 (jumpTo 는 렌더마다 새로 만들어진다)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, ready])
}

/** 열린 방 하나: 머리 · 공지 · 메시지 · 입력창 + 창들 */
export function RoomView({ room }: { room: ChatRoom }) {
  const view = useRoomView(room)
  useJumpFromAddress(view)
  const { dialogs, canNotice, canManageChannel } = view
  return (
    <RoomContext value={view}>
      <div className="flex min-h-0 flex-1 flex-col">
        <RoomHeader />
        <NoticeBar key={room.id} room={room} onEdit={canNotice ? () => dialogs.notice.show() : undefined} />
        {view.messages.isPending ? (
          <div className="flex flex-1 items-center justify-center">
            <Spinner className="size-6 text-gray-400" />
          </div>
        ) : (
          <MessageList />
        )}
        <TypingIndicator />
        <div className="shrink-0 border-t border-gray-200 p-3 sm:p-4 dark:border-gray-800">
          <MessageComposer key={room.id} room={room} tools={<NoticeTool />} />
        </div>
        <InviteToRoomModal room={room} dialog={dialogs.invite} />
        {canNotice && <NoticeModal room={room} dialog={dialogs.notice} />}
        {canManageChannel && <ChannelSettingsModal room={room} dialog={dialogs.settings} />}
        <EmojiPickerModal dialog={dialogs.reaction} />
      </div>
    </RoomContext>
  )
}
