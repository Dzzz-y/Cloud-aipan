package net.aipan.dcloud_aipan.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.Arrays;

@Service
@Slf4j

public class ChatServiceImpl implements ChatService {

    @Value("${ai.key}")
    private String aiKey;

    @Override
    public GenerationResult callWithMessage(String input) throws Exception {
        //构建大模型生成器
        Generation gen=new Generation();
        //构建系统角色信息
        Message systemMsg= Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("你是一个智能助手")
                .build();

        Message userMsg= Message.builder()
                .role(Role.USER.getValue())
                .content(input)
                .build();

        //构建请求参数，配置模型的其他关键词和对话上下文
        GenerationParam param = GenerationParam.builder()
                .model("deepseek-chat")
                .messages(Arrays.asList(systemMsg, userMsg))  //上下文
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)  //设置返回格式
                .temperature(0.7f)
                .apiKey(aiKey)
                .build();
        //执行模型调用
        return gen.call(param);
    }
}
