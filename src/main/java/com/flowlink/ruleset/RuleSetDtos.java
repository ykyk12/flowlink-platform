package com.flowlink.ruleset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 规则集相关 DTO。 */
public final class RuleSetDtos {

    private RuleSetDtos() {
    }

    public record CreateSetRequest(
            @NotBlank @Size(max = 64) String key,
            @NotBlank @Size(max = 128) String name) {
    }

    public record CreateVersionRequest(
            @NotBlank String content,
            @Size(max = 255) String note) {
    }

    public record CanaryRequest(
            @Min(0) @Max(100) int percent) {
    }

    public record RuleSetView(String id,
                              String key,
                              String name,
                              Long activeVersion,
                              Long canaryVersion,
                              int canaryPercent,
                              String activeMode,
                              int activeRuleCount,
                              Instant updatedAt) {
    }

    public record VersionView(String id,
                              long version,
                              String status,
                              String note,
                              Instant createdAt) {
    }

    public record ContentView(String key, long version, String content) {
    }
}
