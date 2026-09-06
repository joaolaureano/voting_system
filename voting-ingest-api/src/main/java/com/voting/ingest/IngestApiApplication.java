package com.voting.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Borda de entrada do sistema: recebe votos por HTTP e os entrega ao Kafka.
 *
 * <p>Este modulo e so adaptador. Toda a regra vive em voting-domain e voting-application; se
 * uma decisao de negocio aparecer num controller ou numa classe de configuracao daqui, ela
 * esta no lugar errado.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IngestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestApiApplication.class, args);
    }
}
