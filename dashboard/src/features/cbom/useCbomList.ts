import { useQuery } from '@tanstack/react-query'
import { getCbomList } from '@/api/cbom'

export function useCbomList() {
  return useQuery({
    queryKey: ['cbom'],
    queryFn: getCbomList,
  })
}
