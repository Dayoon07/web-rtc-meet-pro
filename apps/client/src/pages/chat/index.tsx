import { useNavigate, useParams } from 'react-router'
import { ChatWorkspace } from '@/widgets/chat-workspace'

/** /app/chat, /app/chat/:teamId, /app/chat/:teamId/:roomId */
export function ChatPage() {
  const params = useParams()
  const navigate = useNavigate()
  const teamId = params.teamId ? Number(params.teamId) : undefined
  const roomId = params.roomId ? Number(params.roomId) : undefined

  return (
    // 앱 레이아웃의 상단 바(4rem)와 패딩을 뺀 높이를 꽉 채운다
    <div className="-mx-4 -my-8 flex h-[calc(100dvh-4rem)] flex-col sm:-mx-6 md:mx-0 md:my-0 md:h-[calc(100dvh-8rem)]">
      <ChatWorkspace
        teamId={teamId}
        roomId={roomId}
        onNavigate={(team, room) => navigate(room ? `/app/chat/${team}/${room}` : `/app/chat/${team}`)}
      />
    </div>
  )
}
