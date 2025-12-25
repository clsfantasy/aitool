package com.aitool.demo.aitool_cli.service.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ListDirTool implements AgentTool {

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "列出当前目录下的文件和文件夹结构。建议在读取文件前先使用此工具确认路径。";
    }

    @Override
    public String execute(String args) {
        // 默认列出当前工作目录
        File currentDir = new File(System.getProperty("user.dir"));

        // 为了避免 Token 爆炸，我们这里写一个简单的递归树生成器
        // 限制深度为 2 层，防止把 node_modules 这种黑洞打出来
        StringBuilder sb = new StringBuilder();
        sb.append("当前工作目录: ").append(currentDir.getAbsolutePath()).append("\n");
        listDirectory(currentDir, 0, sb);
        return sb.toString();
    }

    private void listDirectory(File dir, int depth, StringBuilder sb) {
        if (depth > 3) return; // 限制深度

        File[] files = dir.listFiles();
        if (files == null) return;

        // 排序：文件夹在前，文件在后
        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareTo(f2.getName());
        });

        for (File file : files) {
            // 忽略隐藏文件 (.git, .idea 等)
            if (file.getName().startsWith(".")) continue;
            // 忽略构建目录
            if (file.getName().equals("target") || file.getName().equals("build")) continue;

            String indent = "  ".repeat(depth);
            if (file.isDirectory()) {
                sb.append(indent).append("📂 ").append(file.getName()).append("/\n");
                listDirectory(file, depth + 1, sb);
            } else {
                sb.append(indent).append("📄 ").append(file.getName()).append("\n");
            }
        }
    }
}