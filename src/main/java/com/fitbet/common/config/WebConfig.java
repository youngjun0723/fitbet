package com.fitbet.common.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fitbet.common.auth.LoginUserIdArgumentResolver;
import com.fitbet.common.storage.LocalStorageService;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String localDir;

    public WebConfig(@Value("${fitbet.storage.local-dir}") String localDir) {
        this.localDir = localDir;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserIdArgumentResolver());
    }

    /** 로컬 디스크의 업로드 폴더를 /uploads/** URL로 노출 (PRD 3: 정적 리소스 URL 매핑) */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(localDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(LocalStorageService.URL_PREFIX + "**")
                .addResourceLocations(location);
    }
}
