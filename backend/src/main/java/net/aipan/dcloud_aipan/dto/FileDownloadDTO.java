package net.aipan.dcloud_aipan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDownloadDTO {
    private String fileName;

    private String downloadUrl;
}
