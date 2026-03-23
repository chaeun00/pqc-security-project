import { create } from 'zustand'

interface AppState {
  token: string | null
  selectedAlgorithm: string
  setToken: (t: string) => void
  clearToken: () => void
  setSelectedAlgorithm: (a: string) => void
}

export const useAppStore = create<AppState>()((set) => ({
  token: null,
  selectedAlgorithm: 'ML-KEM-768',
  setToken: (t) => set({ token: t }),
  clearToken: () => set({ token: null }),
  setSelectedAlgorithm: (a) => set({ selectedAlgorithm: a }),
}))
