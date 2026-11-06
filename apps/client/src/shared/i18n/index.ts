import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'
import en from './locales/en'
import ja from './locales/ja'
import ko from './locales/ko'
import zh from './locales/zh'

export const LANGUAGES = ['ko', 'en', 'ja', 'zh'] as const
export type Language = (typeof LANGUAGES)[number]

/** Intl API 에 넘길 로케일 (중국어는 간체) */
export const INTL_LOCALE: Record<Language, string> = { ko: 'ko-KR', en: 'en-US', ja: 'ja-JP', zh: 'zh-CN' }

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      ko: { translation: ko },
      en: { translation: en },
      ja: { translation: ja },
      zh: { translation: zh },
    },
    supportedLngs: [...LANGUAGES],
    nonExplicitSupportedLngs: true, // zh-CN, en-US → zh, en
    load: 'languageOnly',
    fallbackLng: 'en',
    interpolation: { escapeValue: false }, // React 가 이미 이스케이프
    detection: {
      // 사용자가 고른 언어(localStorage) → 브라우저 언어 순
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'wrmp-lang',
      caches: ['localStorage'],
    },
  })

const syncHtmlLang = (lng: string) => {
  document.documentElement.lang = lng
}
syncHtmlLang(i18n.resolvedLanguage ?? 'en')
i18n.on('languageChanged', syncHtmlLang)

export const currentLanguage = (): Language =>
  LANGUAGES.find((l) => (i18n.resolvedLanguage ?? i18n.language)?.startsWith(l)) ?? 'en'

export const intlLocale = () => INTL_LOCALE[currentLanguage()]

/** 서버 코드처럼 런타임에 정해지는 키 (타입 검사 밖). 없으면 fallback */
export function translateDynamic(key: string, params?: Record<string, unknown>, fallback?: string): string {
  return i18n.exists(key) ? String(i18n.t(key as never, params as never)) : (fallback ?? key)
}

export default i18n
