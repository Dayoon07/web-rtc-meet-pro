import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { intlLocale } from '@/shared/i18n'

function createFormat(locale: string) {
  return {
    dateTime: (iso: string) =>
      new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso)),
    date: (iso: string) => new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(iso)),
    monthDay: (iso: string) => new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }).format(new Date(iso)),
    dayHeading: (d: Date) => new Intl.DateTimeFormat(locale, { month: 'long', day: 'numeric', weekday: 'long' }).format(d),
    longDate: (d: Date) => new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(d),
    time: (iso: string | Date) => new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(new Date(iso)),
    relative: (iso: string) => {
      const diff = (new Date(iso).getTime() - Date.now()) / 1000
      const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
      const abs = Math.abs(diff)
      if (abs < 60) return rtf.format(Math.round(diff), 'second')
      if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute')
      if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour')
      return rtf.format(Math.round(diff / 86400), 'day')
    },
    list: (items: string[]) => new Intl.ListFormat(locale, { style: 'short', type: 'conjunction' }).format(items),
    region: (code: string) => new Intl.DisplayNames([locale], { type: 'region' }).of(code) ?? code,
  }
}

export type Format = ReturnType<typeof createFormat>

/** 서버 시간(오프셋 포함 ISO)을 사용자 언어·시간대로 표시. 언어가 바뀔 때만 새로 만든다 */
export function useFormat(): Format {
  const { i18n } = useTranslation()
  const locale = intlLocale()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => createFormat(locale), [locale, i18n.language])
}

export const isSameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
