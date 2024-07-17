package com.michael.limit.management.openApi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class OpenApiConfig {

    RestTemplate restTemplate = new RestTemplate();

    private final static String V2_API_DOCS = "/v2/api-docs";

    private final static String SWAGGER_RESOURCES_CONFIGURATION_UI = "/swagger-resources/configuration/ui";

    private final static String SWAGGER_RESOURCES_CONFIGURATION_SECURITY = "/swagger-resources/configuration/security";

    private final static String SWAGGER_RESOURCES = "/swagger-resources";

    private final static Pattern pattern = Pattern.compile("http[s]*://([^/]+)", Pattern.CASE_INSENSITIVE);

    @Value("${server.port}")
    private String port;

    @Bean
    public OpenAPI usersMicroserviceOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("APIC LIMIT MANAGEMENT SERVICE")
                        .description("This service manages transaction limits for channels")
                        .version("1.0"));
    }

    @GetMapping(V2_API_DOCS)
    @SuppressWarnings("unchecked")
    public Map<String, Object> getV2ApiDocs(HttpServletRequest request) {
        Matcher matcher = pattern.matcher(request.getRequestURL().toString());
        matcher.find();

        Map<String, Object> resp = (Map<String, Object>) restTemplate.getForObject(toLocalSwaggerUrl(V2_API_DOCS),
                Map.class);
        // we have to replace standard host, to requested host. as swagger UI make api
        // requests from this host
        resp.put("host", matcher.group(1));

        return resp;
    }

    @GetMapping(SWAGGER_RESOURCES_CONFIGURATION_UI)
    public Object getSwaggerResourcesConfigurationUi() {
        return restTemplate.getForObject(toLocalSwaggerUrl(SWAGGER_RESOURCES_CONFIGURATION_UI), Object.class);
    }

    @GetMapping(SWAGGER_RESOURCES_CONFIGURATION_SECURITY)
    public Object getSwaggerResourcesConfigurationSecurity() {
        return restTemplate.getForObject(toLocalSwaggerUrl(SWAGGER_RESOURCES_CONFIGURATION_SECURITY), Object.class);
    }


    @GetMapping(SWAGGER_RESOURCES)
    public Object getSwaggerResources() {
        return restTemplate.getForObject(toLocalSwaggerUrl(SWAGGER_RESOURCES), Object.class);
    }

    private String toLocalSwaggerUrl(String path) {
        return "http://0.0.0.0:" + port + path;
    }

    /*
     * public SwaggerController(RestTemplate restTemplate, String port) { super();
     * this.restTemplate = restTemplate; this.port = port; }
     */
}