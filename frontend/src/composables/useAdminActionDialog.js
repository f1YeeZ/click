import { ref } from 'vue'

export const useAdminActionDialog = () => {
  const actionDialog = ref(null)
  let settle = null

  const closePending = value => {
    const resolve = settle
    settle = null
    actionDialog.value = null
    resolve?.(value)
  }

  const requestAdminAction = options => new Promise(resolve => {
    if (settle) closePending(null)
    settle = resolve
    actionDialog.value = {
      title: options.title,
      subtitle: options.subtitle || '',
      message: options.message || '',
      confirmLabel: options.confirmLabel || '确认操作',
      cancelLabel: options.cancelLabel || '取消',
      tone: options.tone === 'danger' ? 'danger' : 'default',
      fields: (options.fields || []).map(field => ({
        type: 'text',
        value: '',
        required: false,
        maxlength: 1000,
        ...field,
      })),
    }
  })

  const confirmAdminAction = values => closePending(values || {})
  const cancelAdminAction = () => closePending(null)

  return { actionDialog, requestAdminAction, confirmAdminAction, cancelAdminAction }
}
