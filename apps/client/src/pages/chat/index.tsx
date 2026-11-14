import { ChatWorkspace } from '@/widgets/chat-workspace'

export function ChatPage() {
  return (
    <div className="-mx-4 -my-8 flex h-[calc(100dvh-4rem)] flex-col sm:-mx-6 lg:-mx-10">
      <ChatWorkspace />
    </div>
  )
}
