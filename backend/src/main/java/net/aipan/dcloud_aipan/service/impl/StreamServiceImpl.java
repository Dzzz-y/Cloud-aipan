package net.aipan.dcloud_aipan.service.impl;


import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.config.WebClientConfig;
import net.aipan.dcloud_aipan.service.StreamService;
import net.aipan.dcloud_aipan.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StreamServiceImpl implements StreamService {

    @Autowired
    private WebClientConfig webClientConfig;

    @Autowired
    private WebClient webClient;

    @Override
    public Flux<String> handleChatStream(String token, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        return sendRequest(webClientConfig.getChatStreamPath(),body,token);
    }

    private Flux<String> sendRequest(String path,Map<String, Object> body, String  token){
        String requestBodyJson = JsonUtil.obj2Json(body);
        WebClient.RequestBodySpec requestBodySpec = webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            requestBodySpec.header("token", token);
        }

        return requestBodySpec.bodyValue(requestBodyJson)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(error->log.error("Error: {}", error.getMessage()))
                .doOnComplete(() -> log.info("Request completed."));
    }
}
