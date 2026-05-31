package net.aipan.dcloud_aipan.service;

import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.dto.ShareDTO;
import net.aipan.dcloud_aipan.dto.ShareDetailDTO;
import net.aipan.dcloud_aipan.dto.ShareSimpleDTO;
import net.aipan.dcloud_aipan.req.*;

import java.util.List;

public interface ShareService {
    /**
     * 获取我的分享列表接口
     * @return
     */
    List<ShareDTO> listShare();

    ShareDTO createShare(ShareCreateReq req);

    void cancelShare(ShareCancelReq req);

    ShareSimpleDTO simpleDetail(Long shareId);

    String checkShareCode(ShareCheckReq req);

    ShareDetailDTO detail(Long shareId);

    List<AccountFileDTO> fileShareList(ShareFileQueryReq req);

    void transferShareFile(ShareFileTransferReq req);
}
