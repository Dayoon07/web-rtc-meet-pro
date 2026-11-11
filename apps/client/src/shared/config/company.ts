import { SUPPORT_EMAIL } from './env'

/**
 * 사업자 정보 (전자상거래법 제10조·제13조, 정보통신망법: 사이버몰에 표시해야 하는 정보).
 * 운영 전에 반드시 apps/client/.env(.production) 에 채운다. 비어 있으면 화면에 "입력 필요" 로 보인다.
 */
const env = import.meta.env
const need = (v: string | undefined) => v?.trim() || '(입력 필요)'

export const COMPANY = {
  /** 상호 */
  name: need(env.VITE_COMPANY_NAME),
  /** 대표자 */
  ceo: need(env.VITE_COMPANY_CEO),
  /** 사업자등록번호 */
  bizNo: need(env.VITE_COMPANY_BIZ_NO),
  /** 통신판매업 신고번호 (유료 결제를 받으면 필수) */
  mailOrderNo: need(env.VITE_COMPANY_MAIL_ORDER_NO),
  /** 사업장 주소 */
  address: need(env.VITE_COMPANY_ADDRESS),
  /** 고객센터 전화 */
  phone: need(env.VITE_COMPANY_PHONE),
  email: SUPPORT_EMAIL,
  /** 호스팅 서비스 제공자 (전자상거래법 제10조) */
  hosting: env.VITE_COMPANY_HOSTING?.trim() || '스마일서브(iwinv)',
  /** 개인정보 보호책임자 (개인정보 보호법 제31조) */
  privacyOfficer: need(env.VITE_PRIVACY_OFFICER),
  privacyOfficerContact: env.VITE_PRIVACY_OFFICER_CONTACT?.trim() || SUPPORT_EMAIL,
  /** 개인정보처리방침·이용약관 시행일 */
  policyDate: env.VITE_POLICY_DATE?.trim() || '2026-11-12',
}
