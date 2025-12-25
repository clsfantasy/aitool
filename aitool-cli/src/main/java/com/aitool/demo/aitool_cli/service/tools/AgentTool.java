package com.aitool.demo.aitool_cli.service.tools;

/**
 * 策略模式接口：所有 Agent 工具必须实现此接口
 */
public interface AgentTool {

    // 工具的唯一名称 (例如 "read_file")，AI 通过这个名字调用它
    String getName();

    // 工具描述 (例如 "读取本地文件内容")，会放入 System Prompt 给 AI 看
    String getDescription();

    // 核心执行逻辑
    String execute(String args);
}