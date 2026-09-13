package com.flowlink.decision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 批量评估请求（上限由 app.engine.max-batch-size 控制）。 */
public record BatchEvaluateRequest(@NotEmpty @Valid List<EvaluateRequest> items) {
}
