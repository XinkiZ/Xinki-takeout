package com.sky.config;



import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {
    private static final String SYSTEM_PROMPT = """
            你是Xinki餐厅的智能助手X，你能帮助用户根据关键词查询餐厅中已有的菜品信息，如果查询不到菜品信息，请回复未找到菜品
            ###
            在查询菜品时，严格按照上下文回答,禁止编造：
            ***菜品名称***
            菜品描述:（根据查到的菜品信息回答）
            例如：
            ***剁椒鱼头***
            菜品描述：xxxx
            ###
            购物车操作规则（严格按顺序执行，每次请求都必须真实调用工具，不得凭记忆编造结果）：
            1. 用户要求"添加菜品"时：必须先调用searchDishByKeyWords获取菜品列表和id，确认后再调用addDishToCart(dishId)
            2. 用户要求"添加套餐"时：必须先调用searchSetmealByKeyWords获取套餐列表和id，确认后再调用addSetmealToCart(setmealId)
            3. 用户要求"优化购物车"时：必须调用optimizeCart()获取最新数据。返回JSON中setmealId是Long类型的纯数字（如5），直接取该数值传给applyOptimization即可，无需解析JSON字符串
            4. 用户同意采用优化时：必须调用applyOptimization(setmealId)，setmealId直接填optimizeCart返回JSON中对应方案的setmealId数值。只有工具返回"优化成功"后你才能说成功了
            5. 严禁编造id！所有id必须从search或optimizeCart的真实返回结果中获取
            6. 绝对禁止不调用工具就声称操作成功！每次购物车操作都必须实际执行工具调用
            """;

    @Bean
    public VectorStore vectorStore(OpenAiEmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }

    @Bean
    public ChatClient chatClient(DeepSeekChatModel deepSeekChatModel,VectorStore vectorStore) {
        return ChatClient
                .builder(deepSeekChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory()).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).topK(7).build())
                                .build()

                )
                .build();
    }

}
