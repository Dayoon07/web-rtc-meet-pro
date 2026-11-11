import { ArrowRight, BellRing, Hash, Headphones, MessageSquare, Mic, NotebookPen, PhoneOff, ShieldCheck, Video, Volume2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useIsAuthenticated } from '@/entities/session'
import { cn } from '@/shared/lib/cn'
import { Avatar, LinkButton } from '@/shared/ui'

/** 채팅 화면 미리보기: 채널 목록 · 대화 · 채팅 속 음성 막대 · AI 회의록 카드 (실제 앱과 같은 배치) */
function ChatPreview() {
  const { t } = useTranslation()
  const messages = [
    { name: 'Minji Kim', text: t('home.preview.msg1'), mine: false },
    { name: 'Alex Lee', text: t('home.preview.msg2'), mine: false },
    { name: 'Me', text: t('home.preview.msg3'), mine: true },
  ]
  return (
    <div className="relative" aria-hidden>
      <div className="absolute -inset-6 -z-10 rounded-[2rem] bg-gradient-to-br from-brand-200/60 via-brand-100/40 to-transparent blur-2xl dark:from-brand-700/30 dark:via-brand-900/20" />
      <div className="flex overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl shadow-brand-900/10 dark:border-gray-800 dark:bg-gray-900">
        <div className="hidden w-40 shrink-0 flex-col border-r border-gray-100 bg-gray-50 sm:flex dark:border-gray-800 dark:bg-gray-950/60">
          <p className="px-3 pt-3 pb-2 text-[11px] font-semibold tracking-wide text-gray-400 uppercase">{t('home.previewTitle')}</p>
          {[t('home.preview.ch1'), t('home.preview.ch2'), t('home.preview.ch3')].map((c, i) => (
            <div key={c} className={cn('mx-2 rounded-lg px-2 py-1.5 text-xs', i === 0 ? 'bg-brand-50 font-semibold text-brand-700 dark:bg-brand-500/15 dark:text-brand-200' : 'text-gray-600 dark:text-gray-400')}>
              <span className="flex items-center gap-1.5">
                <Hash className="size-3.5" />
                {c}
              </span>
              {i === 0 && (
                <span className="mt-1 flex items-center gap-1 text-[10px] font-medium text-green-700 dark:text-green-400">
                  <Volume2 className="size-3" />
                  {t('home.preview.voiceNow')}
                </span>
              )}
            </div>
          ))}
          <div className="mt-auto border-t border-gray-200 p-2.5 dark:border-gray-800">
            <p className="flex items-center gap-1 text-[10px] font-semibold text-green-700 dark:text-green-400">
              <span className="size-1.5 rounded-full bg-green-500" />
              {t('voice.connected')}
            </p>
            <div className="mt-1.5 flex items-center gap-1">
              <span className="rounded-full ring-2 ring-green-500">
                <Avatar name="Minji Kim" size="xs" />
              </span>
              <Avatar name="Alex Lee" size="xs" />
              <span className="ml-auto flex gap-1 text-gray-500">
                <Mic className="size-3.5" />
                <PhoneOff className="size-3.5 text-red-500" />
              </span>
            </div>
          </div>
        </div>
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex items-center gap-2 border-b border-gray-100 px-4 py-2.5 dark:border-gray-800">
            <Hash className="size-4 text-brand-600" />
            <span className="text-sm font-semibold text-gray-900 dark:text-white">{t('home.preview.ch1')}</span>
            <span className="ml-auto flex items-center gap-1 rounded-lg bg-brand-600 px-2 py-1 text-[11px] font-semibold text-white">
              <Headphones className="size-3.5" />
              {t('home.preview.voiceJoin')}
            </span>
          </div>
          <div className="flex flex-1 flex-col gap-3 bg-gray-50/60 p-4 dark:bg-gray-950/30">
            {messages.map((m) => (
              <div key={m.name} className={cn('flex items-end gap-2', m.mine && 'flex-row-reverse')}>
                {!m.mine && <Avatar name={m.name} size="sm" />}
                <span
                  className={cn(
                    'max-w-[80%] rounded-2xl px-3 py-2 text-[13px] leading-relaxed',
                    m.mine ? 'rounded-br-md bg-brand-600 text-white' : 'rounded-bl-md bg-white text-gray-800 ring-1 ring-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-700',
                  )}
                >
                  {m.text}
                </span>
              </div>
            ))}
            <div className="rounded-xl border border-brand-200 bg-brand-50/70 px-3 py-2.5 dark:border-brand-500/30 dark:bg-brand-500/10">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold text-brand-700 dark:text-brand-300">
                <NotebookPen className="size-3.5" />
                {t('notes.cardLabel')}
              </p>
              <p className="mt-1 text-xs text-gray-700 dark:text-gray-300">• {t('home.preview.note')}</p>
            </div>
          </div>
          <div className="border-t border-gray-100 px-4 py-2.5 text-xs text-gray-400 dark:border-gray-800">{t('chat.placeholder')}</div>
        </div>
      </div>
    </div>
  )
}

export function HomePage() {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
  const primary = isAuthenticated ? { to: '/app/chat', label: t('home.ctaWorkspace') } : { to: '/signup', label: t('home.ctaStart') }
  const features = [
    { icon: <MessageSquare />, ...t('home.features.chat', { returnObjects: true }) },
    { icon: <Video />, ...t('home.features.video', { returnObjects: true }) },
    { icon: <BellRing />, ...t('home.features.schedule', { returnObjects: true }) },
    { icon: <ShieldCheck />, ...t('home.features.security', { returnObjects: true }) },
  ]
  const steps = [t('home.steps.signup', { returnObjects: true }), t('home.steps.team', { returnObjects: true }), t('home.steps.meet', { returnObjects: true })]

  return (
    <>
      <section className="mx-auto grid max-w-6xl items-center gap-14 px-4 pt-16 pb-20 sm:px-6 lg:grid-cols-[1.05fr_1fr] lg:pt-24">
        <div className="flex flex-col items-start gap-6">
          <span className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700 dark:border-brand-500/30 dark:bg-brand-500/10 dark:text-brand-200">
            <span className="size-1.5 rounded-full bg-brand-500" />
            {t('home.badge')}
          </span>
          <h1 className="text-4xl leading-[1.15] font-extrabold tracking-tight text-gray-900 sm:text-5xl lg:text-[3.4rem] dark:text-white">
            {t('home.titleLine1')}
            <br />
            <span className="bg-gradient-to-r from-brand-600 to-fuchsia-500 bg-clip-text text-transparent dark:from-brand-400 dark:to-fuchsia-400">
              {t('home.titleLine2')}
            </span>
          </h1>
          <p className="max-w-lg text-lg leading-relaxed text-gray-600 dark:text-gray-400">{t('home.subtitle')}</p>
          <div className="flex flex-wrap gap-3">
            <LinkButton to={primary.to} size="lg">
              {primary.label}
              <ArrowRight className="size-4" />
            </LinkButton>
            <LinkButton to="/pricing" variant="outline" size="lg">
              {t('home.ctaPricing')}
            </LinkButton>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-500">{t('home.note')}</p>
        </div>
        <ChatPreview />
      </section>

      <section id="features" className="scroll-mt-20 border-t border-gray-100 bg-gray-50/70 py-20 dark:border-gray-900 dark:bg-gray-900/30">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="mx-auto max-w-2xl text-center">
            <h2 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('home.featuresTitle')}</h2>
            <p className="mt-3 text-gray-600 dark:text-gray-400">{t('home.featuresSubtitle')}</p>
          </div>
          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((f) => (
              <div key={f.title} className="rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900">
                <span className="flex size-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300 [&_svg]:size-5.5">
                  {f.icon}
                </span>
                <h3 className="mt-4 font-semibold text-gray-900 dark:text-white">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-600 dark:text-gray-400">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
        <h2 className="text-center text-3xl font-bold tracking-tight text-gray-900 dark:text-white">{t('home.stepsTitle')}</h2>
        <ol className="mt-12 grid gap-6 md:grid-cols-3">
          {steps.map((s, i) => (
            <li key={s.title} className="flex gap-4">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-600 font-bold text-white">{i + 1}</span>
              <div>
                <p className="font-semibold text-gray-900 dark:text-white">{s.title}</p>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">{s.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {!isAuthenticated && (
        <section className="px-4 pb-20 sm:px-6">
          <div className="mx-auto flex max-w-6xl flex-col items-center gap-6 rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-6 py-14 text-center text-white">
            <h2 className="text-3xl font-bold tracking-tight">{t('home.ctaBandTitle')}</h2>
            <p className="text-brand-100">{t('home.ctaBandBody')}</p>
            <LinkButton to="/signup" size="lg" variant="inverse">
              {t('home.ctaStart')}
            </LinkButton>
          </div>
        </section>
      )}
    </>
  )
}
