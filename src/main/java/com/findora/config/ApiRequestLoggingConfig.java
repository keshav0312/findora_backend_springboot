package com.findora.config;

import com.findora.web.ApiRequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ApiRequestLoggingConfig {

    @Bean
    public FilterRegistrationBean<ApiRequestLoggingFilter> apiRequestLoggingFilter() {
        FilterRegistrationBean<ApiRequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new ApiRequestLoggingFilter());
        registration.setName("apiRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
