package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class FileBatchReq {
    /**
     * 文件id列表
     */
    private List<Long> fileIds;

    /**
     * 目标父级id
     */
    private Long targetParentId;

    /**
     * 用户id
     */
    private Long accountId;
}
