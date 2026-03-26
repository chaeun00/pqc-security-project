import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postAlgorithmSwitch, type AlgorithmSwitchPayload } from '@/api/algorithm'

export function useAlgorithmSwitch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: AlgorithmSwitchPayload) => postAlgorithmSwitch(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cbom'] })
    },
  })
}
