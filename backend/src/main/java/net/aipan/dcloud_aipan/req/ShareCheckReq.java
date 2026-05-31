package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareCheckReq {

    private Long shareId;

    private String shareCode;
}
