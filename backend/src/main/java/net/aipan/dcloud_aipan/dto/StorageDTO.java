package net.aipan.dcloud_aipan.dto;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 存储信息表
 * </p>
 */
@Getter
@Setter
@TableName("storage")
@Schema(name = "StorageDO", description = "存储信息表")
public class StorageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "所属用户")
    private Long accountId;

    @Schema(description = "占用存储大小")
    private Long usedSize;

    @Schema(description = "总容量大小，字节存储")
    private Long totalSize;

    @Schema(description = "创建时间")
    private Date gmtCreate;

    @Schema(description = "更新时间")
    private Date gmtModified;
}
