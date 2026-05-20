package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.AiChatDTO;
import com.sky.result.Result;
import com.sky.tools.UserTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/ai")
@RequiredArgsConstructor
@Tag(name = "AI助手接口")
@Slf4j
public class UserAgentController {

    private final ChatClient chatClient;

    private final UserTools userTools;

    private final OpenAiEmbeddingModel embeddingModel;

    private final VectorStore vectorStore;


    @PostMapping("/chat")
    @Operation(summary = "AI聊天对话")
    public Result<String> chat(@RequestBody AiChatDTO aiChatDTO) {
        log.info("AI聊天请求：{}", aiChatDTO.getMessage());



        String reply = chatClient.prompt()
                .tools(userTools)
                .user(aiChatDTO.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, BaseContext.getCurrentId()))
                .call()
                .content();

        log.info("AI聊天回复：{}", reply);
        return Result.success(reply);
    }

}
