package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class FileChunkInitTaskReq {

    private Long accountId;

    private String fileName;

    private String identifier;

    /**
     * 文件总大小
     */
    private Long totalSize;

    /**
     * 分片大小
     */
    private Long chunkSize;
}
