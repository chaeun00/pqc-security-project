import { useState } from 'react'
import type { CbomEntry } from '@/api/cbom'

type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

const RISK_TABS: RiskLevel[] = ['HIGH', 'MEDIUM', 'LOW']

const RISK_TAB_ACTIVE: Record<RiskLevel, string> = {
  HIGH: 'text-red-700 border-red-500',
  MEDIUM: 'text-yellow-700 border-yellow-500',
  LOW: 'text-green-700 border-green-500',
}

const RISK_CARD_STYLE: Record<RiskLevel, string> = {
  HIGH: 'border-red-300 bg-red-50',
  MEDIUM: 'border-yellow-300 bg-yellow-50',
  LOW: 'border-green-300 bg-green-50',
}

type Props = {
  data: CbomEntry[]
}

export default function CbomPriorityView({ data }: Props) {
  const [activeTab, setActiveTab] = useState<RiskLevel>('HIGH')

  const grouped: Record<RiskLevel, CbomEntry[]> = {
    HIGH: data.filter((e) => e.risk_level === 'HIGH'),
    MEDIUM: data.filter((e) => e.risk_level === 'MEDIUM'),
    LOW: data.filter((e) => e.risk_level === 'LOW'),
  }

  return (
    <div>
      <div className="flex gap-2 mb-4 border-b" role="tablist">
        {RISK_TABS.map((level) => (
          <button
            key={level}
            role="tab"
            aria-selected={activeTab === level}
            onClick={() => setActiveTab(level)}
            className={`px-4 py-2 font-medium border-b-2 transition-colors ${
              activeTab === level
                ? RISK_TAB_ACTIVE[level]
                : 'border-transparent text-gray-500'
            }`}
          >
            {level} ({grouped[level].length})
          </button>
        ))}
      </div>

      <div className="grid gap-3">
        {grouped[activeTab].length === 0 ? (
          <p className="text-gray-500 text-sm">해당 위험도 항목이 없습니다.</p>
        ) : (
          grouped[activeTab].map((entry) => (
            <div key={entry.id} className={`border rounded p-3 ${RISK_CARD_STYLE[activeTab]}`}>
              <div className="font-medium">{entry.algorithm}</div>
              <div className="text-sm text-gray-600">
                {entry.type} · {entry.registered_at ? entry.registered_at.slice(0, 10) : '—'}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
