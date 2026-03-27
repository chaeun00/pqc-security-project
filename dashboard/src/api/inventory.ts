import { axiosClient } from '@/lib/axiosClient'

export type InventoryItem = {
  id: number
  name: string
  algorithm: string
  location: string
  note?: string
}

export type InventoryCreatePayload = Omit<InventoryItem, 'id'>
export type InventoryUpdatePayload = Partial<InventoryCreatePayload>

export async function getInventoryList(): Promise<InventoryItem[]> {
  const res = await axiosClient.get<InventoryItem[]>('/api/inventory')
  return res.data
}

export async function createInventory(payload: InventoryCreatePayload): Promise<InventoryItem> {
  const res = await axiosClient.post<InventoryItem>('/api/inventory', payload)
  return res.data
}

export async function updateInventory(id: number, payload: InventoryUpdatePayload): Promise<InventoryItem> {
  const res = await axiosClient.put<InventoryItem>(`/api/inventory/${id}`, payload)
  return res.data
}

export async function deleteInventory(id: number): Promise<void> {
  await axiosClient.delete(`/api/inventory/${id}`)
}
