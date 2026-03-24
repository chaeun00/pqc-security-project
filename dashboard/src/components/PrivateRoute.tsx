import { Navigate, Outlet } from 'react-router-dom'
import { useAppStore } from '@/store/useAppStore'

export default function PrivateRoute() {
  const token = useAppStore((s) => s.token)
  return token ? <Outlet /> : <Navigate to="/login" replace />
}
