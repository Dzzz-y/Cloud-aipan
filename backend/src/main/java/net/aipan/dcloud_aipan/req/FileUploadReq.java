package net.aipan.dcloud_aipan.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class FileUploadReq {
    /**
     * 文件名
     */
    private String fileName;

    /**
     * 唯一标识
     */
    private String identifier;

    /**
     * 用户ID
     */
    private Long accountId;


    /**
     * 父级ID
     */
    private Long parentId;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     *  文件
     */
    private MultipartFile file;
}
