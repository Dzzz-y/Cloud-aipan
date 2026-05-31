package net.aipan.dcloud_aipan.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;



@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucketName;

    private String avatarBucketName;

    //预签名的URL过期时间
    private Long preSignUrlExpireTime = 60 * 10 * 1000L;

//    @Bean
//    public MinioClient minioClient() {
//        return MinioClient.builder()
//                .endpoint(endpoint)
//                .credentials(accessKey, secretKey)
//                .build();
//    }
}
