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
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Spring Ai Tool
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/12/14 09:51
 */
public class GoogleADKTest {

    private static final Logger log = LoggerFactory.getLogger(GoogleADKTest.class);

    public static void main(String[] args) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://apis.***.cn")
                .apiKey("sk-efen7WX8Q8vGvBps3f7c9a34578d41BbBc****")
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4.1")
                        .toolCallbacks(new ArrayList<>() {{
                            addAll(List.of(sseMcpClient()));
                        }})
                        .build())
                .build();

        // agent 测试
        LlmAgent agent01 = LlmAgent.builder()
                .name("agent01")
                .description("规划与Skill检索智能体")
                .model(new SpringAI(chatModel))
                .instruction("""
                        你是 agent01，负责“理解需求 → 检索 Skill → 形成可执行计划 → 交接执行”。
                        
                        你必须优先使用可用的工具能力：
                        - 使用 `skillport-mcp.search_skills(query)`：按用户目标检索 skill
                        - 使用 `skillport-mcp.load_skill(skill_id)`：加载 skill 的完整指令，并拿到该 skill 的文件系统绝对路径 (path)
                        
                        输入：
                        - 用户的原始请求
                        
                        工作流程（每一轮都执行）：
                        1) 解析用户请求：提取目标、约束、系统类型（若未知则标记 unknown）
                        2) 生成检索 query（中文优先，必要时补充英文关键词），调用 search_skills
                        3) 选择最匹配的 1 个 skill（如有多个候选，按“与目标一致性、是否包含脚本/资源、执行步骤明确”排序）
                        4) 调用 load_skill 获取：
                           - skill 的 instructions
                           - skill 的绝对路径 path（后续所有脚本/文件均基于该路径拼接）
                        5) 从 instructions 中抽取“要执行的脚本/顺序/输入输出/成功判定”，形成执行计划
                        
                        输出要求：
                        - 只输出一个 JSON，对后续执行友好，字段固定如下（可为空但必须出现）：
                        
                        {
                          "user_intent": "...",
                          "assumptions": ["..."],
                          "os_hint": "macOS|Windows|Linux|unknown",
                          "skill": {
                            "id": "...",
                            "name": "...",
                            "description": "...",
                            "path": "绝对路径",
                            "instructions_summary": "从 instructions 提炼的要点"
                          },
                          "execution_plan": [
                            {
                              "step": 1,
                              "title": "......",
                              "command": "......",
                              "expected_output": "......",
                              "success_criteria": "......"
                            }
                          ],
                          "handoff": {
                            "what_to_run_first": "......",
                            "what_to_run_next": "......",
                            "artifacts_to_collect": ["stdout", "stderr", "exit_code"]
                          }
                        }
                        """)
                .outputKey("skill_content")
                .build();

        LlmAgent agent02 = LlmAgent.builder()
                .name("agent02")
                .description("执行与修复智能体")
                .model(new SpringAI(chatModel))
                .instruction("""
                        你是 agent02，负责“按计划执行 → 自动修复 → 产出可交付结果 → 驱动下一轮循环”。
                        
                        输入：
                        - {skill_content}：来自 agent01 的 JSON（包含 skill.path 与 execution_plan）
                        
                        执行原则：
                        1) 严格按 execution_plan 的顺序执行命令
                        2) 任何一步失败，都要尝试自动修复后重试（最多 2 次）
                        3) 如果当前环境不支持直接执行命令/脚本，你必须：
                           - 输出“下达命令”式的可复制命令清单（按顺序）
                           - 明确要求用户提供每一步的 stdout/stderr/exit_code
                           - 将状态标记为 NEEDS_USER_OUTPUT，以便进入下一轮循环
                        
                        常见错误的自动修复策略：
                        - Permission denied：先执行 `chmod +x <script_path>`，再重试原命令
                        - command not found：识别缺失依赖并给出安装命令（按 os_hint），再重试
                        - path not found：基于 skill.path 重新拼接/校验脚本位置
                        
                        输出要求：
                        - 只输出一个 JSON，字段固定如下（可为空但必须出现）：
                        {
                          "status": "SUCCESS|NEEDS_USER_OUTPUT|FAILED",
                          "executed": [
                            {
                              "step": 1,
                              "command": "...",
                              "exit_code": 0,
                              "stdout": "...",
                              "stderr": "..."
                            }
                          ],
                          "fixes_applied": ["..."],
                          "summary": "一句话总结本轮结果",
                          "next_request": "如果需要用户补充信息，这里写清楚要什么"
                        }
                        """)
                .outputKey("skill_content")
                .build();

        LoopAgent refinementLoop =
                LoopAgent.builder()
                        .name("refinement_loop")
                        .description("规划与执行循环，直到任务成功完成")
                        .subAgents(agent01, agent02)
                        .maxIterations(5)
                        .build();

        LlmAgent agent03 = LlmAgent.builder()
                .name("agent03")
                .description("结果整理与输出智能体")
                .model(new SpringAI(chatModel))
                .instruction("""
                        你是 agent03，负责“最终汇总输出”，面向用户交付可直接使用的结果。
                        
                        输入：
                        - {skill_content}：规划与交接信息
                        
                        输出规则：
                        1) 如果 status=SUCCESS：基于执行输出给出最终结论与建议步骤
                        2) 如果 status=NEEDS_USER_OUTPUT：输出一份“下达命令 + 需要用户回传的结果清单”
                        3) 如果执行过程中有 fixes_applied：在结果中简要说明做了哪些自动修复
                        4) 若 skill 的 instructions 要求特定话术结构（例如 reference.md 风格）：
                           - 能读取到 reference.md 时，严格按其结构输出
                           - 否则按“下达命令”的口吻输出，保持结构清晰、步骤可复制
                        """)
                .build();

        SequentialAgent sequentialAgent =
                SequentialAgent.builder()
                        .name("sequentialAgent")
                        .description("首先通过 Loop 进行规划和执行，最后由 agent03 汇总输出结果。")
                        .subAgents(refinementLoop, agent03)
                        .build();

        InMemoryRunner runner = new InMemoryRunner(sequentialAgent);

        Session session = runner
                .sessionService()
                .createSession("sequentialAgent", "fzw")
                .blockingGet();

        Flowable<Event> events = runner.runAsync("fzw", session.id(),
                Content.fromParts(Part.fromText("基于 skill 解答，电脑性能优化")));

        System.out.print("\nAgent > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));

//        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
//            while (true) {
//                System.out.print("\nYou > ");
//                String userInput = scanner.nextLine();
//
//                if ("quit".equalsIgnoreCase(userInput)) {
//                    break;
//                }
//
//                Content userMsg = Content.fromParts(Part.fromText(userInput));
//                events = runner.runAsync("fzw", session.id(), userMsg);
//
//                System.out.print("\nAgent > ");
//                events.blockingForEach(event -> System.out.println(event.stringifyContent()));
//            }
//        }

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
