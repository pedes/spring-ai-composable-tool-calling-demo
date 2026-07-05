# Composable Tool Calling Notes

These notes summarize the Spring AI 2.0 APIs exercised by the sample and a few adjacent recipes worth trying next.

## Core Loop

Spring AI 2.0 runs tool calling through the advisor chain. `DefaultChatClient` auto-registers a `ToolCallingAdvisor`, which inspects model responses, executes requested tools through `ToolCallingManager`, appends tool responses to the conversation history, and loops until the model returns a final response with no tool calls.

In this project, `AgentController#basic` demonstrates the framework-controlled path:

```java
String answer = chatClient.prompt()
    .user(request.message())
    .tools(travelTools)
    .call()
    .content();
```

## Tool Search

`ToolSearchToolCallingAdvisor` is useful when the tool catalog is large. It indexes all registered tools for the session, initially exposes only the built-in `toolSearchTool`, and adds discovered tool definitions to later model calls.

This project enables it with:

```yaml
spring.ai.chat.client.tool-search-advisor.enabled: true
spring.ai.chat.client.tool-search-advisor.tool-index-type: regex
```

The Spring AI 2.0.0 reference guide mentions a `spring-ai-starter-tool-search-advisor` dependency. The 2.0.0 BOM currently manages the module as `spring-ai-tool-search-advisor`, which is what this repository uses.

The `/api/agent/search` endpoint passes a session id:

```java
chatClient.prompt()
    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, sessionId))
    .user(request.message())
    .tools(travelTools)
    .call()
    .content();
```

## Memory Ordering

Advisor ordering controls whether memory sees only the final answer or every tool-loop iteration.

Default memory placement is outside the tool loop:

```java
var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
```

If memory is placed inside the tool loop, use a repository that supports tool request/response messages and disable the tool advisor's internal history when wiring it manually:

```java
var toolCallingAdvisor = ToolCallingAdvisor.builder()
    .toolCallingManager(toolCallingManager)
    .disableInternalConversationHistory()
    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
    .build();

var chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 400)
    .build();
```

## User-Controlled Execution

Use user-controlled execution when an application must approve tools, emit intermediate progress, or interrupt the loop.

```java
ToolCallback[] tools = ToolCallbacks.from(travelTools);
ChatOptions chatOptions = ToolCallingChatOptions.builder()
    .toolCallbacks(tools)
    .build();

ChatClientResponse response = chatClient.prompt()
    .user(question)
    .options(chatOptions)
    .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
    .call()
    .chatClientResponse();

Prompt prompt = new Prompt(List.of(new UserMessage(question)), chatOptions);

while (response.chatResponse() != null && response.chatResponse().hasToolCalls()) {
    ToolExecutionResult result =
        toolCallingManager.executeToolCalls(prompt, response.chatResponse());

    prompt = new Prompt(result.conversationHistory(), chatOptions);
    response = chatClient.prompt()
        .messages(result.conversationHistory())
        .options(chatOptions)
        .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
        .call()
        .chatClientResponse();
}
```

## Tool Argument Augmentation

Spring AI 2.0 can wrap tools with an augmented schema. The model fills extra arguments, such as a reason for calling the tool, while the original tool method receives only its own parameters.

```java
record AgentThinking(
    @ToolParam(description = "Why this tool should be called")
    String innerThought
) {}

var provider = AugmentedToolCallbackProvider.<AgentThinking>builder()
    .toolObject(travelTools)
    .argumentType(AgentThinking.class)
    .argumentConsumer(event -> log.info("{} -> {}",
        event.toolDefinition().name(),
        event.arguments().innerThought()))
    .build();
```
