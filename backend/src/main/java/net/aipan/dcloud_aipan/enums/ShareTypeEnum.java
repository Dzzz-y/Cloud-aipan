package net.aipan.dcloud_aipan.enums;

import lombok.Getter;

@Getter
public enum ShareTypeEnum {
    /**
     * 不需要提取码
     */
    NO_CODE,
    /**
     * 需要提取码
     */
    NEED_CODE;
}
