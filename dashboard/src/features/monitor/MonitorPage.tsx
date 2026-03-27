import { useMonitor } from './useMonitor'

const RISK_CARD: Record<string, string> = {
  HIGH: 'bg-red-50 text-red-700',
  MEDIUM: 'bg-yellow-50 text-yellow-700',
  LOW: 'bg-green-50 text-green-700',
}

export default function MonitorPage() {
  const { data, isLoading, isError } = useMonitor()

  if (isLoading) return <div className="p-4">로딩 중...</div>
  if (isError) return <div className="p-4 text-red-600">데이터를 불러오지 못했습니다.</div>

  const entries = data ?? []
  const total = entries.length
  const highCount = entries.filter((e) => e.risk_level === 'HIGH').length
  const mediumCount = entries.filter((e) => e.risk_level === 'MEDIUM').length
  const lowCount = entries.filter((e) => e.risk_level === 'LOW').length

  const algCounts: Record<string, number> = {}
  for (const e of entries) {
    algCounts[e.algorithm] = (algCounts[e.algorithm] ?? 0) + 1
  }
  const maxAlgCount = Math.max(...Object.values(algCounts), 1)

  const recent5 = [...entries]
    .sort((a, b) => (b.registered_at ?? '').localeCompare(a.registered_at ?? ''))
    .slice(0, 5)

  return (
    <div className="p-6">
      <div className="flex items-center gap-3 mb-6">
        <h1 className="text-2xl font-bold">실시간 모니터링</h1>
      </div>

      {/* 통계 카드 4개 */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        <div className="p-4 bg-gray-50 rounded-lg border">
          <div className="text-sm text-gray-500 mb-1">전체</div>
          <div className="text-3xl font-bold">{total}</div>
        </div>
        {(['HIGH', 'MEDIUM', 'LOW'] as const).map((level) => {
          const count = level === 'HIGH' ? highCount : level === 'MEDIUM' ? mediumCount : lowCount
          return (
            <div key={level} className={`p-4 rounded-lg border ${RISK_CARD[level]}`}>
              <div className="text-sm mb-1">{level}</div>
              <div className="text-3xl font-bold">{count}</div>
            </div>
          )
        })}
      </div>

      {/* 알고리즘별 bar */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold mb-3">알고리즘별 분포</h2>
        <div className="space-y-2">
          {Object.entries(algCounts)
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([alg, count]) => (
              <div key={alg} className="flex items-center gap-3">
                <span className="text-sm w-32 truncate">{alg}</span>
                <div className="flex-1 bg-gray-100 rounded h-5 overflow-hidden">
                  <div
                    className="h-full bg-blue-500 rounded"
                    style={{ width: `${(count / maxAlgCount) * 100}%` }}
                    aria-label={`${alg} ${count}건`}
                  />
                </div>
                <span className="text-sm w-6 text-right">{count}</span>
              </div>
            ))}
        </div>
      </div>

      {/* 최근 이력 5건 */}
      <div>
        <h2 className="text-lg font-semibold mb-3">최근 이력 (5건)</h2>
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="bg-gray-100 text-left">
              <th className="border px-3 py-2">ID</th>
              <th className="border px-3 py-2">알고리즘</th>
              <th className="border px-3 py-2">위험도</th>
              <th className="border px-3 py-2">등록일</th>
            </tr>
          </thead>
          <tbody>
            {recent5.map((entry) => (
              <tr key={entry.id} className="hover:bg-gray-50">
                <td className="border px-3 py-2">{entry.id}</td>
                <td className="border px-3 py-2">{entry.algorithm}</td>
                <td className="border px-3 py-2">{entry.risk_level}</td>
                <td className="border px-3 py-2">
                  {entry.registered_at ? entry.registered_at.slice(0, 10) : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
