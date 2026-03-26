import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import CbomPriorityView from '@/features/cbom/CbomPriorityView'
import type { CbomEntry } from '@/api/cbom'

const mockData: CbomEntry[] = [
  { id: 1, algorithm: 'RSA-2048', type: 'Encryption', risk_level: 'HIGH', registered_at: '2026-03-01T00:00:00Z' },
  { id: 2, algorithm: 'SHA-1', type: 'Hash', risk_level: 'HIGH', registered_at: '2026-03-02T00:00:00Z' },
  { id: 3, algorithm: 'DES-56', type: 'Symmetric', risk_level: 'HIGH', registered_at: '2026-03-03T00:00:00Z' },
  { id: 4, algorithm: 'ECDSA-P256', type: 'DSA', risk_level: 'MEDIUM', registered_at: '2026-03-04T00:00:00Z' },
  { id: 5, algorithm: 'AES-128', type: 'Symmetric', risk_level: 'MEDIUM', registered_at: '2026-03-05T00:00:00Z' },
  { id: 6, algorithm: 'ML-KEM-768', type: 'KEM', risk_level: 'LOW', registered_at: '2026-03-06T00:00:00Z' },
  { id: 7, algorithm: 'ML-DSA-44', type: 'DSA', risk_level: 'LOW', registered_at: '2026-03-07T00:00:00Z' },
  { id: 8, algorithm: 'ML-KEM-512', type: 'KEM', risk_level: 'LOW', registered_at: '2026-03-08T00:00:00Z' },
]

describe('CbomPriorityView', () => {
  it('HIGH 탭이 기본 활성이고 3건 표시', () => {
    render(<CbomPriorityView data={mockData} />)
    const highTab = screen.getByRole('tab', { name: /HIGH \(3\)/ })
    expect(highTab).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('RSA-2048')).toBeInTheDocument()
    expect(screen.getByText('SHA-1')).toBeInTheDocument()
    expect(screen.getByText('DES-56')).toBeInTheDocument()
  })

  it('MEDIUM 탭 클릭 시 2건 표시', () => {
    render(<CbomPriorityView data={mockData} />)
    fireEvent.click(screen.getByRole('tab', { name: /MEDIUM \(2\)/ }))
    expect(screen.getByText('ECDSA-P256')).toBeInTheDocument()
    expect(screen.getByText('AES-128')).toBeInTheDocument()
  })

  it('LOW 탭 클릭 시 3건 표시', () => {
    render(<CbomPriorityView data={mockData} />)
    fireEvent.click(screen.getByRole('tab', { name: /LOW \(3\)/ }))
    expect(screen.getByText('ML-KEM-768')).toBeInTheDocument()
    expect(screen.getByText('ML-DSA-44')).toBeInTheDocument()
    expect(screen.getByText('ML-KEM-512')).toBeInTheDocument()
  })

  it('해당 탭에 항목 없을 시 빈 상태 메시지 표시', () => {
    const onlyHighData: CbomEntry[] = [
      { id: 1, algorithm: 'RSA-2048', type: 'Encryption', risk_level: 'HIGH', registered_at: '2026-03-01T00:00:00Z' },
    ]
    render(<CbomPriorityView data={onlyHighData} />)
    fireEvent.click(screen.getByRole('tab', { name: /MEDIUM \(0\)/ }))
    expect(screen.getByText('해당 위험도 항목이 없습니다.')).toBeInTheDocument()
  })
})
