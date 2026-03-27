import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getInventoryList,
  createInventory,
  updateInventory,
  deleteInventory,
  type InventoryItem,
  type InventoryCreatePayload,
  type InventoryUpdatePayload,
} from '@/api/inventory'

const QUERY_KEY = ['inventory'] as const

export function useInventoryList() {
  return useQuery({ queryKey: QUERY_KEY, queryFn: getInventoryList })
}

export function useCreateInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: InventoryCreatePayload) => createInventory(payload),
    onMutate: async (payload) => {
      await queryClient.cancelQueries({ queryKey: QUERY_KEY })
      const previous = queryClient.getQueryData<InventoryItem[]>(QUERY_KEY)
      const optimistic: InventoryItem = { id: Date.now(), ...payload }
      queryClient.setQueryData<InventoryItem[]>(QUERY_KEY, (old) => [...(old ?? []), optimistic])
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous !== undefined) {
        queryClient.setQueryData(QUERY_KEY, ctx.previous)
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  })
}

export function useUpdateInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: InventoryUpdatePayload }) =>
      updateInventory(id, payload),
    onMutate: async ({ id, payload }) => {
      await queryClient.cancelQueries({ queryKey: QUERY_KEY })
      const previous = queryClient.getQueryData<InventoryItem[]>(QUERY_KEY)
      queryClient.setQueryData<InventoryItem[]>(QUERY_KEY, (old) =>
        (old ?? []).map((item) => (item.id === id ? { ...item, ...payload } : item)),
      )
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous !== undefined) {
        queryClient.setQueryData(QUERY_KEY, ctx.previous)
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  })
}

export function useDeleteInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteInventory(id),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: QUERY_KEY })
      const previous = queryClient.getQueryData<InventoryItem[]>(QUERY_KEY)
      queryClient.setQueryData<InventoryItem[]>(QUERY_KEY, (old) =>
        (old ?? []).filter((item) => item.id !== id),
      )
      return { previous }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous !== undefined) {
        queryClient.setQueryData(QUERY_KEY, ctx.previous)
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  })
}
