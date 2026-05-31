package net.aipan.dcloud_aipan.service;

import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.req.RecycleDelReq;
import net.aipan.dcloud_aipan.req.RecycleRestoreReq;

import java.util.List;

public interface RecycleService {

    List<AccountFileDTO> listRecycleFiles(Long accountId);

    void delete(RecycleDelReq req);

    void restore(RecycleRestoreReq req);
}
