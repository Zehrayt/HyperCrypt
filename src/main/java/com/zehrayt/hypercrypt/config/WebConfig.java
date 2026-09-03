package com.zehrayt.hypercrypt.config;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://127.0.0.1:5500,http://localhost:5500}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    // Frontend, fetch isteklerinde "Accept-Language: tr" veya "Accept-Language: en"
    // header'ini gonderecek (HyperCryptI18n.getLanguage() degerine gore).
    // Bu resolver o header'i okuyup MessageSource'un hangi .properties dosyasini
    // kullanacagina karar veriyor. Header hic gelmezse veya desteklenmeyen bir
    // dil gelirse varsayilan olarak Turkce'ye duser.
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag("tr"));
        resolver.setSupportedLocales(List.of(
                Locale.forLanguageTag("tr"),
                Locale.forLanguageTag("en")));
        return resolver;
    }
}