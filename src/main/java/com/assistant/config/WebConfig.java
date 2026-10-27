package com.assistant.config;

import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionInterceptor;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionInterceptor sessionInterceptor;

    public WebConfig(SessionInterceptor sessionInterceptor) {
        this.sessionInterceptor = sessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionInterceptor).addPathPatterns("/api/**", "/apps/**", "/configuration");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Modeles de reconnaissance (plusieurs Mo) : telecharges une seule fois par appareil.
        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticatedSessionArgumentResolver());
    }
}
