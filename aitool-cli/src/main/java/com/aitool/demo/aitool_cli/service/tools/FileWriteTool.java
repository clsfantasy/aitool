package com.aitool.demo.aitool_cli.service.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

@Slf4j
@Component
public class FileWriteTool implements AgentTool {

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "覆盖写入文件。参数格式: [路径] [内容]。注意：这会完全覆盖旧内容！";
    }

    @Override
    public String execute(String args) {
        // 1. 简单的参数解析 (假设第一个空格前是路径，后面全是内容)
        // 注意：这只是一个简易实现，生产环境需要更强的解析器来处理引号等
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            return "错误：参数不足。格式应为: write_file [路径] [内容]";
        }

        String filePath = parts[0].trim();
        String content = parts[1]; // 这里通常包含换行符，需要保留

        // 2. 🛡️ 人机回环 (Human-in-the-loop) 安全检查
        // 在写入前，强行暂停，询问用户
        System.out.println("\n⚠️  ========== AI 请求写入文件 ========== ⚠️");
        System.out.println("目标文件: " + filePath);
        System.out.println("写入内容预览 (前100字符): " + (content.length() > 100 ? content.substring(0, 100) + "..." : content));
        System.out.println("⚠️  这将覆盖原文件！是否允许? (y/n): ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();

        if (!"y".equalsIgnoreCase(input.trim())) {
            log.info("用户拒绝了写入操作: {}", filePath);
            return "操作被用户拒绝。";
        }

        // 3. 执行写入
        try {
            Path path = Paths.get(filePath);

            // 自动创建父目录 (如果不存在)
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Files.writeString(path, content);
            log.info("文件写入成功: {}", filePath);
            return "成功写入文件: " + filePath;

        } catch (IOException e) {
            log.error("写入文件失败", e);
            return "写入失败: " + e.getMessage();
        }
    }
}