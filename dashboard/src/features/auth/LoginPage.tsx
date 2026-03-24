import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { login } from '@/api/auth'
import { useAppStore } from '@/store/useAppStore'

export default function LoginPage() {
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')
  const setToken = useAppStore((s) => s.setToken)
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      setToken(data.token)
      navigate('/dashboard', { replace: true })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (userId.trim() && password.trim()) {
      mutation.mutate({ userId, password })
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#f8fafc', fontFamily: 'monospace' }}>
      <form onSubmit={handleSubmit} style={{ background: '#fff', padding: '2rem', borderRadius: '8px', boxShadow: '0 2px 8px rgba(0,0,0,0.1)', minWidth: '320px' }}>
        <h2 style={{ marginTop: 0 }}>PQC 로그인</h2>

        <div style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', marginBottom: '0.3rem' }}>아이디</label>
          <input
            type="text"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="admin"
            style={{ width: '100%', padding: '0.5rem', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', marginBottom: '0.3rem' }}>비밀번호</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="password"
            style={{ width: '100%', padding: '0.5rem', boxSizing: 'border-box' }}
          />
        </div>

        {mutation.isError && (
          <p style={{ color: 'red', margin: '0.5rem 0' }}>
            로그인 실패: {((mutation.error as { response?: { data?: { message?: string } } })?.response?.data?.message) ?? (mutation.error as Error).message}
          </p>
        )}

        <button
          type="submit"
          disabled={mutation.isPending}
          style={{ width: '100%', padding: '0.6rem', background: '#0ea5e9', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
        >
          {mutation.isPending ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </div>
  )
}
