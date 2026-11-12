/**
 * 브라우저(크롬 등) 알림 띄우기. 서버 웹 푸시는 "접속 안 한" 사람에게만 가므로,
 * 접속해 있어도 MQ 탭을 안 보고 있을 때(다른 탭·다른 창·다른 프로그램)는 여기서 띄운다 (슬랙처럼).
 * 서비스 워커로 띄워서 알림을 누르면 sw.js 가 그 화면으로 보낸다 (mq:navigate).
 */

export type DesktopNotice = {
  title: string
  body: string
  /** 같은 tag 는 하나로 바꿔 끼운다 (서버 푸시와 같은 tag 를 쓰면 겹쳐 뜨지 않는다) */
  tag: string
  /** 누르면 갈 앱 안 주소 */
  link: string
}

/** 알림을 띄울 수 있고, 사용자가 MQ 를 안 보고 있을 때만 */
export function shouldNotifyDesktop(): boolean {
  if (!('Notification' in window) || Notification.permission !== 'granted') return false
  return document.visibilityState !== 'visible' || !document.hasFocus()
}

export async function showDesktopNotification(notice: DesktopNotice, onClickFallback?: (link: string) => void) {
  if (!shouldNotifyDesktop()) return
  const options: NotificationOptions & { renotify?: boolean } = {
    body: notice.body,
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    tag: notice.tag,
    renotify: true,
    data: { link: notice.link },
  }
  try {
    const registration = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : undefined
    if (registration) {
      await registration.showNotification(notice.title, options)
      return
    }
  } catch {
    // 서비스 워커가 없으면 아래 방법으로
  }
  const n = new Notification(notice.title, options)
  n.onclick = () => {
    window.focus()
    onClickFallback?.(notice.link)
    n.close()
  }
}

/** 방해 금지 시간인지 (서버 User.inQuietHours 와 같은 규칙: 'HH:mm' ~ 'HH:mm', 시작 ≥ 끝이면 다음 날까지, 시간대 없으면 Asia/Seoul) */
export function inQuietHours(start?: string | null, end?: string | null, timeZone?: string | null, now = new Date()): boolean {
  if (!start || !end) return false
  let hhmm: string
  try {
    hhmm = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: timeZone || 'Asia/Seoul' }).format(now)
  } catch {
    hhmm = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(now)
  }
  return start < end ? hhmm >= start && hhmm < end : hhmm >= start || hhmm < end
}
