import { useState } from 'react'
import { ALLOWED_ALGORITHMS } from '@/api/algorithm'
import { useAlgorithmSwitch } from './useAlgorithmSwitch'

type Props = {
  assetId: string
  currentAlgorithm: string
  onClose: () => void
}

export default function AlgorithmSwitchPanel({ assetId, currentAlgorithm, onClose }: Props) {
  const [selected, setSelected] = useState('')
  const { mutate, isPending, isSuccess, isError } = useAlgorithmSwitch()

  const isAllowed = ALLOWED_ALGORITHMS.includes(selected)

  function handleSwitch() {
    if (!isAllowed) return
    mutate({ asset_id: assetId, algorithm: selected })
  }

  return (
    <div className="border rounded p-4 bg-white shadow-sm mt-2">
      <h3 className="font-semibold mb-2">알고리즘 전환</h3>
      <p className="text-sm text-gray-500 mb-2">
        현재: <strong>{currentAlgorithm}</strong>
      </p>
      <select
        value={selected}
        onChange={(e) => setSelected(e.target.value)}
        className="border rounded px-2 py-1 mr-2"
        aria-label="전환할 알고리즘 선택"
      >
        <option value="">알고리즘 선택</option>
        {ALLOWED_ALGORITHMS.map((alg) => (
          <option key={alg} value={alg}>
            {alg}
          </option>
        ))}
      </select>
      <button
        onClick={handleSwitch}
        disabled={!isAllowed || isPending}
        className="px-3 py-1 bg-blue-600 text-white rounded disabled:opacity-40"
      >
        {isPending ? '전환 중...' : '전환'}
      </button>
      <button onClick={onClose} className="ml-2 px-3 py-1 border rounded">
        닫기
      </button>
      {isSuccess && (
        <p className="mt-2 text-green-600 text-sm">전환 완료. 목록이 갱신됩니다.</p>
      )}
      {isError && (
        <p className="mt-2 text-red-600 text-sm">전환 실패. 다시 시도해 주세요.</p>
      )}
    </div>
  )
}
