import { createBrowserRouter } from 'react-router'
import { AppHomePage } from '@/pages/app-home'
import { BillingPage } from '@/pages/billing'
import { ChatPage } from '@/pages/chat'
import { HomePage } from '@/pages/home'
import { InvitationsPage } from '@/pages/invitations'
import { LoginPage } from '@/pages/login'
import { MeetingDetailPage } from '@/pages/meeting-detail'
import { MeetingsPage } from '@/pages/meetings'
import { NotFoundPage } from '@/pages/not-found'
import { OAuthCallbackPage } from '@/pages/oauth-callback'
import { OnboardingPage } from '@/pages/onboarding'
import { ForgotPasswordPage, ResetPasswordPage } from '@/pages/password-reset'
import { PricingPage } from '@/pages/pricing'
import { RouteErrorPage } from '@/pages/route-error'
import { SettingsPage } from '@/pages/settings'
import { SignupPage } from '@/pages/signup'
import { TeamDetailPage } from '@/pages/team-detail'
import { TeamsPage } from '@/pages/teams'
import { ConfirmEmailPage, VerifyEmailPage } from '@/pages/verify-email'
import { GuestOnly, OnboardingOnly, RequireAuth, RequireCompletedProfile, VerifyEmailOnly } from './guards'
import { AppLayout, AuthLayout, MarketingLayout } from './layouts'

/** handle.title 은 번역 키 (AppLayout 이 현재 언어로 표시) */
export const router = createBrowserRouter([
  {
    // 어느 화면에서든 처리 못 한 에러는 이 화면으로 (React Router 기본 개발자 화면 대신)
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <MarketingLayout />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/pricing', element: <PricingPage /> },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          {
            element: <GuestOnly />,
            children: [
              { path: '/login', element: <LoginPage /> },
              { path: '/signup', element: <SignupPage /> },
              { path: '/forgot-password', element: <ForgotPasswordPage /> },
            ],
          },
          // 메일 링크로 들어온다. 다른 계정으로 로그인한 상태여도 열 수 있게 GuestOnly 밖에 둔다
          { path: '/reset-password', element: <ResetPasswordPage /> },
          { path: '/verify-email/confirm', element: <ConfirmEmailPage /> },
        ],
      },
      { path: '/oauth/callback', element: <OAuthCallbackPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { element: <OnboardingOnly />, children: [{ path: '/onboarding', element: <OnboardingPage /> }] },
              { element: <VerifyEmailOnly />, children: [{ path: '/verify-email', element: <VerifyEmailPage /> }] },
            ],
          },
          {
            element: <RequireCompletedProfile />,
            children: [
              // 회의실(mediasoup-client, socket.io)은 들어갈 때만 내려받는다
              { path: '/meet/:meetingId', lazy: async () => ({ Component: (await import('@/pages/meet')).MeetPage }) },
              {
                path: '/app',
                element: <AppLayout />,
                children: [
                  { index: true, element: <AppHomePage />, handle: { title: 'nav.home' } },
                  { path: 'chat/:teamId?/:roomId?', element: <ChatPage />, handle: { title: 'nav.chat' } },
                  { path: 'meetings', element: <MeetingsPage />, handle: { title: 'nav.meetings' } },
                  { path: 'meetings/:meetingId', element: <MeetingDetailPage />, handle: { title: 'nav.meetings' } },
                  { path: 'teams', element: <TeamsPage />, handle: { title: 'nav.teams' } },
                  { path: 'teams/:teamId', element: <TeamDetailPage />, handle: { title: 'nav.teams' } },
                  { path: 'invitations', element: <InvitationsPage />, handle: { title: 'nav.invitations' } },
                  { path: 'billing', element: <BillingPage />, handle: { title: 'nav.billing' } },
                  { path: 'settings', element: <SettingsPage />, handle: { title: 'nav.settings' } },
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
