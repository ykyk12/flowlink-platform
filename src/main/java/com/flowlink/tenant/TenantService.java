package com.flowlink.tenant;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.common.Hashes;
import com.flowlink.common.Ids;
import com.flowlink.config.FlowLinkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 租户管理：创建（返回一次性 API Key）、轮换 Key、调整配额、启停用。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final FlowLinkProperties properties;

    @Transactional
    public TenantDtos.TenantCreatedView create(TenantDtos.CreateTenantRequest request) {
        String rawKey = generateApiKey();
        int quota = request.quotaPerMinute() == null
                ? properties.getDefaultQuotaPerMinute() : request.quotaPerMinute();
        Tenant tenant = new Tenant(Ids.uuid(), request.name(), Hashes.sha256Hex(rawKey),
                request.plan() == null ? "standard" : request.plan(), quota);
        tenantRepository.save(tenant);
        log.info("创建租户 {}（{}），配额 {}/分钟", tenant.getName(), tenant.getId(), quota);
        return new TenantDtos.TenantCreatedView(tenant.getId(), tenant.getName(), tenant.getPlan(),
                tenant.getQuotaPerMinute(), rawKey);
    }

    /** 供演示数据与测试使用：用固定明文 Key 创建租户。 */
    @Transactional
    public Tenant createWithRawKey(String name, String rawKey, String plan, int quotaPerMinute) {
        Tenant tenant = new Tenant(Ids.uuid(), name, Hashes.sha256Hex(rawKey), plan, quotaPerMinute);
        return tenantRepository.save(tenant);
    }

    @Transactional
    public TenantDtos.TenantCreatedView rotateKey(String tenantId) {
        Tenant tenant = require(tenantId);
        String rawKey = generateApiKey();
        tenant.setApiKeyHash(Hashes.sha256Hex(rawKey));
        tenantRepository.save(tenant);
        log.info("租户 {} 轮换 API Key", tenantId);
        return new TenantDtos.TenantCreatedView(tenant.getId(), tenant.getName(), tenant.getPlan(),
                tenant.getQuotaPerMinute(), rawKey);
    }

    @Transactional
    public TenantDtos.TenantView updateQuota(String tenantId, int quotaPerMinute) {
        Tenant tenant = require(tenantId);
        tenant.setQuotaPerMinute(quotaPerMinute);
        tenantRepository.save(tenant);
        return toView(tenant);
    }

    @Transactional
    public TenantDtos.TenantView setEnabled(String tenantId, boolean enabled) {
        Tenant tenant = require(tenantId);
        tenant.setEnabled(enabled);
        tenantRepository.save(tenant);
        return toView(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantDtos.TenantView> list() {
        return tenantRepository.findAll().stream().map(this::toView).toList();
    }

    public Tenant require(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户不存在：" + tenantId));
    }

    private TenantDtos.TenantView toView(Tenant tenant) {
        return new TenantDtos.TenantView(tenant.getId(), tenant.getName(), tenant.getPlan(),
                tenant.getQuotaPerMinute(), tenant.isEnabled(), tenant.getCreatedAt());
    }

    private String generateApiKey() {
        return "flk_" + Ids.shortId();
    }
}
