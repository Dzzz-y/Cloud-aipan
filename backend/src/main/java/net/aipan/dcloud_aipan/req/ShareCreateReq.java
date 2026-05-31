package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ShareCreateReq {
    /**
     * 分享名称
     */
    private String shareName;

    /**
     * 分享类型
     */
    private String shareType;

    /**
     * 分享有效期 0:永久有效  1:7天有效  2:30天有效
     */
    private Integer shareDayType;

    /**
     * 分享文件id列表
     */
    private List<Long> fileIds;

    /**
     * 分享者ID
     */
    private Long accountId;
}
