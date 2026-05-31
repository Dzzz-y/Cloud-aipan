package net.aipan.dcloud_aipan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;


import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import java.util.List;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@Slf4j
public class InterceptorConfig implements WebMvcConfigurer {


    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                // 1. 统一拦截所有以 /api 开头的请求，万无一失
                .addPathPatterns("/api/**")

                // 2. 排除不需要登录、允许匿名公开访问的接口
                .excludePathPatterns(
                        // 账号相关放行
                        "/api/account/*/register",
                        "/api/account/*/login",
                        "/api/account/*/upload_avatar",

                        // 分享公开页面相关放行
                        "/api/share/*/check_share_code",
                        "/api/share/*/visit",
                        "/api/share/*/detail_no_code",
                        "/api/share/*/detail_with_code"
                );
    }


    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                ObjectMapper objectMapper = jacksonConverter.getObjectMapper();
                SimpleModule simpleModule = new SimpleModule();
                simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
                simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
                objectMapper.registerModule(simpleModule);
                break;
            }
        }
    }


}
