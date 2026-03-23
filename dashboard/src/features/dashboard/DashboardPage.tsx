import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { encrypt } from '@/api/encrypt'

export default function DashboardPage() {
  const [plaintext, setPlaintext] = useState('')

  const mutation = useMutation({ mutationFn: encrypt })

  const handleEncrypt = () => {
    if (plaintext.trim()) mutation.mutate({ plaintext })
  }

  return (
    <div style={{ padding: '2rem', fontFamily: 'monospace' }}>
      <h1>PQC Dashboard</h1>
      <div>
        <input
          type="text"
          value={plaintext}
          onChange={(e) => setPlaintext(e.target.value)}
          placeholder="plaintext..."
          style={{ width: '300px', marginRight: '8px' }}
        />
        <button onClick={handleEncrypt} disabled={mutation.isPending}>
          {mutation.isPending ? '암호화 중...' : '암호화'}
        </button>
      </div>

      {mutation.isError && (
        <p style={{ color: 'red' }}>
          오류: {(mutation.error as Error).message}
        </p>
      )}

      {mutation.isSuccess && (
        <pre style={{ background: '#f0f0f0', padding: '1rem', marginTop: '1rem' }}>
          {JSON.stringify(
            {
              key_id: mutation.data.key_id,
              algorithm: mutation.data.algorithm,
              risk_level: mutation.data.risk_level,
            },
            null,
            2,
          )}
        </pre>
      )}
    </div>
  )
}
