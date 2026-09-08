import { BarChart3, LayoutDashboard, Receipt, Settings } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { cn } from '@/lib/utils'

const NAV_ITEMS = [
  { to: '/', label: '대시보드', icon: LayoutDashboard },
  { to: '/transactions', label: '거래 내역', icon: Receipt },
  { to: '/analytics', label: '통계·분석', icon: BarChart3 },
  { to: '/settings', label: '설정', icon: Settings },
] as const

export function AppLayout() {
  return (
    <div className="min-h-screen">
      <header className="border-b">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-lg font-bold">Beetle</h1>
            <p className="text-xs text-muted-foreground">개인 맞춤형 가계부</p>
          </div>

          <nav className="flex gap-1" aria-label="주요 화면">
            {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  cn(
                    'inline-flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-secondary text-secondary-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                  )
                }
              >
                <Icon className="size-4" />
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-5 py-6">
        <Outlet />
      </main>
    </div>
  )
}
