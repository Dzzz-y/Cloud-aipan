package net.aipan.dcloud_aipan.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ShareAccountDTO {
    private Long id;

    private String userName;

    private String avatarUrl;
}
