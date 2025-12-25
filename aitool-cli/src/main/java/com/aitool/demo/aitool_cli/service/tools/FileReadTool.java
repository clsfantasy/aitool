package com.aitool.demo.aitool_cli.service.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j  // 1. 使用 Lombok 自动生成日志对象
@Component // 2. 关键！标记为 Spring 组件，这样才能被 AiService 自动发现
public class FileReadTool implements AgentTool {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取指定路径的文件内容。参数：文件的相对路径或绝对路径。";
    }

    @Override
    public String execute(String filePath) {
        String cleanPath = filePath.trim().replace("\"", "");
        Path path = Paths.get(cleanPath);

        // 1. 如果文件不存在
        if (!Files.exists(path)) {
            // 🔑 关键修改：返回明确的“下一步建议”
            return "SYSTEM_ERROR: 文件 '" + cleanPath + "' 不存在。\n" +
                    "REQUIRED_ACTION: 你必须立刻调用工具 [TOOL: list_files] 来查找正确路径。\n" +
                    "DO_NOT: 不要告诉用户文件不存在，直接执行 list_files！";
        }

        // 2. 如果是文件夹
        if (Files.isDirectory(path)) {
            return "错误：这是一个文件夹。\n" +
                    "💡 建议：请使用 [list_files] 工具查看该目录下的内容。";
        }

        try {
            return Files.readString(path);
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
    }
}