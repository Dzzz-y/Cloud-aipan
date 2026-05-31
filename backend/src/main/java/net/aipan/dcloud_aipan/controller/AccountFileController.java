package net.aipan.dcloud_aipan.controller;

import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.dto.FileChunkDTO;
import net.aipan.dcloud_aipan.dto.FileDownloadDTO;
import net.aipan.dcloud_aipan.dto.FolderTreeNodeDTO;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.req.*;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.FileChunkService;
import net.aipan.dcloud_aipan.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/file/v1")
public class AccountFileController {

    @Autowired
    private AccountFileService accountFileService;

    @Autowired
    private FileChunkService fileChunkService;

    /**
     * 查询文件列表接口
     */
    @GetMapping("/list")
    public JsonData list(@RequestParam(value = "parent_id") Long parentId) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        List<AccountFileDTO> list=accountFileService.listFile(accountId, parentId);
        return JsonData.buildSuccess(list);
    }

    /**
     * 创建文件夹接口
     */
    @PostMapping("/create_folder")
    public JsonData createFolder(@RequestBody FolderCreateReq req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.createFolder(req);
        return JsonData.buildSuccess();
    }

    /**
     * 文件重命名
     */
    @PostMapping("/renameFile")
    public JsonData renameFile(@RequestBody FileUpdateReq  req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.updateFileName(req);
        return JsonData.buildSuccess();
    }

    /**
     * 文件树接口
     */
    @GetMapping("/folder/tree")
    public JsonData folderTree(){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        List<FolderTreeNodeDTO> list=accountFileService.folderTree(accountId);
        return JsonData.buildSuccess(list);
    }

    /**
     * 小文件上传
     */
    @PostMapping("/upload")
    public JsonData upload(FileUploadReq req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.fileUpload(req);
        return JsonData.buildSuccess();
    }
    /**
     * 文件批量移动
     */
    @PostMapping("/move_batch")
    public JsonData moveBatch(@RequestBody FileBatchReq req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.moveBatch(req);
        return JsonData.buildSuccess();
    }
    /**
     * 文件批量删除
     */
    @PostMapping("/delete_batch")
    public JsonData deleteBatch(@RequestBody FileDelReq req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.deleteBatch(req);
        return JsonData.buildSuccess();
    }
    /**
     * 文件复制
     */
    @PostMapping("/copy_batch")
    public JsonData copyBatch(@RequestBody FileBatchReq req) {
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        accountFileService.copyBatch(req);
        return JsonData.buildSuccess();
    }

    /**
     * 秒传文件 true 秒传成功  false 秒传失败(重新调用上传接口)
     */
    @PostMapping("/second_upload")
    public JsonData secondUpload(@RequestBody FileSecondUploadReq req){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        Boolean flag=accountFileService.secondUpload(req);
        return JsonData.buildSuccess(flag);
    }

    /**
     * 大文件上传
     * 1.创建分片上传任务
     */
    @PostMapping("/init_file_chunk_task")
    public JsonData initFileChunkTask(@RequestBody FileChunkInitTaskReq req){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        FileChunkDTO fileChunkDTO= fileChunkService.initFileChunkTask(req);
        return JsonData.buildSuccess(fileChunkDTO);
    }

    /**
     * 大文件上传
     * 2.获取分片上传地址,返回minio临时签名地址
     */
    @GetMapping("/get_file_chunk_upload_url/{identifier}/{partNumber}")
    public JsonData getFileChunkUploadUrl(@PathVariable("identifier") String identifier
            , @PathVariable("partNumber") int partNumber){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        String url=fileChunkService.genPreSignUploadUrl(accountId, identifier, partNumber);
        return JsonData.buildSuccess(url);
    }
    /**
     *大文件上传
     *3.合并分片
     */
    @PostMapping("/merge_file_chunk")
    public JsonData mergeFileChunk(@RequestBody FileChunkMergeReq req){
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        fileChunkService.mergeFileChunk( req);
        return JsonData.buildSuccess();
    }
    /**
     * 查询分片上传进度
     */
    @GetMapping("/chunk_upload_progress/{identifier}")
    public JsonData chunkUploadProgress(@PathVariable("identifier") String identifier){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        FileChunkDTO  fileChunkDTO=fileChunkService.listFileChunk(accountId, identifier);
        return JsonData.buildSuccess(fileChunkDTO);
    }

    /**
     * 根据条件查询文件列表
     */
    @GetMapping("/search")
    public JsonData search(@RequestParam(value = "search") String search){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        List<AccountFileDTO> list=accountFileService.search(accountId,search);
        return JsonData.buildSuccess(list);
    }

    /**
     * 多文件下载url获取
     */
    @PostMapping("/batch_download_url")
    public JsonData batchDownloadUrl(@RequestBody FileDownloadReq req){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        req.setAccountId(accountId);
        List<FileDownloadDTO> fileDownloadDTOList =accountFileService.batchDownloadUrl(req);
        return JsonData.buildSuccess(fileDownloadDTOList);
    }


}
