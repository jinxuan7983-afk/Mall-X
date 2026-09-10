import { defineStore } from 'pinia'

/** 购物车条目:下单前快照商品信息,离线保存在 localStorage(大纲:本地缓存及离线购物车合并策略) */
export interface CartItem {
  goodsNum: string
  goodsName: string
  price: number
  count: number
  stock: number
  shopNum: string
  shopName: string
  picSeed: string
}

const KEY = 'mallx_cart_v1'

function read(): CartItem[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '[]') as CartItem[]
  } catch {
    return []
  }
}

export const useCartStore = defineStore('cart', {
  state: () => ({ items: read() }),
  getters: {
    total: (s) => s.items.reduce((acc, i) => acc + i.price * i.count, 0),
    count: (s) => s.items.reduce((acc, i) => acc + i.count, 0),
    /** 按商户分组(后端单笔订单仅支持同一商户) */
    groups: (s) => {
      const map = new Map<string, CartItem[]>()
      for (const it of s.items) {
        const list = map.get(it.shopNum) || []
        list.push(it)
        map.set(it.shopNum, list)
      }
      return [...map.entries()].map(([shopNum, items]) => ({
        shopNum,
        shopName: items[0]?.shopName || shopNum,
        items,
        total: items.reduce((acc, i) => acc + i.price * i.count, 0),
      }))
    },
  },
  actions: {
    /** 加入购物车:同商品累加数量;登录后离线购物车与已保存内容自然合并(同一 localStorage 键) */
    add(item: Omit<CartItem, 'count'>, count: number) {
      const found = this.items.find((i) => i.goodsNum === item.goodsNum)
      if (found) {
        found.count = Math.min(found.count + count, item.stock)
      } else {
        this.items.push({ ...item, count })
      }
      this.persist()
    },
    setCount(goodsNum: string, count: number) {
      const it = this.items.find((i) => i.goodsNum === goodsNum)
      if (!it) return
      if (count <= 0) this.remove(goodsNum)
      else {
        it.count = Math.min(count, it.stock)
        this.persist()
      }
    },
    remove(goodsNum: string) {
      this.items = this.items.filter((i) => i.goodsNum !== goodsNum)
      this.persist()
    },
    removeMany(goodsNums: string[]) {
      const set = new Set(goodsNums)
      this.items = this.items.filter((i) => !set.has(i.goodsNum))
      this.persist()
    },
    clear() {
      this.items = []
      this.persist()
    },
    persist() {
      localStorage.setItem(KEY, JSON.stringify(this.items))
    },
  },
})
