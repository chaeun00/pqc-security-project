import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom'
import LoginPage from '@/features/auth/LoginPage'
import DashboardPage from '@/features/dashboard/DashboardPage'
import CbomPage from '@/features/cbom/CbomPage'
import InventoryPage from '@/features/inventory/InventoryPage'
import MonitorPage from '@/features/monitor/MonitorPage'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/dashboard', element: <DashboardPage /> },
  { path: '/cbom', element: <CbomPage /> },
  { path: '/inventory', element: <InventoryPage /> },
  { path: '/monitor', element: <MonitorPage /> },
  { path: '/', element: <Navigate to="/dashboard" replace /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
