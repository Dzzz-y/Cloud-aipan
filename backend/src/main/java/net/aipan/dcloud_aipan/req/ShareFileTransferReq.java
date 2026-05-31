package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareFileTransferReq {
    private Long shareId;
    private Long accountId;
    private Long parentId;
    private List< Long> fileIds;
}
