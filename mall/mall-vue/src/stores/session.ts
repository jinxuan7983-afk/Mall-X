import { defineStore } from 'pinia'

export interface UserSession {
  userNum: string
  username: string
}
export interface MchSession {
  shopNum: string
  shopName: string
  shopState: 0 | 1 | 2
  /** 登录令牌:商户端敏感操作(如删除商品)随请求头提交,由后端校验身份与商品归属 */
  token?: string
}
export interface AdminSession {
  username: string
  realName: string
  role: 'ADMIN' | 'OPS'
  /** 登录令牌:管理端敏感操作(删除等)随请求头提交,由后端校验身份与角色 */
  token?: string
}

function load<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    user: load<UserSession>('mallx_user'),
    mch: load<MchSession>('mallx_mch'),
    admin: load<AdminSession>('mallx_admin'),
  }),
  getters: {
    isUser: (s) => !!s.user,
    isMch: (s) => !!s.mch,
    isAdmin: (s) => !!s.admin,
  },
  actions: {
    setUser(u: UserSession | null) {
      this.user = u
      u ? localStorage.setItem('mallx_user', JSON.stringify(u)) : localStorage.removeItem('mallx_user')
    },
    setMch(m: MchSession | null) {
      this.mch = m
      m ? localStorage.setItem('mallx_mch', JSON.stringify(m)) : localStorage.removeItem('mallx_mch')
    },
    setAdmin(a: AdminSession | null) {
      this.admin = a
      a ? localStorage.setItem('mallx_admin', JSON.stringify(a)) : localStorage.removeItem('mallx_admin')
    },
  },
})
