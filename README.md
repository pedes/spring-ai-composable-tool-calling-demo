# Spring AI Composable Tool Calling Demo

This repository demonstrates the Spring AI 2.0 composable tool-calling architecture described in Christian Tzolov's Spring blog post, [Tool Calling in Spring AI 2.0: A Composable, Agentic Architecture](https://spring.io/blog/2026/06/15/spring-ai-composable-tool-calling/).

The demo is a small Spring Boot API for a travel-planning assistant. The model can call local application methods for current time, weather, flight search, booking, hotel search, attraction lookup, and budget math.

## What It Shows

- `@Tool` and `@ToolParam` methods in `TravelTools`.
- Per-request tool registration with `ChatClient.prompt().tools(...)`.
- The advisor-managed tool loop via Spring AI's auto-registered `ToolCallingAdvisor`.
- Progressive tool disclosure with the Spring AI `spring-ai-tool-search-advisor` module.
- Conversation/session isolation using `ChatMemory.CONVERSATION_ID`.
- Deterministic tools that are easy to test without calling an LLM.

Spring AI 2.0 lifts tool execution into the advisor chain. That means tool loops, memory, observability, retries, and custom advisors all compose through one mechanism: advisor ordering. This sample keeps the running code approachable and links to deeper recipes in [docs/composable-tool-calling-notes.md](docs/composable-tool-calling-notes.md).

## Requirements

- Java 17+
- Maven 3.9+
- An OpenAI API key

Spring AI 2.0.0 supports Spring Boot 4.0.x and 4.1.x. This sample uses Spring Boot 4.0.0 and the Spring AI 2.0.0 BOM.

## Run

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

The application listens on `http://localhost:8080`.

## Try It

Basic framework-controlled tool calling:

```bash
curl -s http://localhost:8080/api/agent/basic \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "I am in Madrid. Check the weather, find a flight to Amsterdam tomorrow, and book the cheapest option."
  }'
```

Tool-search mode with a stable session id:

```bash
curl -s http://localhost:8080/api/agent/search \
  -H 'Content-Type: application/json' \
  -d '{
    "sessionId": "andres-demo",
    "message": "Plan a two-night Amsterdam trip under 900 EUR. Use weather, flights, hotels, and attractions."
  }'
```

Health check and tool inventory:

```bash
curl -s http://localhost:8080/api/agent/tools
```

## Architecture

The application is intentionally thin around Spring AI so the tool-calling mechanics are easy to see.

```text
+-------------------+
| HTTP Client / curl |
+---------+---------+
          |
          v
+-------------------+
| AgentController   |
|                   |
| POST /basic       |
| POST /search      |
| GET  /tools       |
+---------+---------+
          |
          | builds prompts with tools
          v
+-------------------+
| ChatClient        |
|                   |
| system prompt     |
| user prompt       |
| TravelTools       |
| conversation id   |
+---------+---------+
          |
          | advisor chain
          v
+---------------------------------------------------+
| ToolCallingAdvisor or ToolSearchToolCallingAdvisor |
|                                                   |
| 1. inject tool definitions                        |
| 2. detect model tool-call requests                |
| 3. execute tools through ToolCallingManager       |
| 4. loop until the model returns a final answer    |
+--------------------+------------------------------+
                     |
             +-------+-------+
             |               |
             v               v
  +------------------+   +-------------------+
  | TravelTools      |   | OpenAI Chat Model |
  |                  |   |                   |
  | @Tool methods    |   | decides when to   |
  | deterministic    |   | call tools and    |
  | travel domain    |   | writes the answer |
  +------------------+   +-------------------+
```

## Request Flow

1. A client sends a travel request to `AgentController`.
2. The controller creates a `ChatClient` prompt and registers `TravelTools`.
3. Spring AI sends the prompt and tool definitions to the chat model.
4. If the model asks for a tool, the advisor calls `ToolCallingManager`.
5. `ToolCallingManager` invokes the matching `@Tool` method.
6. The tool result is returned to the model as tool context.
7. The advisor repeats the loop until the model returns a normal final answer.
8. The controller returns the final answer as JSON.

In `/api/agent/search`, the request also passes `ChatMemory.CONVERSATION_ID`. That gives the tool-search advisor a stable session key for isolating tool indexes between conversations.

## Project Structure

```text
.
|-- pom.xml
|-- README.md
|-- docs/
|   `-- composable-tool-calling-notes.md
|-- src/
|   |-- main/
|   |   |-- java/com/example/springai/
|   |   |   |-- SpringAiToolCallingDemoApplication.java
|   |   |   |-- tools/
|   |   |   |   `-- TravelTools.java
|   |   |   `-- web/
|   |   |       `-- AgentController.java
|   |   `-- resources/
|   |       `-- application.yml
|   `-- test/
|       `-- java/com/example/springai/tools/
|           `-- TravelToolsTest.java
`-- .github/
    `-- workflows/
        `-- ci.yml
```

## Component Guide

- `SpringAiToolCallingDemoApplication` starts the Spring Boot application.
- `AgentController` exposes the demo API and shows the two main invocation styles.
- `TravelTools` is the local tool catalog. Its methods are normal Java methods annotated with `@Tool`.
- `application.yml` contains the OpenAI model settings and visible tool-search configuration.
- `TravelToolsTest` verifies deterministic tool behavior without making LLM calls.
- `docs/composable-tool-calling-notes.md` contains advanced recipes for memory ordering, manual tool-loop control, and argument augmentation.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/agent/tools` | Lists the local tool catalog. |
| `POST` | `/api/agent/basic` | Registers the tool catalog directly for one framework-controlled request. |
| `POST` | `/api/agent/search` | Uses a session id so tool-search mode can isolate tool discovery by conversation. |

Request body:

```json
{
  "sessionId": "andres-demo",
  "message": "Plan a two-night Amsterdam trip under 900 EUR."
}
```

Response body:

```json
{
  "sessionId": "andres-demo",
  "answer": "..."
}
```

## Configuration

`src/main/resources/application.yml` keeps the tool-search advisor settings visible:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-5-mini}
          temperature: 0.2
    chat:
      client:
        tool-search-advisor:
          enabled: ${TOOL_SEARCH_ENABLED:true}
          tool-index-type: ${TOOL_SEARCH_INDEX:regex}
```

The Spring AI reference guide describes a starter named `spring-ai-starter-tool-search-advisor`; the 2.0.0 BOM resolved by this project manages the module as `spring-ai-tool-search-advisor`. The demo therefore uses the BOM-managed module and leaves the properties in place for applications that add the starter/autoconfiguration variant.

## Test

```bash
mvn test
```

In restricted environments, a project-local Maven cache also works:

```bash
mvn -Dmaven.repo.local=.m2/repository test
```

## References

- [Spring blog post](https://spring.io/blog/2026/06/15/spring-ai-composable-tool-calling/)
- [Spring AI Tool Calling reference](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI Dynamic Tool Discovery guide](https://docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html)
