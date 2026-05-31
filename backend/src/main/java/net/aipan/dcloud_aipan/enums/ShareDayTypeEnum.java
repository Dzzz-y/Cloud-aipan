package net.aipan.dcloud_aipan.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ShareDayTypeEnum {
    PERMANENT(0, 0),
    SEVEN_DAYS(1, 7),
    THIRTY_DAYS(2, 30);

    private Integer dayType;
    private Integer days;

    /**
     * 根据类型获取对应天数
     */
    public static Integer getDaysByType(Integer dayType) {
        for (ShareDayTypeEnum value : ShareDayTypeEnum.values()) {
            if (value.getDayType().equals(dayType)) {
                return value.getDays();
            }
        }
        // 默认7天
        return SEVEN_DAYS.getDays();
    }
}
