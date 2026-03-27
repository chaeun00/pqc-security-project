import { Fragment, useState } from 'react'
import { useInventoryList, useCreateInventory, useUpdateInventory, useDeleteInventory } from './useInventory'
import type { InventoryItem, InventoryCreatePayload } from '@/api/inventory'

const EMPTY_FORM: InventoryCreatePayload = { name: '', algorithm: '', location: '', note: '' }

export default function InventoryPage() {
  const { data, isLoading, isError } = useInventoryList()
  const createMutation = useCreateInventory()
  const updateMutation = useUpdateInventory()
  const deleteMutation = useDeleteInventory()

  const [form, setForm] = useState<InventoryCreatePayload>(EMPTY_FORM)
  const [editId, setEditId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState<InventoryCreatePayload>(EMPTY_FORM)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
  const [createError, setCreateError] = useState('')
  const [editError, setEditError] = useState('')

  if (isLoading) return <div className="p-4">로딩 중...</div>
  if (isError) return <div className="p-4 text-red-600">데이터를 불러오지 못했습니다.</div>

  const items = data ?? []

  function handleCreate() {
    setCreateError('')
    createMutation.mutate(form, {
      onSuccess: () => setForm(EMPTY_FORM),
      onError: () => setCreateError('등록에 실패했습니다. 다시 시도해 주세요.'),
    })
  }

  function startEdit(item: InventoryItem) {
    setEditId(item.id)
    setEditForm({ name: item.name, algorithm: item.algorithm, location: item.location, note: item.note ?? '' })
    setEditError('')
  }

  function handleUpdate() {
    if (editId === null) return
    setEditError('')
    updateMutation.mutate(
      { id: editId, payload: editForm },
      {
        onSuccess: () => setEditId(null),
        onError: () => setEditError('수정에 실패했습니다. 다시 시도해 주세요.'),
      },
    )
  }

  function handleDelete(id: number) {
    deleteMutation.mutate(id, {
      onSettled: () => setDeleteConfirmId(null),
    })
  }

  const isCreateDisabled = !form.name.trim() || !form.algorithm.trim() || !form.location.trim() || createMutation.isPending
  const isEditDisabled = !editForm.name.trim() || !editForm.algorithm.trim() || !editForm.location.trim() || updateMutation.isPending

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">재고 관리</h1>

      {/* 등록 폼 */}
      <div className="mb-6 border rounded p-4 bg-gray-50">
        <h2 className="font-semibold mb-3">새 항목 등록</h2>
        <div className="flex flex-wrap gap-2 items-end">
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-600">이름 *</label>
            <input
              className="border rounded px-2 py-1 text-sm"
              placeholder="이름"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-600">알고리즘 *</label>
            <input
              className="border rounded px-2 py-1 text-sm"
              placeholder="알고리즘"
              value={form.algorithm}
              onChange={(e) => setForm((f) => ({ ...f, algorithm: e.target.value }))}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-600">위치 *</label>
            <input
              className="border rounded px-2 py-1 text-sm"
              placeholder="위치"
              value={form.location}
              onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-sm text-gray-600">비고</label>
            <input
              className="border rounded px-2 py-1 text-sm"
              placeholder="비고 (선택)"
              value={form.note}
              onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
            />
          </div>
          <button
            onClick={handleCreate}
            disabled={isCreateDisabled}
            className="px-3 py-1 bg-blue-600 text-white rounded disabled:opacity-40 text-sm"
          >
            {createMutation.isPending ? '등록 중...' : '등록'}
          </button>
        </div>
        {createError && <p className="mt-2 text-red-600 text-sm">{createError}</p>}
      </div>

      {/* 목록 테이블 */}
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="bg-gray-100 text-left">
            <th className="border px-3 py-2">ID</th>
            <th className="border px-3 py-2">이름</th>
            <th className="border px-3 py-2">알고리즘</th>
            <th className="border px-3 py-2">위치</th>
            <th className="border px-3 py-2">비고</th>
            <th className="border px-3 py-2">액션</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <Fragment key={item.id}>
              <tr className="hover:bg-gray-50">
                <td className="border px-3 py-2">{item.id}</td>
                <td className="border px-3 py-2">{item.name}</td>
                <td className="border px-3 py-2">{item.algorithm}</td>
                <td className="border px-3 py-2">{item.location}</td>
                <td className="border px-3 py-2">{item.note ?? '—'}</td>
                <td className="border px-3 py-2">
                  <button
                    onClick={() => startEdit(item)}
                    className="mr-2 px-2 py-0.5 border rounded text-xs"
                    aria-label={`편집 ${item.name}`}
                  >
                    편집
                  </button>
                  {deleteConfirmId === item.id ? (
                    <>
                      <span className="text-xs text-gray-600 mr-1">삭제할까요?</span>
                      <button
                        onClick={() => handleDelete(item.id)}
                        className="px-2 py-0.5 bg-red-600 text-white rounded text-xs mr-1"
                        aria-label={`삭제 확인 ${item.name}`}
                      >
                        확인
                      </button>
                      <button
                        onClick={() => setDeleteConfirmId(null)}
                        className="px-2 py-0.5 border rounded text-xs"
                      >
                        취소
                      </button>
                    </>
                  ) : (
                    <button
                      onClick={() => setDeleteConfirmId(item.id)}
                      className="px-2 py-0.5 border rounded text-xs text-red-600"
                      aria-label={`삭제 ${item.name}`}
                    >
                      삭제
                    </button>
                  )}
                </td>
              </tr>
              {editId === item.id && (
                <tr>
                  <td colSpan={6} className="border px-3 py-3 bg-blue-50">
                    <div className="flex flex-wrap gap-2 items-end">
                      <input
                        className="border rounded px-2 py-1 text-sm"
                        placeholder="이름 *"
                        value={editForm.name}
                        onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                        aria-label="편집 이름"
                      />
                      <input
                        className="border rounded px-2 py-1 text-sm"
                        placeholder="알고리즘 *"
                        value={editForm.algorithm}
                        onChange={(e) => setEditForm((f) => ({ ...f, algorithm: e.target.value }))}
                        aria-label="편집 알고리즘"
                      />
                      <input
                        className="border rounded px-2 py-1 text-sm"
                        placeholder="위치 *"
                        value={editForm.location}
                        onChange={(e) => setEditForm((f) => ({ ...f, location: e.target.value }))}
                        aria-label="편집 위치"
                      />
                      <input
                        className="border rounded px-2 py-1 text-sm"
                        placeholder="비고"
                        value={editForm.note}
                        onChange={(e) => setEditForm((f) => ({ ...f, note: e.target.value }))}
                        aria-label="편집 비고"
                      />
                      <button
                        onClick={handleUpdate}
                        disabled={isEditDisabled}
                        className="px-3 py-1 bg-blue-600 text-white rounded disabled:opacity-40 text-sm"
                      >
                        {updateMutation.isPending ? '저장 중...' : '저장'}
                      </button>
                      <button
                        onClick={() => setEditId(null)}
                        className="px-3 py-1 border rounded text-sm"
                      >
                        취소
                      </button>
                    </div>
                    {editError && <p className="mt-2 text-red-600 text-sm">{editError}</p>}
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>

      {items.length === 0 && (
        <p className="mt-4 text-gray-500 text-sm">등록된 항목이 없습니다.</p>
      )}
    </div>
  )
}
