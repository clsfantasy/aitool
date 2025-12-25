package com.aitool.demo.aitool_cli.service;

import com.aitool.demo.aitool_cli.dto.ChatRequest;
import com.aitool.demo.aitool_cli.dto.ChatResponse;
import com.aitool.demo.aitool_cli.service.tools.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiService {

    private static final int MAX_TOOL_CALLS = 10;

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model.name}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final List<ChatRequest.Message> history = new ArrayList<>();
    private final Map<String, AgentTool> toolMap;
    private final String toolsPrompt;

    public AiService(List<AgentTool> tools) {
        // 配置超时时间：连接 10s，读取 30s
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);

        this.toolMap = tools.stream().collect(Collectors.toMap(AgentTool::getName, t -> t));
        this.toolsPrompt = tools.stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));
        initMemory();
    }

    private void initMemory() {
        history.clear();
        String systemInstruction = """
            你是一个资深的 Java 智能助手 (Agent)。
            
            你可以使用以下工具：
            %s
            
            规则：
            1. 每次只输出一个工具指令。
            2. 格式必须严格为：[TOOL: 工具名 参数]
            3. 如果 read_file 失败，请尝试使用 list_files。
            """.formatted(toolsPrompt);

        history.add(ChatRequest.Message.builder().role("system").content(systemInstruction).build());
    }

    public String callAi(String userMessage) {
        history.add(ChatRequest.Message.builder().role("user").content(userMessage).build());

        int currentCall = 0;

        try {
            String aiResponse = sendRequestToLlm();

            while (currentCall < MAX_TOOL_CALLS) {
                // 🛑 核心修复：防止 NPE
                if (aiResponse == null) {
                    throw new RuntimeException("API 调用返回了空结果 (可能是网络问题或被拦截)");
                }

                int toolStartIndex = aiResponse.indexOf("[TOOL:");
                if (toolStartIndex != -1 && aiResponse.contains("]")) {
                    currentCall++;
                    log.info("🔄 Agent Loop: 第 {}/{} 次工具调用...", currentCall, MAX_TOOL_CALLS);

                    // --- 解析指令 ---
                    int toolEndIndex = aiResponse.indexOf("]", toolStartIndex);
                    String commandString = aiResponse.substring(toolStartIndex, toolEndIndex + 1);
                    String commandContent = commandString.substring(7, commandString.length() - 1).trim();

                    String toolName = commandContent.split(" ", 2)[0];
                    String args = commandContent.contains(" ") ? commandContent.split(" ", 2)[1].trim() : "";

                    // --- 执行工具 ---
                    String toolResult;
                    if (toolMap.containsKey(toolName)) {
                        log.info("🚀 执行工具: [{}]", toolName);
                        toolResult = toolMap.get(toolName).execute(args);
                        log.info("✅ 工具执行完毕");
                    } else {
                        toolResult = "系统错误：找不到名为 " + toolName + " 的工具";
                    }

                    // --- 记录结果 ---
                    history.add(ChatRequest.Message.builder()
                            .role("system")
                            .content("工具执行结果:\n" + toolResult)
                            .build());

                    // --- 再次请求 AI ---
                    aiResponse = sendRequestToLlm();

                } else {
                    return aiResponse;
                }
            }

            return "❌ 任务执行失败：Agent 陷入了思维死循环。";

        } catch (Exception e) {
            log.error("Agent 运行异常", e);
            if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).getRole())) {
                history.remove(history.size() - 1);
            }
            return "系统异常: " + e.getMessage();
        }
    }

    // 🛑 核心修复：确保不吞异常，不返回 null
    private String sendRequestToLlm() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .stream(false)
                .messages(history)
                .build();

        try {
            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(apiUrl, new HttpEntity<>(request, headers), ChatResponse.class);

            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String reply = response.getBody().getChoices().get(0).getMessage().getContent();
                history.add(ChatRequest.Message.builder().role("assistant").content(reply).build());
                return reply;
            }
            // 如果 Body 是 null，抛出异常，不要返回 null！
            throw new RuntimeException("AI API 返回了 200 OK 但内容为空");

        } catch (Exception e) {
            // 这里我们抛出运行时异常，让 callAi 的 catch 块去处理
            // 这样就能在日志里看到具体的错误（比如 400 Bad Request 或 502 Bad Gateway）
            throw new RuntimeException("请求 LLM 失败: " + e.getMessage(), e);
        }
    }

    public void clearMemory() {
        initMemory();
    }
}