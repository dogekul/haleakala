export function buildProjectName(customerName?: string, productName?: string, versionName?: string) {
  if (!customerName || !productName || !versionName) return undefined
  return `${customerName} - ${productName} ${versionName} 实施项目`
}
