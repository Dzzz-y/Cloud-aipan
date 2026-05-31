package net.aipan.dcloud_aipan.service;

import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.dto.FileDownloadDTO;
import net.aipan.dcloud_aipan.dto.FolderTreeNodeDTO;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.req.*;

import java.util.List;


public interface AccountFileService {
    /**
     * 查询文件列表接口
     */

    List<AccountFileDTO> listFile(Long accountId, Long parentId);

    /**
     * 创建文件夹接口
     *
     * @param req
     */
    Long createFolder(FolderCreateReq req);

    /**
     * 文件重命名
     *
     * @param req
     */
    void updateFileName(FileUpdateReq req);

    /**
     * 文件树接口
     *
     * @param accountId
     * @return
     */
    List<FolderTreeNodeDTO> folderTree(Long accountId);

    /**
     * 小文件上传接口
     *
     * @param req
     */
    void fileUpload(FileUploadReq req);

    /**
     * 文件批量移动
     */
    void moveBatch(FileBatchReq req);


    /**
     * 文件批量删除
     *
     * @param req
     */
    void deleteBatch(FileDelReq req);

    void copyBatch(FileBatchReq req);

    /**
     * 文件秒传
     *
     * @param req
     */
    Boolean secondUpload(FileSecondUploadReq req);

    /**
     * 文件上传
     *
     * @param req
     * @param storeFileObjectKey
     */
    void saveFileAndAccountFile(FileUploadReq req, String storeFileObjectKey);

    /**
     * 检查文件id
     *
     * @param fileIds
     * @param accountId
     * @return
     */
    List<AccountFileDO> checkFileIds(List<Long> fileIds, Long accountId);

    void findAllAccountFileDOWithRecur(List<AccountFileDO> prepareAccountFileDOList, List<AccountFileDO> allAccountFileDOList, boolean onlyFolder);

    List<AccountFileDO> findBatchCopyFileWithRecur(List<AccountFileDO> accountFileDOList, Long targetParentId);

    boolean checkAndUpdateCapacity(Long accountId, Long fileSize);

    Long processFileNameDuplicate(AccountFileDO accountFileDO);

    List<AccountFileDTO> search(Long accountId, String search);

    List<FileDownloadDTO> batchDownloadUrl(FileDownloadReq req);
}