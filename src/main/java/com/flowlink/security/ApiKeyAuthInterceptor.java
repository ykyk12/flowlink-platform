package com.flowlink.security;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.common.Hashes;
import com.flowlink.tenant.QuotaService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantContext;
import com.flowlink.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * API Key 鉴权 + 配额限流拦截器。
 * 顺序：解析 X-API-Key → 摘要查租户 → 写 TenantContext → 配额检查（滑动窗口）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-API-Key";

    private final TenantRepository tenantRepository;
    private final QuotaService quotaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少请求头 " + HEADER);
        }
        String hash = Hashes.sha256Hex(rawKey.trim());
        Optional<Tenant> found = tenantRepository.findByApiKeyHash(hash);
        if (found.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        Tenant tenant = found.get();
        if (!tenant.isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "租户已被停用");
        }
        TenantContext.set(tenant);

        QuotaService.QuotaDecision decision = quotaService.tryAcquire(tenant);
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            throw new BizException(ErrorCode.QUOTA_EXCEEDED,
                    "超出每分钟配额 " + tenant.getQuotaPerMinute() + "，请在 " + decision.retryAfterSeconds() + "s 后重试");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
