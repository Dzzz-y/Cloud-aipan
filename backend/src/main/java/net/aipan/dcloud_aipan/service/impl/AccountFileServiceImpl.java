package net.aipan.dcloud_aipan.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.component.StoreEngine;
import net.aipan.dcloud_aipan.config.MinioConfig;
import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.dto.FileDownloadDTO;
import net.aipan.dcloud_aipan.dto.FolderTreeNodeDTO;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.enums.FileTypeEnum;
import net.aipan.dcloud_aipan.enums.FolderFlagEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.mapper.AccountFileMapper;
import net.aipan.dcloud_aipan.mapper.FileMapper;
import net.aipan.dcloud_aipan.mapper.StorageMapper;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.model.FileDO;
import net.aipan.dcloud_aipan.model.StorageDO;
import net.aipan.dcloud_aipan.req.*;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.AccountService;
import net.aipan.dcloud_aipan.util.CommonUtil;
import net.aipan.dcloud_aipan.util.SpringBeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AccountFileServiceImpl implements AccountFileService {

    @Autowired
    private AccountFileMapper accountFileMapper;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    private StoreEngine fileStoreEngine;

    @Autowired
    @Lazy
    private FileMapper fileMapper;

    @Autowired
    private AccountService accountService;

    @Override
    public List<AccountFileDTO> listFile(Long accountId, Long parentId) {
        List<AccountFileDO> accountFileDOList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountId)
                .eq("parent_id", parentId)
                .orderByDesc("is_dir")
                .orderByDesc("gmt_create"));
        return SpringBeanUtil.copyProperties(accountFileDOList, AccountFileDTO.class);
    }

    @Override
    public Long createFolder(FolderCreateReq req) {
        AccountFileDTO accountFileDTO = AccountFileDTO.builder().accountId(req.getAccountId()).parentId(req.getParentId())
                .fileName(req.getFolderName()).fileType("folder")
                .isDir(FolderFlagEnum.YES.getCode()).build();
        return savaAccountFile(accountFileDTO);
    }

    /**
     * 文件重命名
     * 1.检查文件id是否存在
     * 2.新旧文件名称不能一样
     * 3.保存相关文件关系
     * @param req
     */
    @Override
    public void updateFileName(FileUpdateReq req) {
        //1.检查文件id是否存在
        AccountFileDO accountFileDO = accountFileMapper.selectOne(
                new QueryWrapper<AccountFileDO>().eq("id", req.getFileId())
                        .eq("account_id", req.getAccountId()));
        if(accountFileDO==null){
            log.error("文件不存在,{}",req);
            throw new BizException(BizCodeEnum.FILE_NOT_EXISTS);
            //2.新旧文件名称不能一样
        }else{
            if(Objects.equals(accountFileDO.getFileName(),req.getNewFileName())){
                log.error("文件名称未改变,{}",req);
                throw new BizException(BizCodeEnum.FILE_RENAME_REPEAT);
            }
            //同层文件名称不能重复
            Long selectCount = accountFileMapper.selectCount(new QueryWrapper<AccountFileDO>()
                    .eq("account_id", req.getAccountId())
                    .eq("parent_id", accountFileDO.getParentId())
                    .eq("file_name", req.getNewFileName()));
            if(selectCount>0){
                log.error("文件名称重复,{}",req);
                throw new BizException(BizCodeEnum.FILE_RENAME_REPEAT);
            }else {
                //保存相关文件关系
                accountFileDO.setFileName(req.getNewFileName());
                accountFileMapper.updateById(accountFileDO);
            }
        }
    }

    /**
     * 处理用户与文件的关系
     * 1.检查父文件是否存在
     * 2.检查文件是否重复(若重复自动加后缀)
     * 3.保存用户文件关系
     */
    private Long savaAccountFile(AccountFileDTO accountFileDTO) {
        //1.检查父文件是否存在
        checkParentFileId(accountFileDTO);
        AccountFileDO accountFileDO = SpringBeanUtil.copyProperties(accountFileDTO, AccountFileDO.class);
        //2.检查文件是否重复 aa aa(1) aa(2)
        processFileNameDuplicate(accountFileDO);
        //3.保存用户文件关系
        accountFileMapper.insert(accountFileDO);
        return accountFileDO.getId();
    }

    /**
     * 查询文件数接口
     * 1.查询用户全部文件夹
     * 2.拼装文件树
     */
    @Override
    public List<FolderTreeNodeDTO> folderTree(Long accountId) {
        //查询用户全部文件夹
        List<AccountFileDO> folderList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountId)
                .eq("is_dir", FolderFlagEnum.YES.getCode()));
        if(CollectionUtils.isEmpty(folderList)){
            return List.of();
        }
        //创建map,存储文件夹id为key,文件夹为value
        Map<Long, FolderTreeNodeDTO> folderMap = folderList.stream().collect(Collectors.toMap(AccountFileDO::getId, accountFileDO ->
                FolderTreeNodeDTO.builder()
                        .id(accountFileDO.getId())
                        .parentId(accountFileDO.getParentId())
                        .label(accountFileDO.getFileName())
                        .children(new ArrayList<>())
                        .build()));
        //构建文件树,遍历数据源,为每个文件夹找到其子文件
        for(FolderTreeNodeDTO node:folderMap.values()){
            Long parentId = node.getParentId();
            if(parentId!=null&&folderMap.containsKey(parentId)){
                //获取子文件
                FolderTreeNodeDTO parentNode = folderMap.get(parentId);
                //获取父文件夹的子节点位置
                List<FolderTreeNodeDTO> children = parentNode.getChildren();
                //添加子节点
                children.add(node);
            }
        }
        //过滤根节点
        List<FolderTreeNodeDTO> rootList=folderMap.values().stream()
                .filter(node ->Objects.equals(node.getParentId(),0L))
                .collect(Collectors.toList());
        return rootList;
    }

    /**
     * 文件上传接口
     * 1.上传到minio
     * 2.保存文件关系
     * 3.保存文件关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fileUpload(FileUploadReq req) {
        //判断内存存储是否足够
        boolean storageEnough = checkAndUpdateCapacity(req.getAccountId(),req.getFileSize());
        if (storageEnough){
            //1.上传到minio
            String storeFileObjectKey=storeFile(req);
            //2.保存文件关系 （账号与文件）
            saveFileAndAccountFile(req,storeFileObjectKey);
        }else{
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }

    }

    /**
     * 检查存储空间和更新存储空间
     * @param accountId
     * @param fileSize
     * @return
     */
    public boolean checkAndUpdateCapacity(Long accountId, Long fileSize) {
        StorageDO storageDO = storageMapper.selectOne(new QueryWrapper<StorageDO>().eq("account_id", accountId));
        Long totalSize = storageDO.getTotalSize();
        if (storageDO.getUsedSize()+fileSize<=totalSize){
            storageDO.setUsedSize(storageDO.getUsedSize()+fileSize);
            storageMapper.updateById(storageDO);
            return true;
        }else{
            return false;
        }
    }

    /**
     * 文件批量移动
     * 1.检查被移动的id是否合法
     * 2.检查目标文件夹id是否合法
     * 3.批量移动文件到目标文件夹
     */
    @Override
    public void moveBatch(FileBatchReq req) {
        //1.检查被移动的id是否合法
        List<AccountFileDO> accountFileDOList=checkFileIds(req.getFileIds(),req.getAccountId());
        //2.检查目标文件夹id是否合法
        checkTargetParentIdLegal(req);
        //批量移动文件到目标文件夹
        accountFileDOList.forEach(accountFileDO -> {
            accountFileDO.setParentId(req.getTargetParentId());
        });
        //3.批量移动文件到目标文件夹-重复名处理
        accountFileDOList.forEach(this::processFileNameDuplicate);
        //更新文件相关id为目标文件夹的Id
        accountFileDOList.forEach(accountFileDO -> {
            accountFileMapper.updateById(accountFileDO);
        });
    }

    /**
     * 文件批量删除
     * 检查：文件id数量是否合法 文件是否属于当前用户
     * 判断：文件是否为文件夹，是否需要递归获取文件夹内的文件id，进行批量删除
     * 更新对应账号存储空间的使用情况
     * 批量删除账号映射关系，考虑回收站如何设计
     */
    @Override
    public void deleteBatch(FileDelReq req) {
        //检查：文件id数量是否合法 文件是否属于当前用户
        List<AccountFileDO> accountFileDOList = checkFileIds(req.getFileIds(), req.getAccountId());

        //判断文件是否为文件夹，是否需要递归获取文件夹内的文件id，进行批量删除
        List<AccountFileDO> storeAccountFileDoList = new ArrayList<>();
        findAllAccountFileDOWithRecur(accountFileDOList,storeAccountFileDoList,false);

        //拿到->全部文件Id的列表
        List<Long> allFileIdList = storeAccountFileDoList.stream().map(AccountFileDO::getId).collect(Collectors.toList());

        //更新对应账号存储空间的使用情况
        long allFileSize = storeAccountFileDoList.stream()
                .filter(file -> file.getIsDir().equals(FolderFlagEnum.NO.getCode()))
                .mapToLong(AccountFileDO::getFileSize).sum();
        StorageDO storageDO = storageMapper.selectOne(new QueryWrapper<StorageDO>().eq("account_id", req.getAccountId()));
        storageDO.setUsedSize(storageDO.getUsedSize()-allFileSize);
        //TODO 加一个分布式锁 使用account_id锁粒度
        storageMapper.updateById(storageDO);
        //批量删除账号映射关系，考虑回收站如何设计
        accountFileMapper.deleteBatchIds(allFileIdList);
    }

    /**
     * 文件批量复制
     * 1.检查被复制的id是否合法
     * 2.检查目标文件夹id是否合法
     * 3.执行拷贝,递归进行查找
     * 4.计算存储空间大小，进行判断
     * 5.批量保存文件关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyBatch(FileBatchReq req) {
        //1.检查被复制的id是否合法
        List<AccountFileDO> accountFileDOList=checkFileIds(req.getFileIds(),req.getAccountId());
        //2.检查目标文件夹id是否合法
        checkTargetParentIdLegal(req);
        //3.执行批量拷贝,递归进行查找
        List<AccountFileDO> newAccountFileDOList=findBatchCopyFileWithRecur(accountFileDOList,req.getTargetParentId());
        //4.计算存储空间大小，进行判断
        //过滤出文件
        Long totalFileSize  =newAccountFileDOList.stream()
                .filter(file -> file.getIsDir().equals(FolderFlagEnum.NO.getCode()))
                .mapToLong(AccountFileDO::getFileSize).sum();

        if (!checkAndUpdateCapacity(req.getAccountId(),totalFileSize)){
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }else{
            //存储
            accountFileMapper.insertFileBatch(newAccountFileDOList);

        }
    }

    /**
     * 文件秒传
     * 1.检查文件是否存在
     * 2.检查空间是否足够
     * 3.建立关系
     * @param req
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean secondUpload(FileSecondUploadReq req) {
        //1.检查文件是否存在
        FileDO fileDO = fileMapper.selectOne(new QueryWrapper<FileDO>().eq("identifier", req.getIdentifier()));
        //2.检查空间是否足够
        if (fileDO!=null&&checkAndUpdateCapacity(req.getAccountId(),fileDO.getFileSize())){
            //处理文件秒传
            String fileSuffix = fileDO.getFileSuffix() != null ? fileDO.getFileSuffix()
                    : CommonUtil.getFileSuffix(req.getFileName());
            AccountFileDTO accountFileDTO =new AccountFileDTO();
            accountFileDTO.setAccountId(req.getAccountId());
            accountFileDTO.setFileId(fileDO.getId());
            accountFileDTO.setParentId(req.getParentId());
            accountFileDTO.setFileName(req.getFileName());
            accountFileDTO.setFileSize(fileDO.getFileSize());
            accountFileDTO.setFileType(FileTypeEnum.fromExtension(fileSuffix).getType());
            accountFileDTO.setFileSuffix(fileSuffix);
            accountFileDTO.setDel(false);
            accountFileDTO.setIsDir(FolderFlagEnum.NO.getCode());
            //处理文件关系并保存
            savaAccountFile(accountFileDTO);
            return true;
        }
        return false;
    }


    /**
     * 批量查找文件
     * 1.递归处理文件，生成新的文件关系
     * @param accountFileDOList
     * @param targetParentId
     * @return
     */
    public List<AccountFileDO> findBatchCopyFileWithRecur(List<AccountFileDO> accountFileDOList, Long targetParentId) {
        List<AccountFileDO> newAccountFileDOList = new ArrayList<>();
        //递归遍历   遍历复制其子文件
        accountFileDOList.forEach(accountFileDO -> doCopyChildRecord(accountFileDO,newAccountFileDOList,targetParentId));
        return newAccountFileDOList;
    }

    private void doCopyChildRecord(AccountFileDO accountFileDO, List<AccountFileDO> newAccountFileDOList, Long targetParentId) {
        //保留旧的id,方便查找子文件
        Long oldAccountFileId=accountFileDO.getId();
        //创建新的记录进行存储
        accountFileDO.setId(IdUtil.getSnowflakeNextId());
        accountFileDO.setParentId(targetParentId);
        accountFileDO.setGmtCreate(null);
        accountFileDO.setGmtModified(null);
        //处理重复文件夹,添加入容器
        processFileNameDuplicate(accountFileDO);
        newAccountFileDOList.add(accountFileDO);

        //递归处理    判断是文件还是文件夹
        if (accountFileDO.getIsDir().equals(FolderFlagEnum.YES.getCode())){
            //为文件夹，进行递归处理并查询其下所有子文件
            List<AccountFileDO> childAccountFileDOList =findChildAccountFile(accountFileDO.getAccountId(),oldAccountFileId);
            if(CollectionUtils.isEmpty(childAccountFileDOList)){
                return;
            }
            childAccountFileDOList.forEach(childAccountFileDO -> doCopyChildRecord(childAccountFileDO,newAccountFileDOList,accountFileDO.getId()));
        }
    }

    private List<AccountFileDO> findChildAccountFile(Long accountId, Long oldAccountFileId) {
        return accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountId)
                .eq("parent_id", oldAccountFileId));
    }

    /**
     * 检查目标文件夹id是否合法
     * 1.目标必须为文件夹
     * 2.操作的文件列表不能包含目标文件夹id
     * @param req
     */
    private void checkTargetParentIdLegal(FileBatchReq req) {
        //目标必须为文件夹！
        AccountFileDO targetAccountFileDO=accountFileMapper.selectOne(new QueryWrapper<AccountFileDO>()
                .eq("id", req.getTargetParentId())
                .eq("is_dir", FolderFlagEnum.YES.getCode())
                .eq("account_id", req.getAccountId()));
        if (targetAccountFileDO==null){
            log.error("目标非文件夹！");
            throw new BizException(BizCodeEnum.FILE_TARGET_PARENT_ILLEGAL);
        }
        /**
         * 要操作的文件列表不能包含目标文件夹id
         * 1.递归查询需要操作的文件和文件夹
         * 2.判断目标文件夹id是否在列表中
         */
        List<AccountFileDO> prepareAccountFileDOList= accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .in("id", req.getFileIds())
                .eq("account_id", req.getAccountId()));
        //定义一个容器,存储全部文件夹，包括子文件夹
        List<AccountFileDO> allAccountFileDOList=new ArrayList<>();
        //递归查找
        findAllAccountFileDOWithRecur(prepareAccountFileDOList,allAccountFileDOList,false);
        //判断是否在里面
        if(allAccountFileDOList.stream().anyMatch(accountFileDO -> Objects.equals(accountFileDO.getId(), req.getTargetParentId()))){
            log.error("目标文件夹不能为文件夹，必须为文件！");
            throw new BizException(BizCodeEnum.FILE_TARGET_PARENT_ILLEGAL);
        }
    }

    /**
     *  递归查询全部文件夹，包括子文件夹
     * @param prepareAccountFileDOList
     * @param allAccountFileDOList
     * @param onlyFolder
     */
    public void findAllAccountFileDOWithRecur(List<AccountFileDO> prepareAccountFileDOList, List<AccountFileDO> allAccountFileDOList, boolean onlyFolder) {
        for(AccountFileDO accountFileDO:prepareAccountFileDOList){
            if(Objects.equals(accountFileDO.getIsDir(), FolderFlagEnum.YES.getCode())){
                List<AccountFileDO> accountFileDOList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                        .eq("parent_id", accountFileDO.getId()));
                findAllAccountFileDOWithRecur(accountFileDOList,allAccountFileDOList,onlyFolder);
            }
            if(!onlyFolder||Objects.equals(accountFileDO.getIsDir(), FolderFlagEnum.YES.getCode())){
                allAccountFileDOList.add(accountFileDO);
            }
        }
    }

    /**
     * 检查被移动的id是否合法
     * @param fileIds
     * @param accountId
     * @return
     */
    public List<AccountFileDO> checkFileIds(List<Long> fileIds, Long accountId) {
        List<AccountFileDO> accountFileDOList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountId)
                .in("id", fileIds));
        if(accountFileDOList.size()!=fileIds.size()){
            log.error("文件id数量不合法");
            throw new BizException(BizCodeEnum.FILE_BATCH_UPDATE_ERROR);
        }
        return accountFileDOList;
    }

    @Override
    public void saveFileAndAccountFile(FileUploadReq req, String storeFileObjectKey) {
        //3.保存文件关系
        FileDO fileDO = saveFile(req,storeFileObjectKey);
        //4.保存用户文件关系
        AccountFileDTO accountFileDTO=AccountFileDTO.builder()
                .accountId(req.getAccountId())
                .fileId(fileDO.getId())
                .fileName(req.getFileName())
                .isDir(FolderFlagEnum.NO.getCode())
                .fileSize(req.getFileSize())
                .fileSuffix(fileDO.getFileSuffix())
                .fileType(FileTypeEnum.fromExtension(fileDO.getFileSuffix()).getType())
                .parentId(req.getParentId())
                .build();
        savaAccountFile(accountFileDTO);
    }

    private FileDO saveFile(FileUploadReq req, String storeFileObjectKey) {
        FileDO fileDO =new FileDO();
        fileDO.setFileName(req.getFileName());
        fileDO.setFileSuffix(CommonUtil.getFileSuffix(req.getFileName()));
        fileDO.setFileSize(req.getFileSize());
        fileDO.setIdentifier(req.getIdentifier());
        fileDO.setObjectKey(storeFileObjectKey);
        fileMapper.insert(fileDO);
        return fileDO;
    }

    private String storeFile(FileUploadReq req) {
        String objectKey = CommonUtil.getFilePath(req.getFileName());
        fileStoreEngine.upload(minioConfig.getBucketName(),objectKey,req.getFile());
        return objectKey;
    }

    /**
     * 检查父文件是否存在
     * @param accountFileDTO
     */
    private void checkParentFileId(AccountFileDTO accountFileDTO) {
        if(accountFileDTO.getParentId()!=0){
            AccountFileDO accountFileDO = accountFileMapper.selectOne(
                    new QueryWrapper<AccountFileDO>().eq("id", accountFileDTO.getParentId())
                            .eq("account_id", accountFileDTO.getAccountId()));
            if(accountFileDO==null){
                throw new BizException(BizCodeEnum.FILE_NOT_EXISTS);
            }

        }
    }

    /**
     * 检查文件是否重复(若重复自动加后缀)
     * 文件夹重复与文件名重复处理规则不同
     * @param accountFileDO
     */
    public Long processFileNameDuplicate(AccountFileDO accountFileDO) {
        Long selectCount = accountFileMapper.selectCount(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountFileDO.getAccountId())
                .eq("parent_id", accountFileDO.getParentId())
                .eq("file_name", accountFileDO.getFileName())
                .eq("is_dir", accountFileDO.getIsDir()));
        if(selectCount>0){
            if(accountFileDO.getIsDir()==FolderFlagEnum.YES.getCode()){
                //文件夹重复
                accountFileDO.setFileName(accountFileDO.getFileName()+"("+selectCount+")");
            }else{
                //文件名重复
                String[] split = accountFileDO.getFileName().split("\\.");
                accountFileDO.setFileName(split[0]+"("+selectCount+")"+"."+split[1]);
            }
        }
        return selectCount;
    }

    /**
     * 根据条件查询文件列表
     * @param accountId
     * @param search
     * @return
     */
    @Override
    public List<AccountFileDTO> search(Long accountId, String search) {
        List<AccountFileDO> accountFileDOList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", accountId)
                .like("file_name", search)
                .orderByDesc("is_dir")
                .orderByDesc("gmt_create").last("limit 30")
        );
        return SpringBeanUtil.copyProperties(accountFileDOList,AccountFileDTO.class);
    }

    /**
     * 批量下载 url获取
     * @param req
     * @return
     */
    @Override
    public List<FileDownloadDTO> batchDownloadUrl(FileDownloadReq req) {
        //获取下载的文件对象，不能是文件夹
        List<AccountFileDO> accountFileDOList = accountFileMapper.selectList(new QueryWrapper<AccountFileDO>()
                .eq("account_id", req.getAccountId())
                .eq("is_dir", FolderFlagEnum.NO.getCode())
                .in("id", req.getFileIds()));

        List<FileDownloadDTO> list =new ArrayList<>();

        for(AccountFileDO accountFileDO:accountFileDOList){
            String objectKey = fileMapper.selectOne(new QueryWrapper<FileDO>().eq("id", accountFileDO.getFileId())).getObjectKey();
            //获取下载url
            String downloadUrl = fileStoreEngine.getDownloadUrl(minioConfig.getBucketName(),
                    objectKey, minioConfig.getPreSignUrlExpireTime(), TimeUnit.MILLISECONDS);
            FileDownloadDTO fileDownloadDTO = new FileDownloadDTO(accountFileDO.getFileName(), downloadUrl);
            list.add(fileDownloadDTO);
        }
        return list;
    }

}
