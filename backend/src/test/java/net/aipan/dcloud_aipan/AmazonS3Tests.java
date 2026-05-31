package net.aipan.dcloud_aipan;

import cn.hutool.core.date.DateUtil;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Date;
import java.util.Optional;

@SpringBootTest
@Slf4j
class AmazonS3ClientTests {

    @Autowired
    private AmazonS3Client amazonS3Client;

    // ============bucket相关操作===========

    /**
     * 判断bucket是否存在
     */
    @Test
    public void testBucketExist() {
        boolean exist = amazonS3Client.doesBucketExist("aipan");
        log.info("bucket是否存在：{}", exist);
    }

    /**
     * 创建bucket
     */
    @Test
    public void testCreateBucket() {
        amazonS3Client.createBucket("aipan");
    }

    /**
     * 删除bucket
     */
    @Test
    public void testDeleteBucket() {
        amazonS3Client.deleteBucket("ai-pan1");
    }

    /**
     * 获取所有bucket
     */
    @Test
    public void testListBucket() {
        amazonS3Client.listBuckets().forEach(bucket -> {
            log.info("bucket名称：{}", bucket.getName());
        });
    }

    /**
     * 获取bucket详情
     */
    @Test
    public void testGetBucket() {
        String bucketName = "aipan";
        Optional<Bucket> first = amazonS3Client.listBuckets().stream().filter(bucket -> bucket.getName().equals(bucketName)).findFirst();
        if (first.isPresent()) {
            Bucket bucket = first.get();
            log.info("bucket name: {}", bucket.getName());
        } else {
            log.info("bucket name: {}", bucketName + "不存在");
        }

    }

    // ============文件相关操作===========

    /**
     * 上传单个文件，直接写入文本
     */
    @Test
    public void testUploadFile() {
        PutObjectResult putObject = amazonS3Client.putObject("aipan", "test.txt", "hello world");
        log.info("putObject:{}", putObject);
    }

    /**
     * 上传单个文件，直接写入文本
     */
    @Test
    public void testUploadFile2() {
        amazonS3Client.putObject("aipan", "/png/1.png", new File("C:\\Users\\Mryy\\Pictures\\a451f9837755d75f25e2d0691206442f.jpg"));
    }

    /**
     * 上传文件 包括文件夹路径 不带斜杠 都一样
     */
    @Test
    public void testUploadFileWithDir1() {
        amazonS3Client.putObject("ai-pan", "aa/bb/test3.txt", new File("C:\\Users\\86133\\Desktop\\姜明明\\640.png"));
    }

    /**
     * 上传文件 包括文件夹路径 带斜杠 都一样
     */
    @Test
    public void testUploadFileWithDir2() {
        amazonS3Client.putObject("aipan", "/a/b/test4.txt", new File("/Users/xdclass/Desktop/dpan.sql"));
    }

    /**
     * 上传文件，输入流的方式  带上文件元数据
     */
    @Test
    @SneakyThrows
    public void testUploadFileWithMetadata() {
        try (FileInputStream fileInputStream = new FileInputStream("\\E:\\Time\\1.txt");) {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType("text/plain");
            amazonS3Client.putObject("aipan", "/txt/test.txt", fileInputStream, objectMetadata);
        }
    }



    /**
     * 获取文件
     */
    @Test
    @SneakyThrows
    public void testGetObject() {
        try (FileOutputStream fileOutputStream = new FileOutputStream(new File("\\E:\\Time"));) {
            S3Object s3Object = amazonS3Client.getObject("aipan", "/txt/test.txt");
            s3Object.getObjectContent().transferTo(fileOutputStream);
        }
    }

    /**
     * 删除文件
     */
    @Test
    public void testDeleteObject() {
        amazonS3Client.deleteObject("ai-pan", "/meta/test5.txt");
    }

    /**
     * 生成文件访问地址
     */
    @Test
    public void testGeneratePresignedUrl() {
        // 预签名url过期时间(ms)
        long PRE_SIGN_URL_EXPIRE = 60 * 10 * 1000L;
        // 计算预签名url的过期日期
        Date expireDate = DateUtil.offsetMillisecond(new Date(), (int) PRE_SIGN_URL_EXPIRE);
        // 创建生成预签名url的请求，并设置过期时间和HTTP方法, withMethod是生成的URL访问方式
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest("ai-pan", "/1.png")
                .withExpiration(expireDate).withMethod(HttpMethod.GET);

        // 生成预签名url
        URL preSignedUrl = amazonS3Client.generatePresignedUrl(request);

        // 输出预签名url
        System.out.println(preSignedUrl.toString());
    }
}
