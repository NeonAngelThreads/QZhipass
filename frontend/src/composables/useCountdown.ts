import { onScopeDispose, ref } from 'vue'

export function useCountdown() {
  const seconds = ref(0)
  let timer: number | undefined

  function stop() {
    if (timer) {
      window.clearInterval(timer)
      timer = undefined
    }
  }

  function start(value: number) {
    stop()
    seconds.value = Math.max(0, Math.ceil(value))
    if (seconds.value === 0) {
      return
    }

    timer = window.setInterval(() => {
      seconds.value = Math.max(0, seconds.value - 1)
      if (seconds.value === 0) {
        stop()
      }
    }, 1000)
  }

  onScopeDispose(stop)

  return {
    seconds,
    start,
    stop
  }
}
