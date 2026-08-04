export const createCatalogFilters = (defaults, query, multiKeys) => {
  const filters = Object.fromEntries(
    Object.keys(defaults).map((key) => [key, query[key] ?? defaults[key]])
  )
  multiKeys.forEach((key) => {
    filters[key] = filters[key] ? String(filters[key]).split(',').filter(Boolean) : []
  })
  filters.page = Number(query.page || defaults.page)
  filters.pageSize = Number(query.pageSize || defaults.pageSize)
  return filters
}

export const compactCatalogFilters = (source) => Object.fromEntries(
  Object.entries(source)
    .filter(([, value]) => Array.isArray(value) ? value.length : value !== '' && value != null)
    .map(([key, value]) => [key, Array.isArray(value) ? value.join(',') : value])
)

export const clearCatalogFilterKeys = (filters, keys, multiKeys) => {
  keys.forEach((key) => { filters[key] = multiKeys.includes(key) ? [] : '' })
}

