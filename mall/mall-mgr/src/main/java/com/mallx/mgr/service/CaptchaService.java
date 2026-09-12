package com.mallx.mgr.service;

import com.mallx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 图形验证码服务：Redis 一次性存储 + 过期控制，防注册接口被脚本批量刷写。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    /** 保存验证码：captchaKey → code，5 分钟过期 */
    public void save(String captchaKey, String code) {
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaKey, code.toLowerCase(), TTL);
    }

    /**
     * 校验并消费验证码（忽略大小写，一次性使用：无论成功与否均删除，防止重放）。
     * 校验失败抛出业务异常。
     */
    public void verifyAndConsume(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaKey.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BizException("请输入验证码");
        }
        String key = KEY_PREFIX + captchaKey;
        String stored = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (stored == null) {
            throw new BizException("验证码已过期，请点击图片刷新后重试");
        }
        if (!stored.equals(captchaCode.trim().toLowerCase())) {
            throw new BizException("验证码错误，请重新输入");
        }
    }
}
