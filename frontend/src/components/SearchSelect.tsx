import { Select, type SelectProps } from 'antd'
import type { DefaultOptionType } from 'antd/es/select'
import { useRef } from 'react'

function normalize(value: unknown) {
  return String(value ?? '').toLocaleLowerCase().replace(/[\s·._-]+/g, '')
}

export function matchSearchOption(input: string, option?: DefaultOptionType) {
  return normalize(option?.label ?? option?.value).includes(normalize(input))
}

export function SearchSelect<ValueType = unknown>({
  options = [],
  filterOption = matchSearchOption,
  value,
  ...props
}: SelectProps<ValueType, DefaultOptionType>) {
  const cache = useRef(new Map<unknown, DefaultOptionType>())
  options.forEach(option => {
    if ('value' in option) cache.current.set(option.value, option)
  })

  const selected = Array.isArray(value) ? value : [value]
  const available = new Set(options.flatMap(option => 'value' in option ? [option.value] : []))
  const selectedFallbacks = selected.flatMap(item => {
    if (item === undefined || item === null || available.has(item)) return []
    const option = cache.current.get(item)
    return option ? [option] : []
  })

  return <Select<ValueType, DefaultOptionType>
    showSearch
    allowClear
    virtual={false}
    optionFilterProp="label"
    {...props}
    value={value}
    filterOption={filterOption}
    options={[...options, ...selectedFallbacks]}
  />
}
