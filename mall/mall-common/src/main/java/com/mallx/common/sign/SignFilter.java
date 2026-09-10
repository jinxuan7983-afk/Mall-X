package com.mallx.common.sign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一验签过滤器：拦截所有业务 POST 请求，重算 MD5 签名比对，验签失败直接拒绝。
 * 规则见 {@link SignUtil}。请求体仅读取一次并缓存，后续 Controller 仍可正常绑定。
 */
@Slf4j
@RequiredArgsConstructor
public class SignFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SignProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(p -> MATCHER.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(request);
        Map<String, String> params = wrapper.flatParams();
        String sign = params.remove(SignUtil.SIGN_FIELD);
        if (sign == null || sign.isEmpty()) {
            reject(response, "缺少签名参数sign");
            return;
        }
        if (!SignUtil.verify(params, properties.getSecret(), sign)) {
            log.warn("验签失败 uri={} params={}", request.getRequestURI(), params);
            reject(response, "签名校验失败，禁止访问");
            return;
        }
        chain.doFilter(wrapper, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"id\":\"401\",\"message\":\"" + message + "\"}");
    }

    /**
     * 缓存请求体，并把 查询参数 + JSON扁平字段 合并为签名参数集合（数字/布尔/字符串参与签名，
     * 嵌套对象与数组不参与签名，详见 SignUtil 注释）。
     */
    public static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        public Map<String, String> flatParams() {
            Map<String, String> params = new LinkedHashMap<>();
            getRequest().getParameterMap().forEach((k, vs) -> {
                if (vs != null && vs.length > 0) {
                    params.put(k, vs[0]);
                }
            });
            if (body.length > 0) {
                try {
                    JsonNode node = MAPPER.readTree(body);
                    if (node != null && node.isObject()) {
                        node.fields().forEachRemaining(e -> {
                            JsonNode v = e.getValue();
                            // 仅基本值参与签名；null 与嵌套对象/数组不参与
                            if (v.isValueNode() && !v.isNull()) {
                                params.put(e.getKey(), v.asText());
                            }
                        });
                    }
                } catch (Exception ignore) {
                    // 非JSON请求体（如文件上传）不参与签名
                }
            }
            return params;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
