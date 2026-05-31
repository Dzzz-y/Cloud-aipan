package net.aipan.dcloud_aipan.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum FolderFlagEnum {
    /**
     * 非文件夹
     */
    NO(0),

    /**
     * 文件夹
     */
    YES(1);

    private Integer code;
}
