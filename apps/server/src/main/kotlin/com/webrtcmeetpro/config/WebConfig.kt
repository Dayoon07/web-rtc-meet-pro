package com.webrtcmeetpro.config

import com.webrtcmeetpro.config.properties.StorageProperties
import java.nio.file.Path
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val storageProperties: StorageProperties) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val dir = Path.of(storageProperties.uploadDir).toAbsolutePath().normalize().toUri().toString()
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(if (dir.endsWith("/")) dir else "$dir/")
    }
}
