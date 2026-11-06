package com.webrtcmeetpro.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Path

@Configuration
class WebConfig(private val storageProperties: StorageProperties) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val dir = Path.of(storageProperties.uploadDir).toAbsolutePath().normalize().toUri().toString()
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(if (dir.endsWith("/")) dir else "$dir/")
    }
}
