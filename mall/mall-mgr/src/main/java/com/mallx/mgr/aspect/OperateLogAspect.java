package com.mallx.mgr.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallx.common.jwt.JwtUtil;
import com.mallx.mgr.entity.OperateLog;
import com.mallx.mgr.mapper.OperateLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 后台操作日志切面：记录平台管理端所有操作（系统运维管理）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperateLogAspect {

    private final OperateLogMapper operateLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${mallx.jwt.secret:mallx-jwt-secret-2026}")
    private String jwtSecret;

    @Pointcut("execution(* com.mallx.mgr.controller.AdminController.*(..))"
            + " || execution(* com.mallx.mgr.controller.RefundController.audit(..))"
            + " || execution(* com.mallx.mgr.controller.RefundController.list(..))"
            + " || execution(* com.mallx.mgr.controller.RefundController.delete(..))"
            + " || execution(* com.mallx.mgr.controller.CategoryController.save(..))"
            + " || execution(* com.mallx.mgr.controller.CategoryController.delete(..))")
    public void adminPointcut() {
    }

    @AfterReturning("adminPointcut()")
    public void record(JoinPoint joinPoint) {
        try {
            OperateLog logEntity = new OperateLog();
            logEntity.setModule(joinPoint.getSignature().getName());
            Object[] args = joinPoint.getArgs();
            String content = "success";
            try {
                if (args.length > 0) {
                    content = objectMapper.writeValueAsString(args[0]);
                }
            } catch (Exception ignore) {
                // 非JSON参数退化为字符串
                content = args.length > 0 ? String.valueOf(args[0]) : content;
            }
            logEntity.setContent(content.length() > 500 ? content.substring(0, 500) : content);
            logEntity.setOperator("admin");
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                logEntity.setIp(ip == null ? request.getRemoteAddr() : ip.split(",")[0].trim());
                logEntity.setOperator(resolveOperator(request));
            }
            operateLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
    }

    /** 从请求头 X-Admin-Token 解析真实操作员（管理端敏感操作携带）；缺失或非法时回退 admin */
    private String resolveOperator(HttpServletRequest request) {
        Map<String, Object> payload = JwtUtil.verify(jwtSecret, request.getHeader("X-Admin-Token"));
        return payload == null ? "admin" : String.valueOf(payload.get(JwtUtil.CLAIM_NUM));
    }
}
