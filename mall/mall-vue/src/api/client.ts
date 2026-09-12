import axios from 'axios'
import { ElMessage } from 'element-plus'
import md5 from 'js-md5'
import type { R } from '@/types'

const SECRET = import.meta.env.VITE_SIGN_SECRET || 'mallx-pay-secret-2026'

export const wayCodeText: Record<string, string> = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  UNIONPAY: '云闪付(银联)',
}

/**
 * 统一签名(与后端 SignUtil 一致):
 * 剔除 sign/空值/嵌套对象数组 → 按 key ASCII 升序 k=v& 拼接 → 末尾 &key=密钥 → MD5 小写。
 * 金额类参数传字符串,避免数字序列化差异导致验签失败。
 */
export function signOf(params: Record<string, unknown>): string {
  const items = Object.entries(params)
    .filter(
      ([k, v]) =>
        k !== 'sign' && v !== null && v !== undefined && v !== '' && typeof v !== 'object',
    )
    .map(([k, v]) => [k, String(v)] as const)
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
  const raw = items.map(([k, v]) => `${k}=${v}`).join('&') + `&key=${SECRET}`
  return md5(raw)
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

/** GET 请求(仅类目列表等只读接口,免签名) */
export async function get<T extends R = R>(path: string): Promise<T> {
  const { data } = await http.get<T>(path)
  if (data?.id !== '0') throw new Error(data?.message || '请求失败')
  return data
}

/** POST + 自动签名。业务失败(id≠0)抛出 Error(message),由调用方或全局提示处理。 */
export async function post<T extends R = R>(
  path: string,
  params: Record<string, unknown> = {},
  opt: { silent?: boolean; headers?: Record<string, string> } = {},
): Promise<T> {
  const sign = signOf(params)
  try {
    const { data } = await http.post<T>(path, { ...params, sign }, { headers: opt.headers })
    if (data?.id !== '0') {
      const err = new Error(data?.message || '请求失败') as Error & { id?: string }
      err.id = data?.id
      throw err
    }
    return data
  } catch (e) {
    const msg = e instanceof Error ? e.message : '网络异常,请稍后重试'
    // 限流/熔断响应没有 id 字段,统一按失败提示
    if (!opt.silent) ElMessage.error(msg || '系统繁忙,请稍后再试')
    throw e
  }
}

/** 免签名请求(登录类接口;即使后端豁免,带上 sign 也无副作用,这里按规范不带) */
export async function postFree<T extends R = R>(
  path: string,
  params: Record<string, unknown> = {},
  opt: { silent?: boolean } = {},
): Promise<T> {
  try {
    const { data } = await http.post<T>(params ? path : path, params)
    if (data?.id !== '0') {
      const err = new Error(data?.message || '请求失败') as Error & { id?: string }
      err.id = data?.id
      throw err
    }
    return data
  } catch (e) {
    const msg = e instanceof Error ? e.message : '网络异常,请稍后重试'
    if (!opt.silent) ElMessage.error(msg || '系统繁忙,请稍后再试')
    throw e
  }
}

export function fmtTime(s?: string | null): string {
  if (!s) return '—'
  return s.includes('T') ? s.replace('T', ' ').slice(0, 19) : s.slice(0, 19)
}

export function money(n: unknown): string {
  const v = Number(n ?? 0)
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
