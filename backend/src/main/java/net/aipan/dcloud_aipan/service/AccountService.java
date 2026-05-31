package net.aipan.dcloud_aipan.service;

import net.aipan.dcloud_aipan.dto.AccountDTO;
import net.aipan.dcloud_aipan.req.AccountLoginReq;
import net.aipan.dcloud_aipan.req.AccountRegisterReq;
import org.springframework.web.multipart.MultipartFile;

public interface AccountService {
    void register(AccountRegisterReq req);

    String uploadAvatar(MultipartFile file);

    AccountDTO login(AccountLoginReq req);

    AccountDTO queryDetail(Long id);
}
