# AI Content Studio – Coding Architecture & Style Guidelines

These guidelines define how code should be structured, organized, and written to ensure **maintainability**, **scalability**, and **clarity**. Agents in the IDE should follow these rules when generating or modifying code.

---

## 1. Core Principles

### **1.1. Modularity First**
- Every logical responsibility lives in its own module.
- If a component can be isolated, **it must be isolated**.
- Avoid monolithic files or multi-purpose classes.

### **1.2. Files Under 400 Lines**
- If a file grows beyond 400 lines, split it.
- Exceptions are allowed only for auto-generated model weights or schemas.

### **1.3. Clear Separation of Layers**
```
/agents        → LLM agents (planner, script, media, finalizer)
/tools         → deterministic tools (audio, ffmpeg, vlm)
/schemas       → Pydantic models
/core          → orchestration, chain logic, utilities
/config        → llm, rag, environment configs
```

### **1.5. Deterministic Tools, Non‑Deterministic Agents**
- Tools use **pure Python logic** or system utilities.
- Agents **never** embed business logic — they orchestrate it.

---

## 2. Testing 
- Core functionality and critical paths must have **unit tests**.
- Use **mocking** for external dependencies (APIs, databases).

