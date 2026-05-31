package net.aipan.dcloud_aipan.service;

import net.aipan.dcloud_aipan.dto.FileChunkDTO;
import net.aipan.dcloud_aipan.req.FileChunkInitTaskReq;
import net.aipan.dcloud_aipan.req.FileChunkMergeReq;


public interface FileChunkService {

    /**
     * 初始化分片上传任务
     * @param req
     * @return
     */
    FileChunkDTO initFileChunkTask(FileChunkInitTaskReq req);

    /**
     * 获取临时上传地址
     * @param accountId
     * @param identifier
     * @param partNumber
     * @return
     */
    String genPreSignUploadUrl(Long accountId, String identifier, int partNumber);


    /**
     * 合并分片
     * @param req
     */
    void mergeFileChunk(FileChunkMergeReq req);

    /**
     * 查询分片列表
     * @param accountId
     * @param identifier
     * @return
     */
    FileChunkDTO listFileChunk(Long accountId, String identifier);
}
