package net.aipan.dcloud_aipan.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("阿呆 AI 网盘系统 API")
                        .version("1.0")
                        .description("AI 网盘系统接口文档，包含账号管理、文件管理、分享管理等模块")
                        .termsOfService("https://Adai.net")
                        .license(new License().name("Apache 2.0").url("https://Adai.net"))
                        .contact(new Contact()
                                .name("阿呆")
                                .email("15315863305@qq.com")
                                .url("https://Adai.net")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .schemaRequirement("Authorization", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Token，格式：Bearer {token}")
                );
    }
}
