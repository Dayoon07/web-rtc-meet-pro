import { ArrowRight, BellRing, Hand, MessageSquare, Mic, MicOff, MonitorUp, PhoneOff, ShieldCheck, Video } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useIsAuthenticated } from '@/entities/session'
import { cn } from '@/shared/lib/cn'
import { Avatar, LinkButton } from '@/shared/ui'

const TILES = [
  { name: 'Minji Kim', bg: 'from-violet-400 to-fuchsia-400', speaking: true },
  { name: 'Alex Lee', bg: 'from-sky-400 to-indigo-400', muted: true },
  { name: 'Seojun Park', bg: 'from-amber-300 to-orange-400' },
  { name: 'Haneul Lee', bg: 'from-emerald-300 to-teal-400', muted: true },
]

function ControlButton({ children, danger, active }: { children: ReactNode; danger?: boolean; active?: boolean }) {
  return (
    <span
      className={cn(
        'flex size-10 items-center justify-center rounded-full [&_svg]:size-4.5',
        danger ? 'w-14 bg-red-500 text-white' : active ? 'bg-brand-500 text-white' : 'bg-gray-700 text-gray-100',
      )}
    >
      {children}
    </span>
  )
}

/** Google Meet / Zoom 처럼 누구나 알아보는 회의 화면 미리보기 */
function MeetingPreview() {
  const { t } = useTranslation()
  return (
    <div className="relative" aria-hidden>
      <div className="absolute -inset-6 -z-10 rounded-[2rem] bg-gradient-to-br from-brand-200/60 via-brand-100/40 to-transparent blur-2xl dark:from-brand-700/30 dark:via-brand-900/20" />
      <div className="overflow-hidden rounded-2xl border border-gray-200 bg-gray-900 shadow-2xl shadow-brand-900/10 dark:border-gray-800">
        <div className="flex items-center gap-2 border-b border-white/5 px-4 py-3">
          <span className="size-3 rounded-full bg-red-400" />
          <span className="size-3 rounded-full bg-amber-400" />
          <span className="size-3 rounded-full bg-emerald-400" />
          <span className="ml-3 text-xs text-gray-400">{t('home.previewTitle')}</span>
          <span className="ml-auto flex items-center gap-1 rounded-full bg-red-500/15 px-2 py-0.5 text-[10px] font-semibold text-red-300">
            <span className="size-1.5 rounded-full bg-red-400" /> REC
          </span>
        </div>
        <div className="grid grid-cols-2 gap-2 p-2">
          {TILES.map((tile) => (
            <div
              key={tile.name}
              className={cn('relative flex aspect-video items-center justify-center rounded-xl bg-gradient-to-br', tile.bg, tile.speaking && 'ring-3 ring-brand-400')}
            >
              <Avatar name={tile.name} size="lg" className="bg-white/90 text-gray-800 shadow-md" />
              <span className="absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-black/45 px-2 py-0.5 text-[11px] font-medium text-white">
                {tile.muted ? <MicOff className="size-3" /> : <Mic className="size-3" />}
                {tile.name}
              </span>
            </div>
          ))}
        </div>
        <div className="flex items-center justify-center gap-2.5 px-4 pt-1 pb-4">
          <ControlButton>
            <Mic />
          </ControlButton>
          <ControlButton>
            <Video />
          </ControlButton>
          <ControlButton active>
            <MonitorUp />
          </ControlButton>
          <ControlButton>
            <Hand />
          </ControlButton>
          <ControlButton>
            <MessageSquare />
          </ControlButton>
          <ControlButton danger>
            <PhoneOff />
          </ControlButton>
        </div>
      </div>
    </div>
  )
}

export function HomePage() {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
  const primary = isAuthenticated ? { to: '/app', label: t('home.ctaWorkspace') } : { to: '/signup', label: t('home.ctaStart') }
  const features = [
    { icon: <Video />, ...t('home.features.video', { returnObjects: true }) },
    { icon: <MessageSquare />, ...t('home.features.chat', { returnObjects: true }) },
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
        <MeetingPreview />
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
