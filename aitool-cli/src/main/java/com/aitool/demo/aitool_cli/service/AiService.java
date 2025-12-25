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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j // 启用日志
@Service
public class AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model.name}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final List<ChatRequest.Message> history = new ArrayList<>();

    // 工具注册表 (Map<工具名, 工具对象>)
    private final Map<String, AgentTool> toolMap;
    // 工具描述文本 (给 AI 看的说明书)
    private final String toolsPrompt;

    // 🏆 构造函数注入：Spring 会自动把所有实现了 AgentTool 的类（比如 FileReadTool）塞进这个 List 里
    public AiService(List<AgentTool> tools) {
        // 1. 转成 Map 方便查找
        this.toolMap = tools.stream().collect(Collectors.toMap(AgentTool::getName, t -> t));

        // 2. 生成工具说明书
        this.toolsPrompt = tools.stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));

        // 3. 初始化 System Prompt (赋予 AI 人设)
        initMemory();
    }

    private void initMemory() {
        history.clear();
        // 构建强大的 System Prompt
        String systemInstruction = """
            你是一个资深的 Java 智能助手 (Agent)。
            
            你可以使用以下工具来辅助用户：
            %s
            
            如果你需要使用工具，请**不要**直接回答，而是只输出以下格式的指令：
            [TOOL: 工具名 参数]
            
            例如：如果要读 Main.java，请输出：
            [TOOL: read_file src/Main.java]
            """.formatted(toolsPrompt);

        history.add(ChatRequest.Message.builder()
                .role("system")
                .content(systemInstruction)
                .build());
    }

    public String callAi(String userMessage) {
        // 1. 先把消息包装好
        var userMsgObj = ChatRequest.Message.builder().role("user").content(userMessage).build();

        // 2. 加入历史
        history.add(userMsgObj);

        try {
            // 3. 发送请求
            String aiResponse = sendRequestToLlm();

            // 3. 🕵️‍♂️ 检测 AI 是否想调用工具
            if (aiResponse.startsWith("[TOOL:") && aiResponse.contains("]")) {
                log.info("🔍 1. 命中工具调用规则，原始指令: {}", aiResponse);

                try {
                    // --- 🛡️ 更稳健的解析逻辑 Start ---
                    // 找到第一个 ] 的位置，防止后面有空格或换行干扰
                    int endIndex = aiResponse.indexOf("]");
                    // 提取中间内容： "read_file pom.xml"
                    String commandContent = aiResponse.substring(7, endIndex).trim();

                    String toolName;
                    String args;

                    // 拆分工具名和参数
                    if (commandContent.contains(" ")) {
                        String[] parts = commandContent.split(" ", 2);
                        toolName = parts[0];
                        args = parts[1].trim();
                    } else {
                        toolName = commandContent;
                        args = "";
                    }
                    // --- 🛡️ 解析逻辑 End ---

                    log.info("🛠️ 2. 解析成功 -> 工具名: [{}], 参数: [{}]", toolName, args);

                    // 执行工具
                    String toolResult;
                    if (toolMap.containsKey(toolName)) {
                        log.info("🚀 3. 正在执行工具...");
                        toolResult = toolMap.get(toolName).execute(args);
                        log.info("✅ 4. 工具执行完成，结果长度: {} 字符", toolResult.length());
                    } else {
                        log.warn("⚠️ 找不到工具: {}", toolName);
                        toolResult = "系统错误：找不到名为 " + toolName + " 的工具";
                    }

                    // 4. 把工具执行结果返回给 AI (这就叫 "Function Calling Loop")
                    history.add(ChatRequest.Message.builder()
                            .role("system")
                            .content("工具 [" + toolName + "] 执行结果:\n" + toolResult)
                            .build());

                    log.info("🔄 5. 正在将工具结果回传给 AI...");
                    // 5. 再次请求 LLM，让它根据文件内容生成最终回答
                    return sendRequestToLlm();

                } catch (Exception e) {
                    log.error("❌ 工具调用流程发生异常", e);
                    return "工具调用失败: " + e.getMessage();
                }
            }

            // 如果不是工具调用，直接返回回答
            return aiResponse;

        } catch (Exception e) {
            log.error("API 调用异常", e);

            // 🛠️ 修复核心：如果报错了，把刚才加进去的那句话删掉！
            // 这样下次发请求时，就不会带上这句失败的话了。
            history.remove(history.size() - 1);

            return "调用失败 (已回滚上下文): " + e.getMessage();
        }
    }

    // 抽取出来的私有方法，避免代码重复
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
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(apiUrl, entity, ChatResponse.class);

            if (response.getBody() != null && !response.getBody().getChoices().isEmpty()) {
                String reply = response.getBody().getChoices().get(0).getMessage().getContent();
                // 记录 AI 的回复
                history.add(ChatRequest.Message.builder().role("assistant").content(reply).build());
                return reply;
            }
            return "AI 响应为空";
        } catch (Exception e) {
            log.error("API 调用异常", e);
            return "API Error: " + e.getMessage();
        }
    }

    public void clearMemory() {
        initMemory();
    }
}