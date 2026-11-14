import 'i18next'
import type ko from './locales/ko'

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation'
    resources: { translation: typeof ko }
  }
}
