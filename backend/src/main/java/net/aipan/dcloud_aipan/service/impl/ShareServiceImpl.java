package net.aipan.dcloud_aipan.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.config.AccountConfig;
import net.aipan.dcloud_aipan.dto.*;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.enums.ShareDayTypeEnum;
import net.aipan.dcloud_aipan.enums.ShareStatusEnum;
import net.aipan.dcloud_aipan.enums.ShareTypeEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import net.aipan.dcloud_aipan.mapper.*;
import net.aipan.dcloud_aipan.model.AccountDO;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import net.aipan.dcloud_aipan.model.ShareDO;
import net.aipan.dcloud_aipan.model.ShareFileDO;
import net.aipan.dcloud_aipan.req.*;
import net.aipan.dcloud_aipan.service.AccountFileService;
import net.aipan.dcloud_aipan.service.ShareService;
import net.aipan.dcloud_aipan.util.JwtUtil;
import net.aipan.dcloud_aipan.util.SpringBeanUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShareServiceImpl implements ShareService {
    @Autowired
    private ShareMapper shareMapper;

    @Autowired
    private ShareFileMapper shareFileMapper;

    @Autowired
    private AccountFileService fileService;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFileMapper accountFileMapper;
    @Autowired
    private FileMapper fileMapper;

    /**
     * 获取我的个人分享列表
     *
     */
    @Override
    public List<ShareDTO> listShare() {
        AccountDTO accountDTO = LoginInterceptor.threadLocal.get();
        List<ShareDO> shareDOList = shareMapper.selectList(new QueryWrapper<ShareDO>()
                .eq("account_id", accountDTO.getId()).orderByDesc("gmt_create"));

        return SpringBeanUtil.copyProperties(shareDOList, ShareDTO.class);
    }

    /**
     * 创建分享
     *检查分享文件的权限
     * 生成分享链接和持久化数据库
     * 生成分享详情
     */
    @Override
    public ShareDTO createShare(ShareCreateReq req) {
        //检查文件权限
        List<Long> fileIds = req.getFileIds();
        fileService.checkFileIds(fileIds, req.getAccountId());
        //生成分享链接和持久化数据库
        Integer dayType = req.getShareDayType();
        Integer shareDays = ShareDayTypeEnum.getDaysByType(dayType);
        long shareId = IdUtil.getSnowflakeNextId();
        //生成分享链接
        String shareUrl = AccountConfig.PAN_FRONT_DOMAIN_SHARE_API + "/" + shareId;
        log.info("shareUrl:{}", shareUrl);
        ShareDO shareDO = ShareDO.builder()
                .id(shareId)
                .shareName(req.getShareName())
                // 核心改动：加上 .toUpperCase()
                .shareType(ShareTypeEnum.valueOf(req.getShareType().toUpperCase()).name())
                .shareDayType(dayType)
                .shareDay(shareDays)
                .shareUrl(shareUrl)
                .shareStatus(ShareStatusEnum.USE.name())
                .accountId(req.getAccountId())
                .build();

        if(ShareDayTypeEnum.PERMANENT.getDayType().equals(dayType)){
            shareDO.setShareEndTime(Date.from(LocalDate.of(9999,12,31)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }else{
            shareDO.setShareEndTime(new Date(System.currentTimeMillis() + shareDays * 24 * 60 * 60 * 1000));
        }

        if (ShareTypeEnum.NEED_CODE.name().equals(req.getShareType())){
            //生成提取码
            String shareCode = RandomStringUtils.randomAlphanumeric(6).toUpperCase();
            shareDO.setShareCode(shareCode);
        }
        shareMapper.insert(shareDO);
        //生成分享详情
        List<ShareFileDO> shareFileDOS=new ArrayList<>();
        for (Long fileId : fileIds) {
            ShareFileDO shareFileDO = ShareFileDO.builder()
                    .shareId(shareId)
                    .accountFileId(fileId)
                    .accountId(req.getAccountId())
                    .build();
            shareFileDOS.add(shareFileDO);
        }
        shareFileMapper.insertBatch(shareFileDOS);
        return SpringBeanUtil.copyProperties(shareDO, ShareDTO.class);
    }

    /**
     * 取消分享
     *
     */

    @Override
    public void cancelShare(ShareCancelReq req) {
        List<ShareDO> shareDOList = shareMapper.selectList(new QueryWrapper<ShareDO>()
                .eq("account_id", req.getAccountId()).in("id", req.getShareIds()));
        if (shareDOList.size()!=req.getShareIds().size()){
            log.info("cancelShare:shareIds error");
            throw new BizException(BizCodeEnum.SHARE_CANCEL_ILLEGAL);
        }
        //删除分享链接
        shareMapper.deleteBatchIds(req.getShareIds());
        //删除分享详情
        shareFileMapper.delete(new QueryWrapper<ShareFileDO>().in("share_id", req.getShareIds()));

    }

    /**
     * 检查分享状态
     * 查询分享记录实体
     * 查询分享者信息
     * 判断是否需要生成校验码,如不需要则直接生成分享token
     * @param shareId
     * @return
     */
    @Override
    public ShareSimpleDTO simpleDetail(Long shareId) {
        //检查分享状态
        ShareDO shareDO=checkShareStatus(shareId);
        //创建分享者实体
        ShareSimpleDTO shareSimpleDTO = SpringBeanUtil.copyProperties(shareDO, ShareSimpleDTO.class);
        //查询分享者信息
        ShareAccountDTO shareAccountDTO = getShareAccount(shareDO.getAccountId());
        shareSimpleDTO.setShareAccountDTO(shareAccountDTO);
        //判断是否需要生成校验码
         if (ShareTypeEnum.NO_CODE.name().equals(shareDO.getShareType())){
             //直接生成分享token
             String shareToken = JwtUtil.geneShareJWT(shareDO.getId());
             shareSimpleDTO.setShareToken(shareToken);
         }
        return  shareSimpleDTO;
    }

    /**
     * 校验分享码
     * @param req
     * @return
     */
    @Override
    public String checkShareCode(ShareCheckReq req) {
        ShareDO shareDO = shareMapper.selectOne(new QueryWrapper<ShareDO>().eq("id", req.getShareId())
                .eq("share_code", req.getShareCode())
                .eq("share_status", ShareStatusEnum.USE.name()));
        if (shareDO!=null){
            //判断是否过期
            if (shareDO.getShareEndTime().getTime()>System.currentTimeMillis()){
                //生成分享token
                return JwtUtil.geneShareJWT(shareDO.getId());
            }else{
                log.error("分享已失效:{}",req.getShareId());
                throw new BizException(BizCodeEnum.SHARE_EXPIRED);
            }
        }
        throw new BizException(BizCodeEnum.SHARE_NOT_EXIST);
    }

    /**
     * 查询分享记录实体
     * 检查分享状态
     * 查询分享文件信息
     * 创建分享者信息
     * 构造分析详情对象并返回
     * @param shareId
     * @return
     */
    @Override
    public ShareDetailDTO detail(Long shareId) {
        //查询分享记录实体
        ShareDO shareDO = checkShareStatus(shareId);
        ShareDetailDTO shareDetailDTO = SpringBeanUtil.copyProperties(shareDO, ShareDetailDTO.class);
        //查询分享文件信息
        List<AccountFileDO> accountFileDOList = getShareFileInfo(shareId);
        List<AccountFileDTO> accountFileDTOList = SpringBeanUtil.copyProperties(accountFileDOList, AccountFileDTO.class);
        shareDetailDTO.setFileDTOList(accountFileDTOList);
        //查询分享者信息
        ShareAccountDTO shareAccountDTO = getShareAccount(shareDO.getAccountId());
        shareDetailDTO.setShareAccountDTO(shareAccountDTO);
        return shareDetailDTO;

    }

    /**
     * 查看某个分享文件夹下的文件列表
     * 检查分享连接状态
     * 查询分享id是否在分享的文件列表中
     * 分组后获取某个文件夹下的所有文件夹
     * 根据父文件夹Id获取子文件列表
     * @param req
     * @return
     */
    @Override
    public List<AccountFileDTO> fileShareList(ShareFileQueryReq req) {
        //检查分享连接状态
        ShareDO shareDO = checkShareStatus(req.getShareId());

        //查询分享id是否在分享的文件列表中
        List<AccountFileDO> accountFileDOList=checkShareFileIdOnStatus(shareDO.getId(), List.of(req.getParentId()));
        List<AccountFileDTO> accountFileDTOList = SpringBeanUtil.copyProperties(accountFileDOList, AccountFileDTO.class);

        //分组后获取某个文件夹下的所有文件夹
        Map<Long, List<AccountFileDTO>> fileListMap = accountFileDTOList.stream().collect(Collectors.groupingBy(AccountFileDTO::getParentId));

        //根据父文件夹Id获取子文件列表
        List<AccountFileDTO> childFileList = fileListMap.get(req.getParentId());
        if (CollectionUtils.isEmpty(childFileList)){
            return List.of();
        }
        return childFileList;
    }

    /**
     * 转移分享文件
     * 分享链接是否状态准确
     * 转存的文件是否是分享链接里的文件
     * 目标文件夹是否为当前用户
     * 获取转存的文件
     * 保存需要转存的文件列表（递归子文件）
     * 同步更新所有文件的accountId为当前用户的id
     * 计算存储空间大小，检查是否足够
     * 更新关联对象信息，存储文件映射关系
     * @param req
     */
    @Override
    @Transactional
    public void transferShareFile(ShareFileTransferReq req) {
        //检查分享链接状态
        checkShareStatus(req.getShareId());
        //转存文件是否是分享链接里的文件
        checkInShareFiles(req.getFileIds(), req.getShareId());
        //目标文件夹是否为当前用户
        AccountFileDO currentAccountDO = accountFileMapper.selectOne(new QueryWrapper<AccountFileDO>()
                .eq("account_id", req.getAccountId())
                .eq("id", req.getParentId()));
        if (currentAccountDO==null){
            log.error("目标文件夹不是当前用户的:{}",req);
            throw new BizException(BizCodeEnum.FILE_NOT_EXISTS);
        }
        //获取需要转存的文件
        List<AccountFileDO> shareFileList = accountFileMapper.selectBatchIds(req.getFileIds());
        //保存需要转存文件列表（递归子文件）
        List<AccountFileDO> batchTransferFileList = fileService.findBatchCopyFileWithRecur(shareFileList, req.getParentId());
        //同步更新所有文件的accountId为当前用户的id
        batchTransferFileList.forEach(file->{
                file.setAccountId(req.getAccountId());
        });
        //计算存储空间大小，检查是否足够
        if(!fileService.checkAndUpdateCapacity(req.getAccountId(),batchTransferFileList.stream().mapToLong(AccountFileDO::getFileSize).sum())){
            throw new BizException(BizCodeEnum.FILE_STORAGE_NOT_ENOUGH);
        }
        //更新关联对象信息，存储文件映射关系
        accountFileMapper.insertFileBatch(batchTransferFileList);
    }

    /**
     * 检查转存文件是否在分享链接里
     * @param fileIds
     * @param shareId
     */
    private void checkInShareFiles(List<Long> fileIds, Long shareId) {
        //获取分享链接的文件
        List<ShareFileDO> shareFileDOS = shareFileMapper.selectList(new QueryWrapper<ShareFileDO>().eq("share_id", shareId));
        List<Long> shareFileIds = shareFileDOS.stream().map(ShareFileDO::getAccountFileId).toList();
        //找文件实体
        List<AccountFileDO> shareAccountFileDOList = accountFileMapper.selectBatchIds(shareFileIds);
        //递归查找分享链接里面的所有子文件
        List<AccountFileDO> allShareFiles=new ArrayList<>();
        fileService.findAllAccountFileDOWithRecur(shareAccountFileDOList, allShareFiles,false);
        //提取全部文件的Id
        List<Long> allShareFileIds = allShareFiles.stream().map(AccountFileDO::getId).toList();

        //判断需要转存的文件是否在分享链接里
        for(Long fileId:fileIds){
            if(!allShareFileIds.contains(fileId)){
                log.error("文件Id:{}不在分享的文件列表中",fileId);
                throw new BizException(BizCodeEnum.SHARE_FILE_ILLEGAL);
            }
        }
    }

    /**
     * 查询分享id是否在分享的文件列表中
     * 返回分享的全部子文件
     */
    private List<AccountFileDO> checkShareFileIdOnStatus(Long shareId, List<Long> fileIdlist) {
        //需要获取分享列表的全部文件夹和子文件内容
        List<AccountFileDO> shareFileInfoList=getShareFileInfo(shareId);
        List<AccountFileDO> allAccountFileDOList=new ArrayList<>();
        //递归获取全部文件
        fileService.findAllAccountFileDOWithRecur(shareFileInfoList, allAccountFileDOList,false);

        if(CollectionUtils.isEmpty(allAccountFileDOList)){
            return List.of();
        }
        //判断目标文件夹集合是否在分享对象的全部文件夹内
        Set<Long> allFileIdList = allAccountFileDOList.stream().map(AccountFileDO::getId).collect(Collectors.toSet());
        if(!allFileIdList.containsAll(fileIdlist)){
            log.error("目标文件Id列表不在分享的文件列表中:{}",fileIdlist);
            throw new BizException(BizCodeEnum.SHARE_FILE_ILLEGAL);
        }

        return allAccountFileDOList;
    }

    private List<AccountFileDO> getShareFileInfo(Long shareId) {
        //查找分析文件列表
        List<ShareFileDO> shareFileList = shareFileMapper.selectList(new QueryWrapper<ShareFileDO>().select("account_file_id")
                .eq("share_id", shareId));
        List<Long> shareFileIdList=shareFileList.stream().map(ShareFileDO::getAccountFileId).toList();
        //查找对象列表
       return accountFileMapper.selectBatchIds(shareFileIdList);
    }

    /**
     * 获取分享者信息
     * @param accountId
     * @return
     */
    private ShareAccountDTO getShareAccount(Long accountId) {
        if(accountId!=null){
            AccountDO accountDO = accountMapper.selectById(accountId);
            if (accountDO!=null){
                return SpringBeanUtil.copyProperties(accountDO, ShareAccountDTO.class);
            }
        }
        return null;
    }


    /**
     * 检查分享状态
     * @param shareId
     * @return
     */
    private ShareDO checkShareStatus(Long shareId) {
        ShareDO shareDO = shareMapper.selectById(shareId);
        if (shareDO==null){
            log.error("分享链接不存在:{}",shareId);
            throw new BizException(BizCodeEnum.SHARE_NOT_EXIST);
        }

        if (ShareStatusEnum.CANCELED.name().equalsIgnoreCase(shareDO.getShareStatus())){
            log.error("分享已取消:{}",shareId);
            throw new BizException(BizCodeEnum.SHARE_CANCELED);
        }

        //判断分享是否过期
        if(shareDO.getShareEndTime().before(new Date())){
            log.error("分享已失效:{}",shareId);
            throw new BizException(BizCodeEnum.SHARE_EXPIRED);
        }
        return shareDO;
    }
}
