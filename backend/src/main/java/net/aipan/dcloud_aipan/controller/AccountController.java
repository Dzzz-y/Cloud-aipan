package net.aipan.dcloud_aipan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.aipan.dcloud_aipan.dto.AccountDTO;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import net.aipan.dcloud_aipan.req.AccountLoginReq;
import net.aipan.dcloud_aipan.req.AccountRegisterReq;
import net.aipan.dcloud_aipan.service.AccountService;
import net.aipan.dcloud_aipan.util.JsonData;
import net.aipan.dcloud_aipan.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "账号管理", description = "用户注册、登录、信息查询等接口")
@RestController
@RequestMapping("/api/account/v1")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Operation(summary = "用户注册", description = "新用户注册账号")
    @PostMapping("/register")
    public JsonData register(@RequestBody AccountRegisterReq req) {
        accountService.register(req);
        return JsonData.buildSuccess();
    }

    @Operation(summary = "上传头像", description = "用户上传个人头像")
    @PostMapping("/avatar/upload")
    public JsonData avatarUpload(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile file) {
        String url=accountService.uploadAvatar(file);
        return JsonData.buildSuccess(url);
    }

    @Operation(summary = "用户登录", description = "手机号和密码登录，返回 JWT Token")
    @PostMapping("/login")
    public JsonData login(@RequestBody AccountLoginReq req) {
        AccountDTO accountDTO=accountService.login(req);
        String token= JwtUtil.geneLoginJWT(accountDTO);
        return JsonData.buildSuccess(token);
    }

    @Operation(summary = "获取用户详情", description = "获取当前登录用户的详细信息")
    @GetMapping("/detail")
    public JsonData detail() {
        AccountDTO accountDTO=accountService.queryDetail(LoginInterceptor.threadLocal.get().getId());
        return JsonData.buildSuccess(accountDTO);
    }
}
