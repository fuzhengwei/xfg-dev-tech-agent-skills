package cn.bugstack.ai.agent.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.models.springai.SpringAI;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.MimeTypeUtils;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/**
 * Spring Ai Tool
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/14 09:51
 */
public class SpringAiToolTest {

    private static final Logger log = LoggerFactory.getLogger(SpringAiToolTest.class);

    public static void main(String[] args) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://apis.***.cn")
                .apiKey("sk-efen7WX8Q8vGvBps3f7c9a34578d41BbBc5*****")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();
        // https://github.com/spring-ai-community/spring-ai-agent-utils
        ToolCallback toolCallback = SkillsTool.builder()
                .addSkillsDirectory("/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/road-map/xfg-dev-tech-agent-skills/docs/skills")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4.1")
                        .toolCallbacks(new ArrayList<>() {{
                            addAll(List.of(sseMcpClient()));
                        }})
                        //  .toolCallbacks(toolCallback)       
                        .build())
                .build();

//        String call = chatModel.call("你哪有哪些 skill 工具能力");
        String call = chatModel.call("基于 skill 解答，电脑性能优化");

        log.info("测试结果:{}", call);

    }

    /**
     * https://github.com/gotalab/skillport
     * pip3 config set global.index-url http://mirrors.aliyun.com/pypi/simple/
     * pip3 config set install.trusted-host mirrors.aliyun.com
     * pip3 config list
     * pip3 install uvx
     */
    public static ToolCallback[] sseMcpClient() {
        ServerParameters stdioParams = ServerParameters.builder("uvx")
                .args("skillport-mcp")
                .env(new HashMap<>() {{
                    put("SKILLPORT_SKILLS_DIR", "/Users/fuzhengwei/coding/gitcode/KnowledgePlanet/road-map/xfg-dev-tech-agent-skills/docs/skills");
                }})
                .build();

        McpSyncClient mcpSyncClient = McpClient.sync(new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                .requestTimeout(Duration.ofSeconds(35000)).build();

        McpSchema.InitializeResult initialize = mcpSyncClient.initialize();

        return SyncMcpToolCallbackProvider.builder().mcpClients(mcpSyncClient).build()
                .getToolCallbacks();
    }

}
