package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class FileUpdateReq {

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 新的文件名
     */
    private String newFileName;
}
