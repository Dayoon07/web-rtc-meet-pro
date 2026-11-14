export type DesktopNotice = {
  title: string
  body: string
  tag: string
  link: string
}

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
  }
  const n = new Notification(notice.title, options)
  n.onclick = () => {
    window.focus()
    onClickFallback?.(notice.link)
    n.close()
  }
}

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
