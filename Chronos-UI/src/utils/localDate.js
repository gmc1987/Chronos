// 业务日期按浏览器所在时区计算；toISOString() 会在中国凌晨返回前一天的 UTC 日期。
export const todayLocalDate = () => {
  const value = new Date()
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
