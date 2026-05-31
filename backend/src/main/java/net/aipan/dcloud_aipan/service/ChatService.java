package net.aipan.dcloud_aipan.service;

import com.alibaba.dashscope.aigc.generation.GenerationResult;

public interface ChatService {

    GenerationResult callWithMessage(String input) throws Exception;
}
