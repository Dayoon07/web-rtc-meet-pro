/*
 * MQ 서비스 워커: 브라우저 푸시 알림만 다룬다 (오프라인 캐시는 하지 않는다).
 * 서버(PushService)가 보내는 내용: { title, body, link, tag }
 */
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { title: 'MQ', body: event.data ? event.data.text() : '' }
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'MQ', {
      body: data.body || '',
      icon: '/favicon.svg',
      badge: '/favicon.svg',
      // 같은 방의 알림은 하나로 바꿔 끼운다 (쌓이지 않게)
      tag: data.tag || undefined,
      renotify: Boolean(data.tag),
      data: { link: data.link || '/app' },
    }),
  )
})

// 알림을 누르면: 열려 있는 MQ 창이 있으면 그 창에서 그 화면으로, 없으면 새 창
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const link = (event.notification.data && event.notification.data.link) || '/app'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((w) => w.url.startsWith(self.location.origin))
      if (open) {
        open.postMessage({ type: 'mq:navigate', link })
        return open.focus()
      }
      return self.clients.openWindow(link)
    }),
  )
})
