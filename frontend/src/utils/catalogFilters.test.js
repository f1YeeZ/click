import { describe, expect, it } from 'vitest'
import { clearCatalogFilterKeys, compactCatalogFilters, createCatalogFilters } from './catalogFilters'

const defaults = { q: '', brand: [], shape: [], page: 1, pageSize: 12 }
const multiKeys = ['brand', 'shape']

describe('catalog filter URL state', () => {
  it('restores typed filters from route query values', () => {
    expect(createCatalogFilters(defaults, { brand: 'Logitech,Razer', page: '3' }, multiKeys)).toEqual({
      q: '', brand: ['Logitech', 'Razer'], shape: [], page: 3, pageSize: 12
    })
  })

  it('serializes active filters and omits empty values', () => {
    expect(compactCatalogFilters({ ...defaults, q: 'viper', brand: ['Razer'] })).toEqual({
      q: 'viper', brand: 'Razer', page: 1, pageSize: 12
    })
  })

  it('clears multi-select filters back to arrays', () => {
    const filters = { brand: ['Razer'], q: 'viper' }
    clearCatalogFilterKeys(filters, ['brand', 'q'], multiKeys)
    expect(filters).toEqual({ brand: [], q: '' })
  })
})

