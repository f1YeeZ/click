import { describe, expect, it } from 'vitest'
import { readStoredJson } from './storage'

const storageWith = (value) => ({ getItem: () => value })

describe('readStoredJson', () => {
  it('returns parsed data when storage contains valid JSON', () => {
    expect(readStoredJson(storageWith('{"role":"ADMIN"}'), 'user')).toEqual({ role: 'ADMIN' })
  })

  it('falls back for missing, malformed, or unavailable storage', () => {
    expect(readStoredJson(storageWith(null), 'user', [])).toEqual([])
    expect(readStoredJson(storageWith('{broken'), 'user', [])).toEqual([])
    expect(readStoredJson(null, 'user', [])).toEqual([])
  })
})

