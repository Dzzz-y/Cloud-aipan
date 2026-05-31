package net.aipan.dcloud_aipan.util;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.PrintWriter;

@Slf4j
public class CommonUtil {




    public static void sendJsonMessage(HttpServletResponse response,Object obj) {
        response.setContentType("application/json; charset=utf-8");
        try(PrintWriter writer = response.getWriter()){
            writer.print(JsonUtil.obj2Json(obj));
            response.flushBuffer();
        }catch (IOException e){
            log.warn("响应数据给前端异常:{}",e);
        }
    }
    /**
     * 根据文件名称获取文件后缀
     */
    public static String getFileSuffix(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 生成文件在存储罐中的唯一路径。先获取文件后缀，然后按照"年/月/日/随机 UUID.后缀"的格式组合格式化返回，确保文件存储路径的唯一性。
     */
    public static String getFilePath(String fileName){
        String suffix=getFileSuffix(fileName);
        //生成文件在存储罐中的唯一键
        return StrUtil.format("{}/{}/{}/{}.{}", DateUtil.thisYear(), DateUtil.thisMonth() + 1,
                DateUtil.thisDayOfMonth(), IdUtil.randomUUID(), suffix);
    }
}
