import { createBrowserRouter } from 'react-router'
import { AppHomePage } from '@/pages/app-home'
import { BillingPage, CardReturnPage } from '@/pages/billing'
import { ChatPage } from '@/pages/chat'
import { HomePage } from '@/pages/home'
import { LicensesPage, PrivacyPage, TermsPage } from '@/pages/legal'
import { InvitationsPage } from '@/pages/invitations'
import { LoginPage } from '@/pages/login'
import { MeetingDetailPage } from '@/pages/meeting-detail'
import { MeetingsPage } from '@/pages/meetings'
import { NotFoundPage } from '@/pages/not-found'
import { OAuthCallbackPage } from '@/pages/oauth-callback'
import { OnboardingPage } from '@/pages/onboarding'
import { TeamStartPage } from '@/pages/team-start'
import { ForgotPasswordPage, ResetPasswordPage } from '@/pages/password-reset'
import { PricingPage } from '@/pages/pricing'
import { RouteErrorPage } from '@/pages/route-error'
import { SettingsPage } from '@/pages/settings'
import { SignupPage } from '@/pages/signup'
import { TeamDetailPage } from '@/pages/team-detail'
import { TeamsPage } from '@/pages/teams'
import { ConfirmEmailPage, VerifyEmailPage } from '@/pages/verify-email'
import { PlanExpiryGate } from '@/widgets/plan-expiry-notice'
import { GuestOnly, OnboardingOnly, RequireAuth, RequireCompletedProfile, RequireTeam, VerifyEmailOnly } from './guards'
import { AppLayout, AuthLayout, MarketingLayout } from './layouts'
import { APP_CHILD_ROUTES, ROUTES } from '@/shared/config/routes'

/** handle.title 은 번역 키 (AppLayout 이 현재 언어로 표시) */
export const router = createBrowserRouter([
  {
    // 어느 화면에서든 처리 못 한 에러는 이 화면으로 (React Router 기본 개발자 화면 대신)
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <MarketingLayout />,
        children: [
          { path: ROUTES.home, element: <HomePage /> },
          { path: ROUTES.pricing, element: <PricingPage /> },
          { path: ROUTES.terms, element: <TermsPage /> },
          { path: ROUTES.privacy, element: <PrivacyPage /> },
          { path: ROUTES.licenses, element: <LicensesPage /> },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          {
            element: <GuestOnly />,
            children: [
              { path: ROUTES.login, element: <LoginPage /> },
              { path: ROUTES.signup, element: <SignupPage /> },
              { path: ROUTES.forgotPassword, element: <ForgotPasswordPage /> },
            ],
          },
          // 메일 링크로 들어온다. 다른 계정으로 로그인한 상태여도 열 수 있게 GuestOnly 밖에 둔다
          { path: ROUTES.resetPassword, element: <ResetPasswordPage /> },
          { path: ROUTES.verifyEmailConfirm, element: <ConfirmEmailPage /> },
        ],
      },
      { path: ROUTES.oauthCallback, element: <OAuthCallbackPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { element: <OnboardingOnly />, children: [{ path: ROUTES.onboarding, element: <OnboardingPage /> }] },
              { element: <VerifyEmailOnly />, children: [{ path: ROUTES.verifyEmail, element: <VerifyEmailPage /> }] },
            ],
          },
          {
            element: <RequireCompletedProfile />,
            children: [
              // 회의실(mediasoup-client, socket.io)은 들어갈 때만 내려받는다
              { path: ROUTES.meetPattern, lazy: async () => ({ Component: (await import('@/pages/meet')).MeetPage }) },
              {
                path: ROUTES.app,
                element: <AppLayout />,
                children: [
                  {
                    // 플랜이 만료되면 앱 화면 위에 안내 창
                    element: <PlanExpiryGate />,
                    children: [
                  // 팀이 없어도 되는 화면: 시작(팀 만들기·초대 수락) · 받은 초대 · 설정(탈퇴 등)
                  { path: APP_CHILD_ROUTES.start, element: <TeamStartPage />, handle: { title: 'start.title' } },
                  { path: APP_CHILD_ROUTES.invitations, element: <InvitationsPage />, handle: { title: 'nav.invitations' } },
                  { path: APP_CHILD_ROUTES.settings, element: <SettingsPage />, handle: { title: 'nav.settings' } },
                  {
                    // 나머지는 팀이 있어야: 없으면 시작 화면으로
                    element: <RequireTeam />,
                    children: [
                      { index: true, element: <AppHomePage />, handle: { title: 'nav.home' } },
                      { path: APP_CHILD_ROUTES.chat, element: <ChatPage />, handle: { title: 'nav.chat' } },
                      { path: APP_CHILD_ROUTES.meetings, element: <MeetingsPage />, handle: { title: 'nav.meetings' } },
                      { path: APP_CHILD_ROUTES.meeting, element: <MeetingDetailPage />, handle: { title: 'nav.meetings' } },
                      { path: APP_CHILD_ROUTES.teams, element: <TeamsPage />, handle: { title: 'nav.teams' } },
                      { path: APP_CHILD_ROUTES.team, element: <TeamDetailPage />, handle: { title: 'nav.teams' } },
                      { path: APP_CHILD_ROUTES.billing, element: <BillingPage />, handle: { title: 'nav.billing' } },
                      { path: APP_CHILD_ROUTES.billingCard, element: <CardReturnPage />, handle: { title: 'nav.billing' } },
                    ],
                  },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
      { element: <MarketingLayout />, children: [{ path: '*', element: <NotFoundPage /> }] },
    ],
  },
])
