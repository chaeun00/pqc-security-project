import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { describe, it, expect, beforeEach } from 'vitest'
import PrivateRoute from '@/components/PrivateRoute'
import { useAppStore } from '@/store/useAppStore'

function renderWithRouter(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<div>Login</div>} />
        <Route element={<PrivateRoute />}>
          <Route path="/dashboard" element={<div>Dashboard</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('PrivateRoute', () => {
  beforeEach(() => {
    useAppStore.setState({ token: null })
  })

  it('token이 없으면 /login으로 리다이렉트', () => {
    renderWithRouter('/dashboard')
    expect(screen.getByText('Login')).toBeInTheDocument()
  })

  it('token이 있으면 protected 페이지 렌더링', () => {
    useAppStore.setState({ token: 'test-token' })
    renderWithRouter('/dashboard')
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })
})
