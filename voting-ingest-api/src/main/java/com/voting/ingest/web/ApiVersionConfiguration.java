package com.voting.ingest.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefixo de versao aplicado a todos os controllers deste pacote.
 *
 * <p>O Spring Boot 3 nao tem anotacao de versionamento de API: {@code @RequestMapping} so
 * conhece caminhos. Repetir {@code /api/v1} em cada controller funciona, mas espalha a decisao
 * de versao por N arquivos - lancar a v2 viraria N edicoes, e esquecer uma passaria batido.
 *
 * <p>{@link PathMatchConfigurer#addPathPrefix} concentra a decisao aqui. O preco e que o
 * caminho completo de um endpoint deixa de estar visivel na anotacao dele; em troca, a versao
 * passa a ter um unico lugar onde e definida.
 *
 * <p>Versionamento nativo por anotacao ({@code @RequestMapping(version = "1.1")}) chegou no
 * Spring Framework 7 / Boot 4. Quando este projeto subir de versao, e para la que isto migra.
 */
@Configuration
public class ApiVersionConfiguration implements WebMvcConfigurer {

    /** Versao corrente da API publica. */
    public static final String CURRENT_VERSION = "v1";

    private static final String BASE_PATH = "/api/" + CURRENT_VERSION;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                BASE_PATH,
                org.springframework.web.method.HandlerTypePredicate.forBasePackage(
                        ApiVersionConfiguration.class.getPackageName()));
    }
}
