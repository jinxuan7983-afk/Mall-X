import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { refundEvidenceUpload } from '@/api/modules'

/** 前端压缩：最长边 800px、JPEG(质量 0.82)，控制上传体积 */
export function compressImage(file: File, maxSide = 800, quality = 0.82): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(img.width * scale))
      canvas.height = Math.max(1, Math.round(img.height * scale))
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('当前浏览器不支持图片压缩'))
        return
      }
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
      resolve(canvas.toDataURL('image/jpeg', quality))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片解析失败，请更换图片重试'))
    }
    img.src = url
  })
}

/**
 * 退款凭证上传：选择即压缩上传，返回可访问地址集合（默认最多 3 张）。
 * 提交退款申请时以数组随表单提交，失败提示由统一请求层处理。
 */
export function useRefundEvidence(maxCount = 3) {
  const urls = ref<string[]>([])
  const uploading = ref(false)

  async function add(file: File) {
    if (urls.value.length >= maxCount) {
      ElMessage.warning(`最多上传 ${maxCount} 张凭证`)
      return
    }
    if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) {
      ElMessage.warning('仅支持 JPG / PNG / WebP 格式的图片')
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.warning('原始图片不能超过 5MB，请压缩后再上传')
      return
    }
    uploading.value = true
    try {
      const base64 = await compressImage(file)
      const r = await refundEvidenceUpload(base64)
      urls.value.push(r.url)
      ElMessage.success('凭证上传成功')
    } catch {
      // 失败原因已由统一请求层提示
    } finally {
      uploading.value = false
    }
  }

  function remove(url: string) {
    urls.value = urls.value.filter((u) => u !== url)
  }

  function clear() {
    urls.value = []
  }

  return { urls, uploading, add, remove, clear }
}
