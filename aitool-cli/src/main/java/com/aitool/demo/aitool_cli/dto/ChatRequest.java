package com.aitool.demo.aitool_cli.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatRequest {
    private String model;
    private List<Message> messages;
    private boolean stream;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}