package com.aitool.demo.aitool_cli.command;

import com.aitool.demo.aitool_cli.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class AiCommands {

    @Autowired
    private AiService aiService;

    @ShellMethod(key = "ask", value = "向 AI 提问")
    public String ask(String question) {
        return aiService.callAi(question);
    }

    // 新增命令
    @ShellMethod(key = "new", value = "开始新对话(清空记忆)")
    public String newChat() {
        aiService.clearMemory();
        return "记忆已清空，我们可以重新开始了。";
    }
}