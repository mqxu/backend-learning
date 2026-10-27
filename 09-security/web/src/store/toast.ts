import { reactive } from 'vue'

/**
 * 极简提示条。不引组件库，把注意力留给鉴权链路本身。
 */
interface Toast {
  id: number
  message: string
}

export const toasts = reactive<Toast[]>([])

let seq = 0

export function notify(message: string): void {
  const id = ++seq
  toasts.push({ id, message })
  setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index > -1) toasts.splice(index, 1)
  }, 2600)
}
