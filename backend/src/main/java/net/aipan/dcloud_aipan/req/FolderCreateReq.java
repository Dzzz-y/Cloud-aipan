package net.aipan.dcloud_aipan.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "文件夹创建请求参数")
public class FolderCreateReq {

    @Schema(description = "文件夹名称", example = "工作文档", requiredMode = Schema.RequiredMode.REQUIRED)
    private String folderName;

    @Schema(description = "上级文件夹 ID", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentId;

    @Schema(description = "用户 ID", hidden = true)
    private Long accountId;
}
