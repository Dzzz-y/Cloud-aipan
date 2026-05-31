package net.aipan.dcloud_aipan.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.Timeout;
import lombok.Data;
import org.apache.tomcat.jni.Pool;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 配置类
 * 用于配置流式通信的 WebClient 实例，包括基础 URL、超时设置和连接池参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "stream")
public class WebClientConfig {

    /**
     * 流式服务的基础 URL
     * 例如：http://localhost:8080/api
     */
    private String baseUrl;

    /**
     * 聊天流式接口的路径
     * 例如：/chat/stream
     */
    private String chatStreamPath;

    /**
     * 超时配置
     * 包含连接超时、响应超时、读取超时和写入超时的设置
     */
    private Timeout timeout = new Timeout();

    /**
     * 连接池配置
     * 包含最大连接数、空闲时间、生命周期等连接池相关参数
     */
    private Pool pool = new Pool();

    /**
     * 超时配置内部类
     * 定义各种超时时间的配置项
     */
    @Data
    public static class Timeout {
        /**
         * 连接超时时间
         * 建立 TCP 连接的最大等待时间
         */
        private Duration connect;

        /**
         * 响应超时时间
         * 服务器返回响应的最大等待时间
         */
        private Duration response;

        /**
         * 读取超时时间
         * 从连接中读取数据的最大等待时间
         */
        private Duration read;

        /**
         * 写入超时时间
         * 向连接中写入数据的最大等待时间
         */
        private Duration write;
    }

    /**
     * 连接池配置内部类
     * 定义连接池的各项参数配置
     */
    @Data
    public static class Pool {
        /**
         * 最大连接数
         * 连接池中允许的最大活跃连接数量
         */
        private int maxConnections;

        /**
         * 最大空闲时间
         * 连接在池中保持空闲状态的最长时间，超过此时间将被回收
         */
        private Duration maxIdleTime;

        /**
         * 最大生命周期
         * 连接从创建到销毁的最长存活时间
         */
        private Duration maxLifeTime;

        /**
         * 获取连接的超时时间
         * 当连接池中没有可用连接时，请求等待获取连接的最大时间
         */
        private Duration pendingAcquireTimeout;

        /**
         * 后台清理间隔
         * 定期清理过期或无效连接的时间间隔
         */
        private Duration evictInBackground;
    }


    @Bean
    public WebClient webClient(){
        // 创建连接池
        ConnectionProvider provider = ConnectionProvider.builder("stream-connection-pool")
                .maxConnections(pool.getMaxConnections())
                .maxIdleTime(pool.getMaxIdleTime())
                .maxLifeTime(pool.getMaxLifeTime())
                .pendingAcquireTimeout(pool.getPendingAcquireTimeout())
                .evictInBackground(pool.getEvictInBackground())
                .build();

        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.getConnect().toMillis())
                .responseTimeout(timeout.getResponse())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout.getRead().getSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout.getWrite().getSeconds(), TimeUnit.SECONDS))
                );

        // 创建 WebClient
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }



}
