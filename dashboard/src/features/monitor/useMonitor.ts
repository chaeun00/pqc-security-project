import { useQuery } from '@tanstack/react-query'
import { getCbomList } from '@/api/cbom'

export function useMonitor() {
  return useQuery({
    queryKey: ['monitor'],
    queryFn: getCbomList,
    refetchInterval: 5000,
  })
}
