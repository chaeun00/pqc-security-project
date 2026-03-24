import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { describe, it, expect, beforeEach } from 'vitest'
import AppLayout from '@/components/AppLayout'
import { useAppStore } from '@/store/useAppStore'

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<div>Dashboard</div>} />
        </Route>
        <Route path="/login" element={<div>Login</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppLayout', () => {
  beforeEach(() => {
    useAppStore.setState({ token: 'test-token' })
  })

  it('로그아웃 버튼 클릭 시 clearToken 호출 + /login 이동', () => {
    renderLayout()
    fireEvent.click(screen.getByRole('button', { name: /로그아웃/ }))
    expect(useAppStore.getState().token).toBeNull()
    expect(screen.getByText('Login')).toBeInTheDocument()
  })
})
