package net.aipan.dcloud_aipan.controller;

import net.aipan.dcloud_aipan.dto.AccountFileDTO;
import net.aipan.dcloud_aipan.interceptor.LoginInterceptor;
import net.aipan.dcloud_aipan.req.RecycleDelReq;
import net.aipan.dcloud_aipan.req.RecycleRestoreReq;
import net.aipan.dcloud_aipan.service.RecycleService;
import net.aipan.dcloud_aipan.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recycle/v1")
public class RecycleController {

    @Autowired
    private RecycleService recycleService;

    /**
     * 获取回收站列表
     */
    @GetMapping("/list")
    public JsonData list(){
        Long accountId = LoginInterceptor.threadLocal.get().getId();
        List<AccountFileDTO>  list=recycleService.listRecycleFiles(accountId);
        return JsonData.buildSuccess(list);
    }

    /**
     * 彻底删除回收站文件
     */
    @PostMapping("/delete")
    public JsonData delete(@RequestBody RecycleDelReq  req){
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        recycleService.delete(req);
        return JsonData.buildSuccess();
    }

    /**
     * 还原回收站文件
     */
    @PostMapping("/restore")
    public JsonData restore(@RequestBody RecycleRestoreReq req){
        req.setAccountId(LoginInterceptor.threadLocal.get().getId());
        recycleService.restore(req);
        return JsonData.buildSuccess();
    }
}
