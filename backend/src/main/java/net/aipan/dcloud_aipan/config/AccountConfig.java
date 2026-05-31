package net.aipan.dcloud_aipan.config;

public class AccountConfig {
    /**
     * 账号密码加密
     */
    public static final String PASSWORD_SALT = "dcloud_aipan";

    /**
     * 默认用户存储大小
     */
    public static final Long DEFAULT_STORAGE_SIZE = 1024 * 1024 * 100L;

    /**
     * 根文件夹名称
     */
    public static final String ROOT_FOLDER_NAME="全部文件夹";

    /**
     * 父级Id
     */
    public static final Long ROOT_PARENT_ID=0L;

    /**
     * 网盘前端地址
     */
    public static final String PAN_FRONT_DOMAIN_SHARE_API="192.168.10.40/share";     //修改为自己的网盘前端地址
}
