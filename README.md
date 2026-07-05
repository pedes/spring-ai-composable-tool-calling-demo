# Spring AI Composable Tool Calling Demo

This repository demonstrates the Spring AI 2.0 tool-calling architecture described in Christian Tzolov's Spring blog post, [Tool Calling in Spring AI 2.0: A Composable, Agentic Architecture](https://spring.io/blog/2026/06/15/spring-ai-composable-tool-calling/).

The demo is a small Spring Boot API for a travel-planning assistant. The model can call local application methods for current time, weather, flight search, booking, hotel search, attraction lookup, and budget math.

## What It Shows

- `@Tool` and `@ToolParam` methods in `TravelTools`.
- Per-request tool registration with `ChatClient.prompt().tools(...)`.
- The advisor-managed tool loop via Spring AI's auto-registered `ToolCallingAdvisor`.
- Progressive tool disclosure with the Spring AI `spring-ai-tool-search-advisor` module.
- Conversation/session isolation using `ChatMemory.CONVERSATION_ID`.
- Deterministic tools that are easy to test without calling an LLM.

The blog explains that Spring AI 2.0 lifts tool execution into the advisor chain, where advisor ordering controls whether memory and observability sit outside the loop or see each tool iteration. This repo keeps the running code simple and includes extra recipes in [docs/composable-tool-calling-notes.md](docs/composable-tool-calling-notes.md).

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

Health check:

```bash
curl -s http://localhost:8080/api/agent/tools
```

## Configuration

`src/main/resources/application.yml` keeps the tool-search advisor settings visible:

```yaml
spring:
  ai:
    chat:
      client:
        tool-search-advisor:
          enabled: true
          tool-index-type: regex
```

The Spring AI reference guide describes a starter named `spring-ai-starter-tool-search-advisor`; the 2.0.0 BOM resolved by this project manages the module as `spring-ai-tool-search-advisor`. The demo therefore uses the BOM-managed module and leaves the properties in place for applications that add the starter/autoconfiguration variant.

## Source Map

- `SpringAiToolCallingDemoApplication` - Spring Boot entry point.
- `TravelTools` - `@Tool` catalog used by the model.
- `AgentController` - REST endpoints that demonstrate basic and tool-search calls.
- `TravelToolsTest` - fast tests for deterministic tool behavior.
- `docs/composable-tool-calling-notes.md` - implementation notes and advanced recipes from the blog post.

## References

- [Spring blog post](https://spring.io/blog/2026/06/15/spring-ai-composable-tool-calling/)
- [Spring AI Tool Calling reference](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI Dynamic Tool Discovery guide](https://docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html)
