package net.aipan.dcloud_aipan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.component.StoreEngine;
import net.aipan.dcloud_aipan.config.AccountConfig;
import net.aipan.dcloud_aipan.config.MinioConfig;
import net.aipan.dcloud_aipan.dto.AccountDTO;
import net.aipan.dcloud_aipan.dto.StorageDTO;
import net.aipan.dcloud_aipan.enums.AccountRoleEnum;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.mapper.AccountFileMapper;
import net.aipan.dcloud_aipan.mapper.AccountMapper;
import net.aipan.dcloud_aipan.mapper.StorageMapper;
import net.aipan.dcloud_aipan.model.AccountDO;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.model.StorageDO;
import net.aipan.dcloud_aipan.req.AccountLoginReq;
import net.aipan.dcloud_aipan.req.AccountRegisterReq;
import net.aipan.dcloud_aipan.req.FolderCreateReq;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.AccountService;
import net.aipan.dcloud_aipan.util.CommonUtil;
import net.aipan.dcloud_aipan.util.SpringBeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private StoreEngine storeEngine;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    @Lazy
    private AccountFileService accountFileService;

    @Autowired

    private AccountFileMapper accountFileMapper;

    @Override
    public void register(AccountRegisterReq req) {
        /**
         * 1.查询手机号是否重复
         * 2.加密密码
         * 3.插入数据库
         * 4.其它业务操作
         */

        //1.查询手机号是否重复
        List<AccountDO> accountDOList = accountMapper.selectList(new QueryWrapper<AccountDO>().eq("phone", req.getPhone()));
        if(!accountDOList.isEmpty()){
            throw new BizException(BizCodeEnum.ACCOUNT_REPEAT);
        }
        AccountDO accountDO = SpringBeanUtil.copyProperties(req, AccountDO.class);

        //2.加密密码
        String digestAsHex = DigestUtils.md5DigestAsHex((AccountConfig.PASSWORD_SALT + req.getPassword()).getBytes());
        accountDO.setPassword(digestAsHex);
        accountDO.setUsername(req.getName());
        accountDO.setRole(AccountRoleEnum.COMMON.name());

        //3.插入数据库
        accountMapper.insert(accountDO);

        //4. TODO 其它操作
        //创建默认的存储空间
        StorageDO storageDO=new StorageDO();
        storageDO.setAccountId(accountDO.getId());
        storageDO.setUsedSize(0L);
        storageDO.setTotalSize(AccountConfig.DEFAULT_STORAGE_SIZE);
        storageMapper.insert(storageDO);

        //初始化根目录
        FolderCreateReq createRootFolderReq = FolderCreateReq.builder()
                .accountId(accountDO.getId())
                .parentId(AccountConfig.ROOT_PARENT_ID)
                .folderName(AccountConfig.ROOT_FOLDER_NAME)
                .build();
        accountFileService.createFolder(createRootFolderReq);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        String filename = CommonUtil.getFilePath(file.getOriginalFilename());
        storeEngine.upload(minioConfig.getBucketName(), filename, file);
        return minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + filename;
    }

    @Override
    public AccountDTO login(AccountLoginReq req) {
        //处理密码
        String digestAsHex = DigestUtils.md5DigestAsHex((AccountConfig.PASSWORD_SALT + req.getPassword()).getBytes());
        AccountDO accountDO = accountMapper.selectOne(new QueryWrapper<AccountDO>().eq("phone", req.getPhone())
                .eq("password", digestAsHex));
        if(accountDO == null){
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }
        return SpringBeanUtil.copyProperties(accountDO,AccountDTO.class);
    }

    @Override
    public AccountDTO queryDetail(Long id) {
        //账号详细
        AccountDO accountDO = accountMapper.selectById(id);
        AccountDTO accountDTO = SpringBeanUtil.copyProperties(accountDO, AccountDTO.class);
        //获取存储信息
        StorageDO storageDO = storageMapper.selectOne(new QueryWrapper<StorageDO>().eq("account_id", id));
        accountDTO.setStorageDTO(SpringBeanUtil.copyProperties(storageDO, StorageDTO.class));
        //获取文件信息
        AccountFileDO accountFileDO = accountFileMapper.selectOne(new QueryWrapper<AccountFileDO>().eq("account_id", id)
                .eq("parent_id", AccountConfig.ROOT_PARENT_ID));
        accountDTO.setRootFolderId(accountFileDO.getId());
        accountDTO.setRootFolderName(accountFileDO.getFileName());
        return accountDTO;
    }
}
