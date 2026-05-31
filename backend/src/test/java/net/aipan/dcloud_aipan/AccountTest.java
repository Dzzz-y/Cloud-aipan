package net.aipan.dcloud_aipan;

import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.dto.AccountDTO;
import net.aipan.dcloud_aipan.req.AccountLoginReq;
import net.aipan.dcloud_aipan.req.AccountRegisterReq;
import net.aipan.dcloud_aipan.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class AccountTest {

    @Autowired
    private AccountService accountService;

    /**
     * 注册方法测试
     */
    @Test
    public void registerTest() {
        AccountRegisterReq req = AccountRegisterReq.builder().phone("12345678901").name("张三").password("12345678").avatarUrl("adai.net")
                        .build();
        accountService.register(req);
    }
    /**
     * 登录方法测试
     */
    @Test
    public void loginTest() {
        AccountLoginReq loginReq = AccountLoginReq.builder().phone("12345678901").password("12345678").build();
        accountService.login(loginReq);
    }

    /**
     * 获取用户信息方法测试
     */
    @Test
    public void queryDetailTest() {
        AccountDTO accountDTO = accountService.queryDetail(2037756651339636738L);
        log.info("accountDTO:{}", accountDTO);
    }
}
