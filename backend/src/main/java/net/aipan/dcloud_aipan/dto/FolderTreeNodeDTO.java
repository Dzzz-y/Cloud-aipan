package net.aipan.dcloud_aipan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FolderTreeNodeDTO {
    /**
     * 文件id
     */
    private Long id;

    /**
     * 父文件id
     */
    private Long parentId;

    /**
     * 文件名称
     */
    private String label;

    /**
     * 子文件列表
     */
    List<FolderTreeNodeDTO> children = new ArrayList<>();
}
