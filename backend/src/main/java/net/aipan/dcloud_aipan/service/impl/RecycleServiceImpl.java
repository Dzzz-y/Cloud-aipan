package net.aipan.dcloud_aipan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.enums.FolderFlagEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.mapper.AccountFileMapper;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.req.RecycleDelReq;
import net.aipan.dcloud_aipan.req.RecycleRestoreReq;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.RecycleService;
import net.aipan.dcloud_aipan.util.SpringBeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecycleServiceImpl implements RecycleService {
    @Autowired
    private AccountFileMapper accountFileMapper;

    @Autowired
    private AccountFileService accountFileService;

    @Override
    public List<AccountFileDTO> listRecycleFiles(Long accountId) {
        List<AccountFileDO> recycleList = accountFileMapper.selectRecycleFiles(accountId,null);

        //如果是文件夹,则只显示文件夹,不显示里面的其他子文件
        List<Long> fileIds=recycleList.stream().map(AccountFileDO::getFileId).toList();
        //需要提取全部删除文件的Id,然后过滤下，如果某个文件的父id在这个文件Id的集合里面，则不显示
        List<AccountFileDO> accountFileDOS = recycleList.stream().filter(accountFileDO ->
                        !fileIds.contains(accountFileDO.getParentId()))
                .collect(Collectors.toList());
        return SpringBeanUtil.copyProperties(accountFileDOS, AccountFileDTO.class);
    }


    /**
     * 删除回收站文件
     * 文件Id 数量是否合法
     * 判断文件是否为文件夹,若为文件夹需要递归获取子文件,然后批量删除
     * 彻底删除文件
     * @param req
     * @return
     */
    @Override
    public void delete(RecycleDelReq req) {
        //文件Id数量是否合法
        List<AccountFileDO> records = accountFileMapper.selectRecycleFiles(req.getAccountId(), req.getFileIdList());
        if(records.size()!=req.getFileIdList().size()){
            throw new BizException(BizCodeEnum.FILE_DEL_BATCH_ILLEGAL);
        }
        //判断文件是否为文件夹,若为文件夹需要递归获取子文件,然后批量删除
        List<AccountFileDO> allRecords=new ArrayList<>();
        findAllAccountFileDOWithRecur(allRecords,records,false);
        //彻底删除文件
        List<Long> recycleFileIds = allRecords.stream().map(AccountFileDO::getId).toList();
        accountFileMapper.deleteRecycleFiles(recycleFileIds);

    }

    /**
     * 回复删除文件
     * 文件Id 数量是否合法
     * 还原前的父文件和当前文件夹是否有重复名称的文件和文件夹
     * 判断文件是否为文件夹,若为文件夹需要递归获取子文件
     * 检查空间是否充足
     * 批量还原文件
     * @param req
     */
    @Override
    public void restore(RecycleRestoreReq req) {
        //文件Id 数量是否合法
        List<AccountFileDO> accountFileDOList = accountFileMapper.selectRecycleFiles(req.getAccountId(), req.getFileIds());
        if(accountFileDOList.size()!=req.getFileIds().size()){
            throw new BizException(BizCodeEnum.FILE_RECYCLE_ILLEGAL);
        }
        //还原前的父文件和当前文件夹是否有重复名称的文件和文件夹
        accountFileDOList.forEach(accountFileDO -> {
            Long selectCount = accountFileService.processFileNameDuplicate(accountFileDO);
            if(selectCount>0){
                accountFileMapper.updateRecycleFileById(accountFileDO.getId(),accountFileDO.getFileName());
            }
        });
        //判断文件是否为文件夹,若为文件夹需要递归获取子文件
        List<AccountFileDO> allRecords=new ArrayList<>();
        findAllAccountFileDOWithRecur(allRecords,accountFileDOList,true);
        //检查空间是否充足（按文件大小计算）
        long totalFileSize = allRecords.stream()
                .filter(f -> Objects.equals(f.getIsDir(), FolderFlagEnum.NO.getCode()))
                .mapToLong(AccountFileDO::getFileSize).sum();
        if(!accountFileService.checkAndUpdateCapacity(req.getAccountId(), totalFileSize)){
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }
        //批量还原文件
        List<Long> allFileIds = allRecords.stream().map(AccountFileDO::getId).toList();
        accountFileMapper.restoreFiles(allFileIds);
    }

    /**
     * 递归获取所有文件
     * @param allRecords
     * @param records
     * @param
     */
    private void findAllAccountFileDOWithRecur(List<AccountFileDO> allRecords, List<AccountFileDO> records, boolean onlyFolder) {
        for(AccountFileDO accountFileDO:records){
            if(Objects.equals(accountFileDO.getIsDir(), FolderFlagEnum.YES.getCode())){
                List<AccountFileDO> childAccountFileDOList = accountFileMapper.selectRecycleChildFiles(accountFileDO.getId(),accountFileDO.getAccountId());
                findAllAccountFileDOWithRecur(allRecords,childAccountFileDOList,onlyFolder);
            }
            if(!onlyFolder||Objects.equals(accountFileDO.getIsDir(), FolderFlagEnum.YES.getCode())){
                allRecords.add(accountFileDO);
            }
        }
    }
}
