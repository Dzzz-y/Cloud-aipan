package net.aipan.dcloud_aipan.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 用户分享表
 * </p>
 *
 */
@Getter
@Setter
@TableName("share")
@Schema(name = "ShareSimpleDTO", description = "分享链接对象")
public class ShareSimpleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分享id")
    private Long id;

    @Schema(description = "分享名称")
    private String shareName;

    @Schema(description = "分享类型（no_code没有提取码 ,need_code有提取码）")
    private String shareType;

    @Schema(description = "分享类型（0 永久有效；1: 7天有效；2: 30天有效）")
    private Integer shareDayType;

    @Schema(description = "分享有效天数（永久有效为0）")
    private Integer shareDay;

    @Schema(description = "分享结束时间")
    private Date shareEndTime;

    @Schema(description = "分享链接地址")
    private String shareUrl;

    /**
     * 分享者信息
     */
    private ShareAccountDTO shareAccountDTO;

    /**
     * 不需要提取码时,传入shareToken
     */
    private String shareToken;
}
