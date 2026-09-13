package com.flowlink.ruleset;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.common.Ids;
import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.dsl.RuleDslParser;
import com.flowlink.dsl.RuleSetDsl;
import com.flowlink.dsl.RuleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 规则集与版本治理：草稿 → 发布 → 灰度 → 回滚，每次变更重建缓存（热加载）。
 * 版本内容不可变：任何调整都产生新版本，历史版本可用于回滚与回放对比。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final RuleDslParser parser;
    private final RuleValidator validator;
    private final RuleCache ruleCache;

    @Transactional
    public RuleSetDtos.RuleSetView createSet(String tenantId, RuleSetDtos.CreateSetRequest request) {
        ruleSetRepository.findByTenantIdAndKey(tenantId, request.key()).ifPresent(existing -> {
            throw new BizException(ErrorCode.CONFLICT, "规则集已存在：" + request.key());
        });
        RuleSetEntity entity = new RuleSetEntity(Ids.uuid(), tenantId, request.key(), request.name());
        ruleSetRepository.save(entity);
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public List<RuleSetDtos.RuleSetView> listSets(String tenantId) {
        return ruleSetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public RuleSetDtos.RuleSetView getSet(String tenantId, String key) {
        return toView(requireSet(tenantId, key));
    }

    @Transactional
    public RuleSetDtos.VersionView createVersion(String tenantId, String key,
                                                 RuleSetDtos.CreateVersionRequest request) {
        RuleSetEntity set = requireSet(tenantId, key);
        CompiledRuleSet compiled = compile(request.content());
        long nextVersion = ruleVersionRepository
                .findFirstByTenantIdAndRuleSetIdOrderByVersionDesc(tenantId, set.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1L);
        RuleVersionEntity version = new RuleVersionEntity(Ids.uuid(), tenantId, set.getId(), nextVersion,
                request.content(), request.note());
        ruleVersionRepository.save(version);
        set.touch();
        ruleSetRepository.save(set);
        log.info("租户 {} 规则集 {} 新建版本 v{}（{} 条规则）", tenantId, key, nextVersion, compiled.ruleCount());
        return toView(version);
    }

    @Transactional(readOnly = true)
    public List<RuleSetDtos.VersionView> listVersions(String tenantId, String key) {
        RuleSetEntity set = requireSet(tenantId, key);
        return ruleVersionRepository.findByTenantIdAndRuleSetIdOrderByVersionDesc(tenantId, set.getId())
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public RuleSetDtos.ContentView getVersionContent(String tenantId, String key, long version) {
        RuleSetEntity set = requireSet(tenantId, key);
        RuleVersionEntity entity = ruleVersionRepository
                .findByTenantIdAndRuleSetIdAndVersion(tenantId, set.getId(), version)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "版本不存在：v" + version));
        return new RuleSetDtos.ContentView(key, version, entity.getContent());
    }

    /** 发布：目标版本置 ACTIVE，其余 ACTIVE 置 RETIRED；指针切换即热加载。 */
    @Transactional
    public RuleSetDtos.RuleSetView publish(String tenantId, String key, long version) {
        RuleSetEntity set = requireSet(tenantId, key);
        RuleVersionEntity target = requireVersion(tenantId, set, version);
        compile(target.getContent());

        for (RuleVersionEntity active : ruleVersionRepository
                .findByTenantIdAndRuleSetIdAndStatus(tenantId, set.getId(), VersionStatus.ACTIVE)) {
            if (!active.getId().equals(target.getId())) {
                active.setStatus(VersionStatus.RETIRED);
                ruleVersionRepository.save(active);
            }
        }
        RuleVersionEntity previousActive = set.getActiveVersionId() == null ? null
                : ruleVersionRepository.findById(set.getActiveVersionId()).orElse(null);

        target.setStatus(VersionStatus.ACTIVE);
        ruleVersionRepository.save(target);
        set.setActiveVersionId(target.getId());
        if (target.getId().equals(set.getCanaryVersionId())) {
            set.setCanaryVersionId(null);
            set.setCanaryPercent(0);
        }
        set.touch();
        ruleSetRepository.save(set);
        refreshCache(tenantId, key);
        log.info("租户 {} 规则集 {} 发布 v{}（上一生效版本 {}）", tenantId, key, version,
                previousActive == null ? "-" : previousActive.getVersion());
        return toView(set);
    }

    /** 灰度：percent=0 表示取消灰度；其它值让指定版本对部分 subject 生效。 */
    @Transactional
    public RuleSetDtos.RuleSetView canary(String tenantId, String key, long version, int percent) {
        RuleSetEntity set = requireSet(tenantId, key);
        if (percent < 0 || percent > 100) {
            throw new BizException(ErrorCode.BAD_REQUEST, "灰度比例需在 0..100");
        }
        if (percent == 0) {
            clearCanary(tenantId, set);
            refreshCache(tenantId, key);
            return toView(requireSet(tenantId, key));
        }
        RuleVersionEntity target = requireVersion(tenantId, set, version);
        if (target.getId().equals(set.getActiveVersionId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "目标版本已是生效版本，无需灰度");
        }
        compile(target.getContent());
        for (RuleVersionEntity canary : ruleVersionRepository
                .findByTenantIdAndRuleSetIdAndStatus(tenantId, set.getId(), VersionStatus.CANARY)) {
            if (!canary.getId().equals(target.getId())) {
                canary.setStatus(VersionStatus.RETIRED);
                ruleVersionRepository.save(canary);
            }
        }
        target.setStatus(VersionStatus.CANARY);
        ruleVersionRepository.save(target);
        set.setCanaryVersionId(target.getId());
        set.setCanaryPercent(percent);
        set.touch();
        ruleSetRepository.save(set);
        refreshCache(tenantId, key);
        log.info("租户 {} 规则集 {} 灰度 v{} @{}%", tenantId, key, version, percent);
        return toView(set);
    }

    /** 回滚：切回次新的历史版本（内容不可变，故回滚只是指针操作）。 */
    @Transactional
    public RuleSetDtos.RuleSetView rollback(String tenantId, String key) {
        RuleSetEntity set = requireSet(tenantId, key);
        if (set.getActiveVersionId() == null) {
            throw new BizException(ErrorCode.CONFLICT, "当前没有生效版本，无法回滚");
        }
        RuleVersionEntity current = ruleVersionRepository.findById(set.getActiveVersionId()).orElseThrow();
        RuleVersionEntity previous = ruleVersionRepository
                .findByTenantIdAndRuleSetIdOrderByVersionDesc(tenantId, set.getId()).stream()
                .filter(v -> v.getVersion() < current.getVersion())
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "没有可回滚的历史版本"));

        current.setStatus(VersionStatus.RETIRED);
        ruleVersionRepository.save(current);
        previous.setStatus(VersionStatus.ACTIVE);
        ruleVersionRepository.save(previous);
        set.setActiveVersionId(previous.getId());
        set.touch();
        ruleSetRepository.save(set);
        refreshCache(tenantId, key);
        log.info("租户 {} 规则集 {} 回滚 v{} → v{}", tenantId, key, current.getVersion(), previous.getVersion());
        return toView(set);
    }

    /** 重建缓存（发布/灰度/回滚后调用，或缓存未命中时回源）。 */
    @Transactional(readOnly = true)
    public RuleCache.CachedRuleSet refreshCache(String tenantId, String key) {
        RuleSetEntity set = requireSet(tenantId, key);
        RuleVersionEntity active = set.getActiveVersionId() == null ? null
                : ruleVersionRepository.findById(set.getActiveVersionId()).orElse(null);
        if (active == null) {
            throw new BizException(ErrorCode.CONFLICT, "规则集没有生效版本：" + key);
        }
        CompiledRuleSet activeCompiled = compile(active.getContent());

        Long canaryVersion = null;
        CompiledRuleSet canaryCompiled = null;
        if (set.getCanaryVersionId() != null) {
            RuleVersionEntity canary = ruleVersionRepository.findById(set.getCanaryVersionId()).orElse(null);
            if (canary != null) {
                canaryVersion = canary.getVersion();
                canaryCompiled = compile(canary.getContent());
            }
        }
        RuleCache.CachedRuleSet cached = new RuleCache.CachedRuleSet(key, active.getVersion(), activeCompiled,
                canaryVersion, canaryCompiled, set.getCanaryPercent());
        ruleCache.put(tenantId, key, cached);
        return cached;
    }

    private void clearCanary(String tenantId, RuleSetEntity set) {
        for (RuleVersionEntity canary : ruleVersionRepository
                .findByTenantIdAndRuleSetIdAndStatus(tenantId, set.getId(), VersionStatus.CANARY)) {
            canary.setStatus(VersionStatus.RETIRED);
            ruleVersionRepository.save(canary);
        }
        set.setCanaryVersionId(null);
        set.setCanaryPercent(0);
        set.touch();
        ruleSetRepository.save(set);
    }

    public CompiledRuleSet compile(String content) {
        RuleSetDsl dsl = parser.parse(content);
        validator.validate(dsl);
        return new CompiledRuleSet(dsl);
    }

    public Optional<RuleSetEntity> findSet(String tenantId, String key) {
        return ruleSetRepository.findByTenantIdAndKey(tenantId, key);
    }

    private RuleSetEntity requireSet(String tenantId, String key) {
        return findSet(tenantId, key)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "规则集不存在：" + key));
    }

    private RuleVersionEntity requireVersion(String tenantId, RuleSetEntity set, long version) {
        return ruleVersionRepository.findByTenantIdAndRuleSetIdAndVersion(tenantId, set.getId(), version)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "版本不存在：v" + version));
    }

    private RuleSetDtos.RuleSetView toView(RuleSetEntity set) {
        Long activeVersion = null;
        String mode = null;
        int ruleCount = 0;
        if (set.getActiveVersionId() != null) {
            RuleVersionEntity active = ruleVersionRepository.findById(set.getActiveVersionId()).orElse(null);
            if (active != null) {
                activeVersion = active.getVersion();
                CompiledRuleSet compiled = compile(active.getContent());
                mode = compiled.mode().name();
                ruleCount = compiled.ruleCount();
            }
        }
        Long canaryVersion = null;
        if (set.getCanaryVersionId() != null) {
            canaryVersion = ruleVersionRepository.findById(set.getCanaryVersionId())
                    .map(RuleVersionEntity::getVersion).orElse(null);
        }
        return new RuleSetDtos.RuleSetView(set.getId(), set.getKey(), set.getName(), activeVersion,
                canaryVersion, set.getCanaryPercent(), mode, ruleCount, set.getUpdatedAt());
    }

    private RuleSetDtos.VersionView toView(RuleVersionEntity entity) {
        return new RuleSetDtos.VersionView(entity.getId(), entity.getVersion(), entity.getStatus().name(),
                entity.getNote(), entity.getCreatedAt());
    }
}
