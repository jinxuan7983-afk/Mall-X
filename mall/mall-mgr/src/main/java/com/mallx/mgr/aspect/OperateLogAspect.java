package com.mallx.mgr.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallx.mgr.entity.OperateLog;
import com.mallx.mgr.mapper.OperateLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Pointcut("execution(* com.mallx.mgr.controller.AdminController.*(..))")
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
            }
            operateLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
    }
}
