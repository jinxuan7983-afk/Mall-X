import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/stores/session'

/**
 * 管理端删除操作统一入口（仅 ADMIN 角色可用）：
 * 前端做入口门控 + 危险操作确认 + 结果反馈；后端对登录令牌二次校验并提供业务保护规则兜底。
 */
export function useAdminDelete() {
  const session = useSessionStore()

  /** 是否 ADMIN 角色（管理端敏感操作入口统一门控） */
  const isAdmin = computed(() => session.admin?.role === 'ADMIN')

  /** 兼容旧命名：是否可执行删除（与 isAdmin 同值） */
  const canDelete = isAdmin

  /**
   * 执行删除。
   * @param target  删除对象描述（如 `商品「iPhone」`）
   * @param tip     删除影响说明（追加在确认文案中）
   * @param action  实际请求，入参为登录令牌（经请求头提交）
   * @param successMsg 成功提示文案
   * @returns 删除成功返回 true；取消或失败返回 false（失败原因由 client.ts 统一提示）
   */
  async function adminDelete(
    target: string,
    tip: string,
    action: (token: string) => Promise<unknown>,
    successMsg: string,
  ): Promise<boolean> {
    const token = session.admin?.token
    if (!token) {
      ElMessage.warning('登录凭证已失效,请退出后重新登录')
      return false
    }
    try {
      await ElMessageBox.confirm(`确认删除${target}?${tip}`, '删除确认', {
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      })
    } catch {
      return false
    }
    try {
      await action(token)
      ElMessage.success(successMsg)
      return true
    } catch {
      return false
    }
  }

  return { isAdmin, canDelete, adminDelete }
}
