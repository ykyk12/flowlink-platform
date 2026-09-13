package com.flowlink.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 文档：所有业务接口通过 X-API-Key 鉴权。 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "ApiKeyAuth";

    @Bean
    public OpenAPI flowLinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowLink 实时风控决策平台 API")
                        .version("1.1.0")
                        .description("规则 DSL / 版本治理 / 可解释决策引擎 / 决策回放对比。请求头 X-API-Key 为租户凭据。"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")));
    }
}
