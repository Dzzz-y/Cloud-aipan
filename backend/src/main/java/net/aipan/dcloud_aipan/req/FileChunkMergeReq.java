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
public class FileChunkMergeReq {

    private String identifier;
    private Long parentId;
    private Long accountId;

}
