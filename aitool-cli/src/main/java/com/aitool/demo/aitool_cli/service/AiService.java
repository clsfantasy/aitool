package com.aitool.demo.aitool_cli.service;

import com.aitool.demo.aitool_cli.dto.ChatRequest;
import com.aitool.demo.aitool_cli.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model.name}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate(); // 如果你配了代理，记得用配过代理的那个

    // 🧠 核心：这就是记忆！
    // 因为 @Service 默认是单例的 (Singleton)，所以这个 List 会一直存在内存里，直到程序关闭
    private final List<ChatRequest.Message> history = new ArrayList<>();

    // 构造函数：初始化时可以给个“人设”
    public AiService() {
        history.add(ChatRequest.Message.builder()
                .role("system") // system 角色是给 AI 设定行为准则的
                .content("你是一个乐于助人的 AI 助手，回答请简练。")
                .build());
    }

    public String callAi(String userMessage) {
        // 1. 把用户的每句话，都存入历史记录
        history.add(ChatRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        // 2. 准备请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 3. 准备请求体 (注意：这里传的是 history，而不是单条消息！)
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .stream(false)
                .messages(history) // <--- 关键点：发送完整的历史
                .build();

        try {
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(apiUrl, entity, ChatResponse.class);

            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String aiReply = response.getBody().getChoices().get(0).getMessage().getContent();

                // 4. 收到 AI 回复后，也要存入历史！
                history.add(ChatRequest.Message.builder()
                        .role("assistant")
                        .content(aiReply)
                        .build());

                return aiReply;
            }
            return "AI 沉默了";
        } catch (Exception e) {
            e.printStackTrace();
            return "调用失败: " + e.getMessage();
        }
    }

    // 增加一个清空记忆的功能，防止聊爆了
    public void clearMemory() {
        history.clear();
        history.add(ChatRequest.Message.builder()
                .role("system")
                .content("你是一个乐于助人的 AI 助手。")
                .build());
    }
}