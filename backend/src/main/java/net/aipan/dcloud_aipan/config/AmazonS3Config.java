package net.aipan.dcloud_aipan.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmazonS3Config {

    // 注入Minio配置类，用于获取访问密钥和Endpoint等信息
    @Resource
    private  MinioConfig minioConfig;

    /**
     * 创建并配置Amazon S3客户端
     *
     * @return AmazonS3 实例，用于与Amazon S3服务进行交互
     */
    @Bean(name = "amazonS3Client")
    public AmazonS3 amazonS3Client() {
        // 设置连接时的参数
        ClientConfiguration config = new ClientConfiguration();
        // 设置连接方式为HTTP，可选参数为HTTP和HTTPS
        config.setProtocol(Protocol.HTTP);
        // 设置网络访问超时时间
        config.setConnectionTimeout(5000);
        config.setUseExpectContinue(true);

        // 使用Minio配置中的访问密钥和秘密密钥创建AWS凭证
        AWSCredentials credentials = new BasicAWSCredentials(minioConfig.getAccessKey(), minioConfig.getSecretKey());

        // 设置Endpoint
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder
                .EndpointConfiguration(minioConfig.getEndpoint(), Regions.US_EAST_1.name());

        // 使用以上配置创建并返回Amazon S3客户端实例
        return AmazonS3ClientBuilder.standard()
                .withClientConfiguration(config)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(endpointConfiguration)
                .withPathStyleAccessEnabled(true).build();
    }

}
