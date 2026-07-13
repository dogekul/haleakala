import '@testing-library/jest-dom/vitest'

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
})

const nativeGetComputedStyle = window.getComputedStyle
window.getComputedStyle = (element: Element, pseudoElement?: string | null) =>
  nativeGetComputedStyle(element, pseudoElement ? undefined : pseudoElement)
