import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LayoutDashboard, ShieldCheck, Package, Activity, LogOut } from 'lucide-react'
import { useAppStore } from '@/store/useAppStore'

const navItems = [
  { to: '/dashboard', label: '대시보드', icon: LayoutDashboard },
  { to: '/cbom', label: 'CBOM', icon: ShieldCheck },
  { to: '/inventory', label: '인벤토리', icon: Package },
  { to: '/monitor', label: '모니터링', icon: Activity },
]

export default function AppLayout() {
  const clearToken = useAppStore((s) => s.clearToken)
  const navigate = useNavigate()

  const handleLogout = () => {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <div style={{ display: 'flex', height: '100vh', fontFamily: 'monospace' }}>
      {/* Sidebar */}
      <nav style={{ width: '200px', background: '#1e293b', color: '#f1f5f9', display: 'flex', flexDirection: 'column', padding: '1rem 0' }}>
        <div style={{ padding: '0 1rem 1.5rem', fontWeight: 'bold', fontSize: '1.1rem' }}>PQC Security</div>
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: '0.5rem',
              padding: '0.6rem 1rem', color: isActive ? '#38bdf8' : '#cbd5e1',
              textDecoration: 'none', background: isActive ? 'rgba(56,189,248,0.1)' : 'transparent',
            })}
          >
            <Icon size={16} />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Main */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Header */}
        <header style={{ height: '48px', background: '#0f172a', color: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'flex-end', padding: '0 1rem' }}>
          <button
            onClick={handleLogout}
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'none', border: '1px solid #475569', color: '#f1f5f9', padding: '0.3rem 0.8rem', cursor: 'pointer', borderRadius: '4px' }}
          >
            <LogOut size={14} />
            로그아웃
          </button>
        </header>

        {/* Content */}
        <main style={{ flex: 1, overflow: 'auto', padding: '1.5rem', background: '#f8fafc' }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
