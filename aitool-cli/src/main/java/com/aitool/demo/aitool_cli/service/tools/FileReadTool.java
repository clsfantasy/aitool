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

        log.info("📂 DEBUG: 当前工作目录是: {}", System.getProperty("user.dir"));

        // 安全性检查：防止读取空路径
        if (filePath == null || filePath.isBlank()) {
            return "错误：文件名不能为空";
        }

        // 去掉可能存在的引号或空格
        String cleanPath = filePath.trim().replace("\"", "");

        try {
            // 3. 使用 Java NIO 读取文件
            Path path = Paths.get(cleanPath);

            // 简单的安全检查：只允许读取当前项目下的文件 (可选)
            // if (!path.toAbsolutePath().startsWith(System.getProperty("user.dir"))) { ... }

            if (!Files.exists(path)) {
                return "错误：文件不存在 -> " + cleanPath;
            }

            String content = Files.readString(path);
            log.info("成功读取文件: {}", cleanPath); // 记录日志
            return content;

        } catch (IOException e) {
            log.error("读取文件失败: {}", cleanPath, e);
            return "读取文件发生异常: " + e.getMessage();
        }
    }
}