package net.aipan.dcloud_aipan.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.component.StoreEngine;
import net.aipan.dcloud_aipan.config.MinioConfig;
import net.aipan.dcloud_aipan.dto.FileChunkDTO;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.mapper.FileChunkMapper;
import net.aipan.dcloud_aipan.mapper.StorageMapper;
import net.aipan.dcloud_aipan.model.FileChunkDO;
import net.aipan.dcloud_aipan.model.StorageDO;
import net.aipan.dcloud_aipan.req.FileChunkInitTaskReq;
import net.aipan.dcloud_aipan.req.FileChunkMergeReq;
import net.aipan.dcloud_aipan.req.FileUploadReq;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.FileChunkService;
import net.aipan.dcloud_aipan.util.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class FileChunkServiceImpl implements FileChunkService {

    @Autowired
    private FileChunkMapper fileChunkMapper;

    @Autowired
    private StoreEngine fileStoreEngine;

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private AccountFileService fileService;
    /**
     * 初始化分片上传任务
     * 1.检查存储空间是否足够
     * 2.根据文件名推断文件内容类型
     * 3.初始化分片上传，获取上传id
     * 4.创建上传实体并设置相关属性
     * 5.插入数据库，构建并返回任务信息DTO
     * @param req
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileChunkDTO initFileChunkTask(FileChunkInitTaskReq req) {
        //检查存储空间是否足够
        StorageDO storageDO = storageMapper.selectOne(new QueryWrapper<>(new StorageDO())
                .eq("account_id", req.getAccountId()));
        if (storageDO.getUsedSize() + req.getTotalSize() > storageDO.getTotalSize()) {
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }
        //根据文件名推断文件内容类型
        String objectKey = CommonUtil.getFilePath(req.getFileName());
        String contentType =MediaTypeFactory.getMediaType(objectKey).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        //配置元数据
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        //初始化分片上传,获取上传id
        InitiateMultipartUploadResult uploadResult = fileStoreEngine.initMultipartUploadTask(minioConfig.getBucketName(), objectKey, metadata);
        String uploadId = uploadResult.getUploadId();
        //计算分片数量
        int chunkNum = (int)Math.ceil(req.getTotalSize() * 1.0 / req.getChunkSize());
        //创建上传实体并设置相关属性
        FileChunkDO task = new FileChunkDO();
        task.setBucketName(minioConfig.getBucketName())
                .setChunkNum(chunkNum)
                .setFileName(req.getFileName())
                .setChunkSize(req.getChunkSize())
                .setTotalSize(req.getTotalSize())
                .setIdentifier(req.getIdentifier())
                .setObjectKey(objectKey)
                .setUploadId(uploadId)
                .setAccountId(req.getAccountId());
        //插入数据库,构建并返回给前端任务信息DTO
        fileChunkMapper.insert(task);
        return new FileChunkDTO(task).setFinished( false).setExitPartList(new ArrayList<>());
    }

    /**
     * 临时文件上传
     * @param accountId
     * @param identifier
     * @param partNumber
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String genPreSignUploadUrl(Long accountId, String identifier, int partNumber) {
        FileChunkDO task = fileChunkMapper.selectOne(new QueryWrapper<>(new FileChunkDO())
                .eq("account_id", accountId)
                .eq("identifier", identifier));
        if (task == null){
            throw new BizException(BizCodeEnum.FILE_CHUNK_TASK_NOT_EXISTS);
        }
        //配置预签名过期时间
        DateTime expireTime = DateUtil.offsetMillisecond(new Date(), minioConfig.getPreSignUrlExpireTime().intValue());
        //生成签名URL
        Map<String,Object> params = new HashMap<>();
        params.put("partNumber",partNumber);
        params.put("uploadId", task.getUploadId());
        URL preSignUrl = fileStoreEngine.genePreSignedUrl(task.getBucketName(), task.getObjectKey(), HttpMethod.PUT, expireTime, params);
        return preSignUrl.toString();
    }

    /**
     * 合并分片
     * 获取任务和分片列表,检查是否足够合并
     * 检查存储空间和更新
     * 合并分片
     * 判断合并分片是否成功
     * 存储文件和关联信息到数据库
     * 根据唯一标识符删除相关分片信息
     * @param req
     */
    @Override
    public void mergeFileChunk(FileChunkMergeReq req) {
        //获取任务和分片列表,检查是否足够合并
        FileChunkDO task = fileChunkMapper.selectOne(new QueryWrapper<FileChunkDO>()
                .eq("account_id", req.getAccountId())
                .eq("identifier", req.getIdentifier()));
        if(task== null){
            throw new BizException(BizCodeEnum.FILE_CHUNK_TASK_NOT_EXISTS);
        }
        PartListing partListing = fileStoreEngine.listMultipart(task.getBucketName(), task.getObjectKey(), task.getUploadId());
        List<PartSummary> parts = partListing.getParts();

        if(parts.size() != task.getChunkNum()){
            //上传的分片与记录中的不对应,合并失败！
            throw new BizException(BizCodeEnum.FILE_CHUNK_NOT_ENOUGH);
        }


        //检查存储空间和更新
        StorageDO storageDO = storageMapper.selectOne(new QueryWrapper<>(new StorageDO())
                .eq("account_id", req.getAccountId()));
        long realFileTotalSize = parts.stream().map(PartSummary::getSize).mapToLong(Long::valueOf).sum();
        if (storageDO.getUsedSize() + realFileTotalSize > storageDO.getTotalSize()){
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }
        storageDO.setUsedSize(storageDO.getUsedSize() + realFileTotalSize);
        storageMapper.updateById(storageDO);


        //合并文件
        CompleteMultipartUploadResult result = fileStoreEngine.mergeChunks(task.getBucketName(),
                task.getObjectKey(), task.getUploadId(),
                parts.stream().map(partSummary ->
                                new PartETag(partSummary.getPartNumber(), partSummary.getETag()))
                        .collect(Collectors.toList()));

       //判断是否合并成功
        FileUploadReq fileUploadReq = new FileUploadReq();
        if(result.getETag()!=null){
            fileUploadReq.setAccountId(req.getAccountId())
                    .setFileName(task.getFileName())
                    .setIdentifier(task.getIdentifier())
                    .setParentId(req.getParentId())
                    .setFileSize(realFileTotalSize);
        }
        //存储文件和关系信息
        fileService.saveFileAndAccountFile(fileUploadReq, task.getObjectKey());
        //删除任务记录
        fileChunkMapper.deleteById(task.getId());
        log.info("合并成功！");
    }

    /**
     *查询分片上传进度
     * @param accountId
     * @param identifier
     * @return
     */
    @Override
    public FileChunkDTO listFileChunk(Long accountId, String identifier) {
        //查询任务是否存在
        FileChunkDO task = fileChunkMapper.selectOne(new QueryWrapper<FileChunkDO>()
                .eq("account_id", accountId)
                .eq("identifier", identifier));
         if(task== null){
             throw new BizException(BizCodeEnum.FILE_CHUNK_TASK_NOT_EXISTS);
         }
         FileChunkDTO result = new FileChunkDTO(task);
         //判断服务器是否存在分片
        boolean objectKey = fileStoreEngine.doesObjectExist(task.getBucketName(), task.getObjectKey());
        if(!objectKey){
            //服务器不存在分片->未上传成功;需要返回已经上传的分片概述
            PartListing partListing = fileStoreEngine.listMultipart(task.getBucketName(), task.getObjectKey(), task.getUploadId());
            if(partListing.getParts().size()==task.getChunkNum()){
                //已经上传成功,可以合并
                result.setFinished(true).setExitPartList(partListing.getParts());
            }else{
                //未上传成功,不可以合并
                result.setFinished(false).setExitPartList(partListing.getParts());
            }

        }
        return result;
    }
}
