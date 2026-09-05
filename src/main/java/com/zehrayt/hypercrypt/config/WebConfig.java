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

    // NOT: Varsayılan listede sadece yerel geliştirme originleri vardı
    // (localhost/127.0.0.1). Gerçek üretim frontend'i artık Netlify'da
    // yayında (https://hypercrypt.netlify.app) ve bu origin listede
    // olmadığı için tarayıcı "Fonksiyon" sayfasındaki /api/verify isteğinin
    // preflight'ını "No 'Access-Control-Allow-Origin' header" hatasıyla
    // engelliyordu -- backend gerçekte ayaktaydı, sorun tamamen CORS
    // izinli origin listesindeydi. Üretim adresini ekliyoruz; ayrıca
    // Netlify'nin deploy-preview/branch-preview adresleri (ör.
    // "https://deploy-preview-3--hypercrypt.netlify.app") için de bir
    // joker (wildcard) desen ekliyoruz ki ileride onlar da otomatik çalışsın.
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080,http://127.0.0.1:5500,http://localhost:5500,https://hypercrypt.netlify.app,https://*.netlify.app}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // "allowedOrigins" tam eşleşme ister; joker karakterli
                // Netlify preview adreslerini de kapsayabilmek için
                // "allowedOriginPatterns" kullanıyoruz (tam adresler de
                // burada normal şekilde çalışmaya devam eder).
                .allowedOriginPatterns(allowedOrigins)
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