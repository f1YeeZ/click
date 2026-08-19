import { describe, expect, it } from 'vitest'
import { useAdminActionDialog } from './useAdminActionDialog'

describe('admin action dialog', () => {
  it('resolves entered fields only after custom confirmation', async () => {
    const dialog = useAdminActionDialog()
    const result = dialog.requestAdminAction({
      title: '复核数据',
      fields: [{ key: 'note', label: '复核结论', value: '已核对' }],
    })

    expect(dialog.actionDialog.value.title).toBe('复核数据')
    dialog.confirmAdminAction({ note: '参数无误' })

    await expect(result).resolves.toEqual({ note: '参数无误' })
    expect(dialog.actionDialog.value).toBeNull()
  })

  it('resolves null when the custom dialog is cancelled', async () => {
    const dialog = useAdminActionDialog()
    const result = dialog.requestAdminAction({ title: '删除图片', tone: 'danger' })
    dialog.cancelAdminAction()
    await expect(result).resolves.toBeNull()
  })
})
