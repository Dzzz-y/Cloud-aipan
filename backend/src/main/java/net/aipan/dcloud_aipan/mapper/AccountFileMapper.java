package net.aipan.dcloud_aipan.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import net.aipan.dcloud_aipan.model.AccountFileDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户文件表 Mapper 接口
 * </p>
 * @since 2026-03-25
 */
public interface AccountFileMapper extends BaseMapper<AccountFileDO> {

    void insertFileBatch(@Param("newAccountFileDOList") List<AccountFileDO> newAccountFileDOList);

    /**
     * 查询被删除的
     * @param accountId
     * @return
     */
    List<AccountFileDO> selectRecycleFiles(@Param("accountId") Long accountId, @Param("fileIdList") List<Long> fileIdList);


    /**
     * 查询回收站删除子文件
     * @param parentId
     * @param accountId
     * @return
     */
    List<AccountFileDO> selectRecycleChildFiles(@Param("parentId")  Long parentId,@Param("accountId") Long accountId);

    /**
     * 彻底删除文件
     * @param recycleFileIds
     */
    void deleteRecycleFiles(List<Long> recycleFileIds);

    boolean updateRecycleFileById(@Param("id")Long id, @Param("fileName")String fileName);

    /**
     * 批量还原文件
     * @param allFileIds
     */
    void restoreFiles(@Param("allFileIds")List<Long> allFileIds);
}
