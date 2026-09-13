package com.flowlink.ruleset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleVersionRepository extends JpaRepository<RuleVersionEntity, String> {

    List<RuleVersionEntity> findByTenantIdAndRuleSetIdOrderByVersionDesc(String tenantId, String ruleSetId);

    Optional<RuleVersionEntity> findByTenantIdAndRuleSetIdAndVersion(String tenantId, String ruleSetId, long version);

    Optional<RuleVersionEntity> findFirstByTenantIdAndRuleSetIdOrderByVersionDesc(String tenantId, String ruleSetId);

    List<RuleVersionEntity> findByTenantIdAndRuleSetIdAndStatus(String tenantId, String ruleSetId, VersionStatus status);
}
