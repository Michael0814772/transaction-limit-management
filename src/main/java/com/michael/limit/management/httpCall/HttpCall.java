package com.michael.limit.management.httpCall;

import com.michael.limit.management.dto.authentication.ServiceTokenBody;
import com.michael.limit.management.dto.authentication.UserTokenBody;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class HttpCall {

    @Value("${application.api.authentication.user.url}")
    private String userUrl;

    @Value("${application.api.authentication.service.url}")
    private String serviceUrl;

    @Value("${application.http.callout.api.http.method}")
    private HttpMethod apiHttpMethod;

    @Value("${application.channel.id}")
    private String defaultChannel;

    public HashMap<String, Object> userTokens(String userToken, String serviceToken, String serviceIpAddress, String accountNumber, String channelId, String cif) {
        log.info("user token call call...");

        if (channelId == null || channelId.isEmpty() || channelId.equals("0")) {
            channelId = defaultChannel;
        }

        UserTokenBody userTokenBody = new UserTokenBody();

        userTokenBody.setServiceToken(serviceToken);
        userTokenBody.setUserToken(userToken);
        userTokenBody.setServiceIpAddress(serviceIpAddress);
        userTokenBody.setChannelId(channelId);
        userTokenBody.setCifId(cif);
        userTokenBody.setAcctNum(accountNumber);

        log.info("userTokenBody: " + userTokenBody);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.method(apiHttpMethod);
        WebClient.RequestBodySpec bodySpec = uriSpec.uri(userUrl);
        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.bodyValue(userTokenBody);
        headersSpec.header( "Content-Type","application/json");
        WebClient.ResponseSpec responseSpec = headersSpec.header(
                        HttpHeaders.CONTENT_TYPE)
                .accept(MediaType.APPLICATION_JSON)
                .ifNoneMatch("*")
                .retrieve();

        Mono<Object> messageResponse = headersSpec.exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.OK)) {
                return response.bodyToMono(Object.class);
            } else if (response.statusCode().is4xxClientError()) {
                return response.bodyToMono(Object.class);
            } else {
                return response.bodyToMono(Object.class);
            }
        });
        Object message = messageResponse.block();
        return (HashMap<String, Object>) message;
    }

    public HashMap<String, Object> serviceToken(String serviceToken, String serviceIpAddress) {
        log.info("service token call...");

        ServiceTokenBody serviceTokenBody = new ServiceTokenBody();
        serviceTokenBody.setToken(serviceToken);
        serviceTokenBody.setSourceIpAddress(serviceIpAddress);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        WebClient.UriSpec<WebClient.RequestBodySpec> uriSpec = client.method(apiHttpMethod);
        WebClient.RequestBodySpec bodySpec = uriSpec.uri(serviceUrl);
        WebClient.RequestHeadersSpec<?> headersSpec = bodySpec.bodyValue(serviceTokenBody);
        headersSpec.header( "Content-Type","application/json");
        WebClient.ResponseSpec responseSpec = headersSpec.header(
                        HttpHeaders.CONTENT_TYPE)
                .accept(MediaType.APPLICATION_JSON)
                .ifNoneMatch("*")
                .retrieve();

        Mono<Object> messageResponse = headersSpec.exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.OK)) {
                return response.bodyToMono(Object.class);
            } else if (response.statusCode().is4xxClientError()) {
                return response.bodyToMono(Object.class);
            } else {
                return response.bodyToMono(Object.class);
            }
        });
        Object message = messageResponse.block();
        return (HashMap<String, Object>) message;
    }
}
