package com.nexa.api.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.*;
import org.springframework.util.unit.DataSize;

@Configuration
public class LoanUploadConfiguration {
  @Bean
  MultipartConfigElement multipartConfigElement() {
    var factory = new MultipartConfigFactory();
    factory.setMaxFileSize(DataSize.ofMegabytes(5));
    factory.setMaxRequestSize(DataSize.ofMegabytes(16));
    factory.setFileSizeThreshold(DataSize.ofKilobytes(0));
    return factory.createMultipartConfig();
  }
}
