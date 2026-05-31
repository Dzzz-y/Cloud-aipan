package net.aipan.dcloud_aipan.controller;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.DcloudAipanApplication;
import net.aipan.dcloud_aipan.service.StreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.awt.*;
import java.net.InetAddress;

@Slf4j
@RestController
@RequestMapping("/ai/chat")
public class StreamController {
    @Autowired
    private StreamService streamService;

    @RequestMapping(value= "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestHeader("token")String token ,@RequestBody String message){
        return streamService.handleChatStream(token,message);
    }
}
