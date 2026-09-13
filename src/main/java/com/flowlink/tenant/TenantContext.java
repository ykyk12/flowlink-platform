package com.flowlink.tenant;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;

/** 当前请求的租户上下文（由鉴权拦截器写入，请求结束清理）。 */
public final class TenantContext {

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    public static Tenant get() {
        return CURRENT.get();
    }

    public static Tenant require() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        return tenant;
    }

    public static String requireTenantId() {
        return require().getId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
