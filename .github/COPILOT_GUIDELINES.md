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

## 3. Learning (Improved Version)

When a prompt is prefixed with **`#learning`**, the assistant should switch into *teaching mode* and follow this structured workflow:

### Step 1 — Internal Reasoning (Hidden from User)
- Silently think through possible solutions.
- Evaluate trade-offs, constraints, and alternative approaches.
- Select the most effective, generalizable solution.

*(This step is not shown to the user.)*

---

### Step 2 — Identify Key Concepts
Present **exactly 3 core concepts** that form the foundation of the solution.

Format:

**Key Concepts**
1. Concept #1 — short, clear description
2. Concept #2 — short, clear description
3. Concept #3 — short, clear description

---

### Step 3 — Explain the Concepts Simply
Explain the three concepts in *beginner-friendly* language.  
Use analogies or short examples when helpful.

Format:

**Explanation**
- *Concept 1:* explanation…
- *Concept 2:* explanation…
- *Concept 3:* explanation…

---

### Step 4 — Provide the Implementation
Deliver a clean, correct, and practical solution (code, architecture, instructions, etc.).

Format:

**Implementation**
\`\`\`ts
// example code
\`\`\`

---

### Example

**Prompt:**  
`#learning analyze the project and build a cache solution so that we reduce the number of calls to an external API`

**Answer:**

**Key Concepts**
1. **Cache Layering** — deciding what data should be cached and at which layer.
2. **Expiration Strategy** — defining how long cached data remains valid.
3. **Cache Invalidation** — determining when and how cached data should be refreshed.

**Explanation**
- *Cache Layering:* Like keeping frequently used items on your desk instead of walking to another room.
- *Expiration Strategy:* Cached data gets stale, so we set how long it stays before being refreshed.
- *Cache Invalidation:* Forcing a refresh when the underlying data changes.

**Implementation**
Here you should write an implementation
