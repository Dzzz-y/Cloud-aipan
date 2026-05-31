package net.aipan.dcloud_aipan.mapper;

import net.aipan.dcloud_aipan.model.ShareFileDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 * 文件分享表 Mapper 接口
 * </p>
 * @since 2026-03-25
 */
public interface ShareFileMapper extends BaseMapper<ShareFileDO> {

    void insertBatch(List<ShareFileDO> shareFileDOS);

}
