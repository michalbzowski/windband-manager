package pl.michalbzowski.windband.config;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${superset.public-url:https://superset.michalbzowski.pl}")
    private String supersetPublicUrl;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        String publicHost = extractHost(supersetPublicUrl);

        // Configure timeouts at the HttpClient 5 level (10s connect, 30s response)
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();

        // Apache HttpClient 5 with a custom interceptor that strips the port
        // from the Host header. Superset validates Host against SERVER_NAME
        // and rejects requests with "hostname:port" when SERVER_NAME has no port.
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .addRequestInterceptorFirst((request, entity, context) -> {
                    if (publicHost != null) {
                        request.setHeader("Host", publicHost);
                    }
                })
                .build();

        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    private String extractHost(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
