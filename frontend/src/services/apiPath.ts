/// <reference types="vite/client" />

export function appBase(base = import.meta.env.BASE_URL) {
  return base === '/' ? '' : base.replace(/\/$/, '')
}

export function apiPath(path: string, base = import.meta.env.BASE_URL) {
  return `${appBase(base)}${path}`
}
