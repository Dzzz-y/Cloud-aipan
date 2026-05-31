package net.aipan.dcloud_aipan.component;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import com.amazonaws.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


/**
 * MinIO 文件存储引擎实现类
 * 提供基于 S3 协议的文件存储操作，包括桶管理、文件上传下载等功能
 */
@Slf4j
@Component
public class MinIoFileStoreEngine implements StoreEngine {

    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,61}[a-z0-9]$|^[a-z0-9]$");
    private static final int MAX_BUCKET_NAME_LENGTH = 63;
    private static final int MAX_OBJECT_KEY_LENGTH = 1024;
    private static final String CONTENT_TYPE_FORCE_DOWNLOAD = "application/octet-stream";

    @Resource
    private AmazonS3Client amazonS3Client;

    /**
     * 检查存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return true-存在，false-不存在
     */
    @Override
    public boolean bucketExists(String bucketName) {
        if (!validateBucketName(bucketName)) {
            return false;
        }
        return amazonS3Client.doesBucketExistV2(bucketName);
    }

    /**
     * 删除存储桶
     *
     * @param bucketName 存储桶名称
     * @return true-删除成功，false-删除失败或桶不存在
     */
    @Override
    public boolean removeBucket(String bucketName) {
        if (!validateBucketName(bucketName)) {
            return false;
        }
        if (amazonS3Client.doesBucketExistV2(bucketName)) {
            amazonS3Client.deleteBucket(bucketName);
            log.info("成功删除存储桶：{}", bucketName);
            return true;
        }
        log.warn("尝试删除不存在的存储桶：{}", bucketName);
        return false;
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 存储桶名称
     */
    @Override
    public void createBucket(String bucketName) {
        if (!validateBucketName(bucketName)) {
            return;
        }
        if (!amazonS3Client.doesBucketExistV2(bucketName)) {
            amazonS3Client.createBucket(bucketName);
            log.info("成功创建存储桶：{}", bucketName);
        } else {
            log.info("存储桶已存在：{}", bucketName);
        }
    }

    /**
     * 获取所有存储桶列表
     *
     * @return 存储桶列表
     */
    @Override
    public List<Bucket> getAllBucket() {
        return amazonS3Client.listBuckets();
    }

    /**
     * 列出存储桶中的所有对象
     *
     * @param bucketName 存储桶名称
     * @return 对象摘要列表，桶不存在时返回空列表
     */
    @Override
    public List<S3ObjectSummary> listObjects(String bucketName) {
        if (!validateBucketName(bucketName)) {
            return List.of();
        }
        if (bucketExists(bucketName)) {
            return amazonS3Client.listObjects(bucketName).getObjectSummaries();
        }
        log.warn("尝试列出不存在的存储桶中的对象：{}", bucketName);
        return List.of();
    }

    /**
     * 检查对象是否存在
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象键
     * @return true-存在，false-不存在
     */
    @Override
    public boolean doesObjectExist(String bucketName, String objectKey) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return false;
        }
        return bucketExists(bucketName) && amazonS3Client.doesObjectExist(bucketName, objectKey);
    }

    /**
     * 上传文件到存储桶
     *
     * @param bucketName      存储桶名称
     * @param objectKey       对象键
     * @param localFileName   本地文件路径
     * @return true-上传成功，false-上传失败
     */
    @Override
    public boolean upload(String bucketName, String objectKey, String localFileName) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return false;
        }

        if (bucketExists(bucketName)) {
            File file = new File(localFileName);
            if (!file.exists() || !file.isFile()) {
                log.error("文件不存在或不是有效文件：{}", localFileName);
                return false;
            }
            amazonS3Client.putObject(bucketName, objectKey, file);
            log.info("成功上传文件：{}/{}", bucketName, objectKey);
            return true;
        }
        log.error("上传失败，存储桶不存在：{}", bucketName);
        return false;
    }

    /**
     * 上传文件到存储桶
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象键
     * @param file       上传的文件
     * @return true-上传成功，false-上传失败
     */
    @Override
    public boolean upload(String bucketName, String objectKey, MultipartFile file) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return false;
        }

        if (file == null || file.isEmpty()) {
            log.error("上传文件为空");
            return false;
        }

        if (bucketExists(bucketName)) {
            try (InputStream inputStream = file.getInputStream()) {
                ObjectMetadata objectMetadata = new ObjectMetadata();
                if (file.getContentType() != null) {
                    objectMetadata.setContentType(file.getContentType());
                }
                objectMetadata.setContentLength(file.getSize());
                amazonS3Client.putObject(bucketName, objectKey, inputStream, objectMetadata);
                log.info("成功上传文件：{}/{}，大小：{} bytes", bucketName, objectKey, file.getSize());
                return true;
            } catch (IOException e) {
                log.error("上传文件失败：{}/{}, 错误：{}", bucketName, objectKey, e.getMessage(), e);
            }
        } else {
            log.error("上传失败，存储桶不存在：{}", bucketName);
        }
        return false;
    }

    /**
     * 删除对象
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象键
     * @return true-删除成功，false-删除失败
     */
    @Override
    public boolean delete(String bucketName, String objectKey) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return false;
        }

        if (bucketExists(bucketName)) {
            amazonS3Client.deleteObject(bucketName, objectKey);
            log.info("成功删除对象：{}/{}", bucketName, objectKey);
            return true;
        }
        log.warn("尝试在不存在的存储桶中删除对象：{}/{}", bucketName, objectKey);
        return false;
    }

    /**
     * 获取文件下载 URL
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象键
     * @param timeout    超时时间
     * @param unit       时间单位
     * @return 预签名下载 URL，失败时返回 null
     */
    @Override
    public String getDownloadUrl(String bucketName, String objectKey, long timeout, TimeUnit unit) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return null;
        }

        if (!bucketExists(bucketName)) {
            log.warn("生成下载 URL 失败，存储桶不存在：{}", bucketName);
            return null;
        }

        try {
            Date expiration = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
            String url = amazonS3Client.generatePresignedUrl(bucketName, objectKey, expiration).toString();
            log.info("成功生成下载 URL：{}/{}", bucketName, objectKey);
            return url;
        } catch (AmazonServiceException e) {
            log.error("生成下载 URL 失败 (服务异常): {}/{}, 错误码：{}, 错误信息：{}",
                    bucketName, objectKey, e.getErrorCode(), e.getErrorMessage(), e);
            return null;
        } catch (AmazonClientException e) {
            log.error("生成下载 URL 失败 (客户端异常): {}/{}, 错误信息：{}",
                    bucketName, objectKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 下载文件到 HTTP 响应
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象键
     * @param response   HTTP 响应对象
     */
    @Override
    public void download2Response(String bucketName, String objectKey, HttpServletResponse response) {
        if (!validateBucketName(bucketName) || !validateObjectKey(objectKey)) {
            return;
        }

        try (S3Object s3Object = amazonS3Client.getObject(bucketName, objectKey)) {
            String fileName = extractFileName(objectKey);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");

            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encodedFileName);
            response.setContentType(CONTENT_TYPE_FORCE_DOWNLOAD);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            IOUtils.copy(s3Object.getObjectContent(), response.getOutputStream());
            log.info("成功下载文件到响应：{}/{}", bucketName, objectKey);
        } catch (AmazonServiceException e) {
            log.error("下载文件失败 (服务异常): {}/{}, 错误码：{}, 错误信息：{}",
                    bucketName, objectKey, e.getErrorCode(), e.getErrorMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (AmazonClientException e) {
            log.error("下载文件失败 (客户端异常): {}/{}, 错误信息：{}",
                    bucketName, objectKey, e.getMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            log.error("下载文件失败 (IO 异常): {}/{}, 错误信息：{}",
                    bucketName, objectKey, e.getMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    @Override
    public PartListing listMultipart(String bucketName, String objectKey, String uploadId) {
        try {
            ListPartsRequest request = new ListPartsRequest(bucketName, objectKey, uploadId);
            return amazonS3Client.listParts(request);
        } catch (Exception e) {
            log.error("errorMsg={}", e);
            return null;
        }
    }

    @Override
    public InitiateMultipartUploadResult initMultipartUploadTask(String bucketName, String objectKey, ObjectMetadata metadata) {
        try {
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectKey, metadata);
            return amazonS3Client.initiateMultipartUpload(request);
        } catch (Exception e) {
            log.error("errorMsg={}", e);
            return null;
        }
    }

    @Override
    public URL genePreSignedUrl(String bucketName, String objectKey, HttpMethod httpMethod, Date expiration, Map<String, Object> params) {
        try {
            GeneratePresignedUrlRequest genePreSignedUrlReq =
                    new GeneratePresignedUrlRequest(bucketName, objectKey, httpMethod)
                            .withExpiration(expiration);

            // 遍历params作为参数加到genePreSignedUrlReq里面，比如：添加上传ID和分片编号作为请求参数
            // genePreSignedUrlReq.addRequestParameter("uploadId", uploadId);
            // genePreSignedUrlReq.addRequestParameter("partNumber", String.valueOf(i));
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                genePreSignedUrlReq.addRequestParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }

            // 生成并获取预签名URL
            return amazonS3Client.generatePresignedUrl(genePreSignedUrlReq);
        } catch (Exception e) {
            log.error("errorMsg={}", e);
            return null;
        }
    }
    @Override
    public CompleteMultipartUploadResult mergeChunks(String bucketName, String objectKey, String uploadId, List<PartETag> partETags) {
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(bucketName, objectKey, uploadId, partETags);
        return amazonS3Client.completeMultipartUpload(request);
    }

    /**
     * 验证存储桶名称
     *
     * @param bucketName 存储桶名称
     * @return true-验证通过，false-验证失败
     */
    private boolean validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.trim().isEmpty()) {
            log.error("存储桶名称不能为空");
            return false;
        }
        if (bucketName.length() > MAX_BUCKET_NAME_LENGTH) {
            log.error("存储桶名称长度不能超过 {} 个字符", MAX_BUCKET_NAME_LENGTH);
            return false;
        }
        if (!BUCKET_NAME_PATTERN.matcher(bucketName).matches()) {
            log.error("存储桶名称格式不正确：{}", bucketName);
            return false;
        }
        return true;
    }

    /**
     * 验证对象键
     *
     * @param objectKey 对象键
     * @return true-验证通过，false-验证失败
     */
    private boolean validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            log.error("对象键不能为空");
            return false;
        }
        if (objectKey.length() > MAX_OBJECT_KEY_LENGTH) {
            log.error("对象键长度不能超过 {} 个字符", MAX_OBJECT_KEY_LENGTH);
            return false;
        }
        if (objectKey.startsWith("/") || objectKey.endsWith("/")) {
            log.error("对象键不能以斜杠开头或结尾：{}", objectKey);
            return false;
        }
        return true;
    }

    /**
     * 从对象键中提取文件名
     *
     * @param objectKey 对象键
     * @return 文件名
     */
    private String extractFileName(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return "unknown";
        }
        int lastSeparatorIndex = objectKey.lastIndexOf("/");
        if (lastSeparatorIndex >= 0 && lastSeparatorIndex < objectKey.length() - 1) {
            String fileName = objectKey.substring(lastSeparatorIndex + 1);
            return fileName.isEmpty() ? "unknown" : fileName;
        }
        return objectKey;
    }
}
