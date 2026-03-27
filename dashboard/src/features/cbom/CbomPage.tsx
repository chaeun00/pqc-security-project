import { Fragment, useState } from 'react'
import { useCbomList } from './useCbomList'
import CbomPriorityView from './CbomPriorityView'
import AlgorithmSwitchPanel from './AlgorithmSwitchPanel'

const PAGE_SIZE = 10

const RISK_BADGE: Record<string, string> = {
  HIGH: 'bg-red-100 text-red-700',
  MEDIUM: 'bg-yellow-100 text-yellow-700',
  LOW: 'bg-green-100 text-green-700',
}
const RISK_BADGE_DEFAULT = 'bg-gray-100 text-gray-600'

type ViewTab = 'list' | 'priority'

export default function CbomPage() {
  const { data, isLoading, isError } = useCbomList()
  const [algorithmFilter, setAlgorithmFilter] = useState('')
  const [page, setPage] = useState(1)
  const [viewTab, setViewTab] = useState<ViewTab>('list')
  const [switchTarget, setSwitchTarget] = useState<{ id: string; algorithm: string } | null>(null)

  if (isLoading) return <div className="p-4">로딩 중...</div>
  if (isError) return <div className="p-4 text-red-600">데이터를 불러오지 못했습니다.</div>

  const algorithms = Array.from(new Set((data ?? []).map((e) => e.algorithm))).sort()

  const filtered = (data ?? []).filter(
    (e) => !algorithmFilter || e.algorithm === algorithmFilter,
  )

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const paged = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  function handleFilterChange(value: string) {
    setAlgorithmFilter(value)
    setPage(1)
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">CBOM 목록</h1>

      {/* 뷰 탭 전환 */}
      <div className="flex gap-2 mb-6 border-b" role="tablist" aria-label="뷰 전환">
        <button
          role="tab"
          aria-selected={viewTab === 'list'}
          onClick={() => setViewTab('list')}
          className={`px-4 py-2 font-medium border-b-2 transition-colors ${
            viewTab === 'list'
              ? 'text-blue-700 border-blue-500'
              : 'border-transparent text-gray-500'
          }`}
        >
          목록
        </button>
        <button
          role="tab"
          aria-selected={viewTab === 'priority'}
          onClick={() => setViewTab('priority')}
          className={`px-4 py-2 font-medium border-b-2 transition-colors ${
            viewTab === 'priority'
              ? 'text-blue-700 border-blue-500'
              : 'border-transparent text-gray-500'
          }`}
        >
          우선순위
        </button>
      </div>

      {viewTab === 'priority' ? (
        <CbomPriorityView data={data ?? []} />
      ) : (
        <>
          <div className="mb-4">
            <label htmlFor="algorithm-filter" className="mr-2 font-medium">알고리즘 필터:</label>
            <select
              id="algorithm-filter"
              value={algorithmFilter}
              onChange={(e) => handleFilterChange(e.target.value)}
              className="border rounded px-2 py-1"
            >
              <option value="">전체</option>
              {algorithms.map((alg) => (
                <option key={alg} value={alg}>{alg}</option>
              ))}
            </select>
            <span className="ml-4 text-sm text-gray-500">총 {filtered.length}건</span>
          </div>

          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-gray-100 text-left">
                <th className="border px-3 py-2">ID</th>
                <th className="border px-3 py-2">알고리즘</th>
                <th className="border px-3 py-2">타입</th>
                <th className="border px-3 py-2">위험도</th>
                <th className="border px-3 py-2">등록일</th>
                <th className="border px-3 py-2">알고리즘 전환</th>
              </tr>
            </thead>
            <tbody>
              {paged.map((entry) => (
                <Fragment key={entry.id}>
                  <tr className="hover:bg-gray-50">
                    <td className="border px-3 py-2">{entry.id}</td>
                    <td className="border px-3 py-2">{entry.algorithm}</td>
                    <td className="border px-3 py-2">{entry.type}</td>
                    <td className="border px-3 py-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${RISK_BADGE[entry.risk_level] ?? RISK_BADGE_DEFAULT}`}>
                        {entry.risk_level}
                      </span>
                    </td>
                    <td className="border px-3 py-2">{entry.registered_at ? entry.registered_at.slice(0, 10) : '—'}</td>
                    <td className="border px-3 py-2">
                      <button
                        onClick={() =>
                          setSwitchTarget(
                            switchTarget?.id === String(entry.id) ? null : { id: String(entry.id), algorithm: entry.algorithm },
                          )
                        }
                        className="px-2 py-0.5 border rounded text-xs"
                        aria-label={`알고리즘 전환 ${entry.id}`}
                      >
                        전환
                      </button>
                    </td>
                  </tr>
                  {switchTarget?.id === String(entry.id) && (
                    <tr>
                      <td colSpan={6} className="border px-3 py-2">
                        <AlgorithmSwitchPanel
                          assetId={switchTarget.id}
                          currentAlgorithm={switchTarget.algorithm}
                          onClose={() => setSwitchTarget(null)}
                        />
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>

          <div className="mt-4 flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-3 py-1 border rounded disabled:opacity-40"
            >
              이전
            </button>
            <span>{page} / {totalPages}</span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page === totalPages}
              className="px-3 py-1 border rounded disabled:opacity-40"
            >
              다음
            </button>
          </div>
        </>
      )}
    </div>
  )
}
