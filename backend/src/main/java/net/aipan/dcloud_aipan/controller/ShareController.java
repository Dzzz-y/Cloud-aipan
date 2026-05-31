package net.aipan.dcloud_aipan.controller;

import net.aipan.dcloud_aipan.annotation.ShareCodeCheck;
import net.aipan.dcloud_aipan.aspect.ShareCodeAspect;
import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.dto.ShareDTO;
import net.aipan.dcloud_aipan.dto.ShareDetailDTO;
import net.aipan.dcloud_aipan.dto.ShareSimpleDTO;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import net.aipan.dcloud_aipan.req.*;
import net.aipan.dcloud_aipan.service.ShareService;
import net.aipan.dcloud_aipan.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/share/v1")
public class ShareController {

    @Autowired
    private ShareService shareService;

     /**34
     * 获取我的个人分享列表接口
     */
    @GetMapping("/list")
    public JsonData list() {
        List<ShareDTO> list=shareService.listShare();
        return JsonData.buildSuccess(list);
    }

    /**
     * 创建分享连接
     */
    @PostMapping("/create")
    public JsonData create(@RequestBody ShareCreateReq req) {
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        ShareDTO shareDTO= shareService.createShare(req);
        return JsonData.buildSuccess(shareDTO);
    }
    /**
     * 取消分享
     */
    @PostMapping("/cancel")
    public JsonData cancel(@RequestBody ShareCancelReq req) {
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        shareService.cancelShare(req);
        return JsonData.buildSuccess();
    }

    /**
     * 访问分享
     */
    @GetMapping("/visit")
    public JsonData visit(@RequestParam(value = "shareId") Long shareId) {
        ShareSimpleDTO shareDTO=shareService.simpleDetail(shareId);
        return JsonData.buildSuccess(shareDTO);
    }
    /**
     * 校验分享码,返回临时token
     */
    @PostMapping("/check_share_code")
    public JsonData checkShareCode(@RequestBody ShareCheckReq req) {
        String shareToken = shareService.checkShareCode(req);
        return JsonData.buildSuccess(shareToken);
    }

    /**
     *查看分享详情
     */
    @ShareCodeCheck
    @GetMapping("/detail")
    public JsonData detail() {
        ShareDetailDTO shareDTO=shareService.detail(ShareCodeAspect.get());
        return JsonData.buildSuccess(shareDTO);
    }
    /**
     *查看某个分享文件夹下的文件列表
     */
    @PostMapping("/file_share_list")
    @ShareCodeCheck
    public JsonData fileShareList(@RequestBody ShareFileQueryReq req) {
        req.setShareId(ShareCodeAspect.get());
        List<AccountFileDTO> list=shareService.fileShareList(req);
        return JsonData.buildSuccess(list);
    }

    /**
     * 文件转存
     */
    @PostMapping("/transfer")
    @ShareCodeCheck
    public JsonData transfer(@RequestBody ShareFileTransferReq req) {
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        req.setShareId(ShareCodeAspect.get());
        shareService.transferShareFile( req);
        return JsonData.buildSuccess();
    }
}
