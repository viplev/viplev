# Agent Overview

This document describes the subagent architecture for the ghissue and ghpr workflows.
The goal is to minimize token usage by assigning the right model to each task.

## Architecture

```
/ghissue #42                         /ghpr #42
     |                                    |
     v                                    v
.claude/skills/ghissue/SKILL.md     .claude/skills/ghpr/SKILL.md
  (orchestrator)                      (orchestrator)
     |                                    |
     +---> github-issue-reader              +---> github-issue-reader
     +---> github-issue-planner             +---> github-issue-planner
     +---> github-issue-implementer         +---> github-issue-implementer
     +---> github-issue-deliver             +---> github-issue-deliver
```

The orchestrator skill runs in the main context. It calls each agent in
sequence, passes relevant output from one agent as input to the next, and
handles user interaction (approval gates) between phases.

## Agent Definitions

### Agent 1: github-issue-reader (Haiku)

| Field  | Value                                                        |
|--------|--------------------------------------------------------------|
| File   | `.claude/agents/github-issue-reader.md`                      |
| Model  | haiku                                                        |
| Tools  | Bash, Read                                                   |
| Memory | none                                                         |
| Input  | Issue number (ghissue) or PR number (ghpr)                   |
| Output | Full issue/PR content + labels + branch name                 |

**Responsibilities:**
- `git pull`
- Fetch issue/PR via `gh` CLI
- Check for existing branch, create if needed
- Checkout the branch
- Return full unmodified issue body + comments (no summarization)

---

### Agent 2: github-issue-planner (Opus)

| Field  | Value                                                        |
|--------|--------------------------------------------------------------|
| File   | `.claude/agents/github-issue-planner.md`                     |
| Model  | opus                                                         |
| Tools  | Read, Glob, Grep, Bash                                       |
| Memory | none                                                         |
| Input  | Full issue content + any user-provided context               |
| Output | Structured YAML plan (file paths, method signatures, pseudocode) |

**Output format:**
```yaml
commit_type: feat|fix|docs|unknown
scope: <component>
files_to_change:
  - path: <full path>
    action: create|modify
    changes:
      - method: <signature>
        description: <pseudocode>
openapi_changes: [optional]
files_to_test:
  - path: <full path>
    action: create|modify
    tests:
      - name: <test name>
        type: unit|integration
        description: <what to verify>
breaking_changes: [optional]
```

---

### Agent 3: github-issue-implementer (Sonnet)

| Field  | Value                                                        |
|--------|--------------------------------------------------------------|
| File   | `.claude/agents/github-issue-implementer.md`                 |
| Model  | sonnet                                                       |
| Tools  | Edit, Write, NotebookEdit, Glob, Grep, Read, WebFetch, WebSearch, Bash |
| Memory | none                                                         |
| Input  | Approved plan from Agent 2                                   |
| Output | Files changed + build result                                 |

**Key rules:**
- Follows the plan exactly — does not improvise
- OpenAPI changes: edit yaml -> `./gradlew openApiGenerate` -> implement
- Build loop: `./gradlew build` -> fix failures -> repeat until green
- **Existing test fails -> STOP.** Assumes the test is correct, re-examines own code.
  Only modifies existing tests with explicit user approval via orchestrator.

---

### Agent 4: github-issue-deliver (Haiku)

| Field  | Value                                                        |
|--------|--------------------------------------------------------------|
| File   | `.claude/agents/github-issue-deliver.md`                     |
| Model  | haiku                                                        |
| Tools  | Bash                                                         |
| Memory | none                                                         |
| Input  | Issue/PR number, branch, commit type, scope, summary, mode   |
| Output | PR URL (issue mode) or comment reply count (pr mode)         |

**Two modes:**
- **issue**: git add -> commit -> push -> `gh pr create` (title ends with `(#N)`, body starts with `Closes #N`)
- **pr**: git add -> commit -> push -> reply to each review comment via `gh api`

---

## Orchestrator Flows

### ghissue (`/ghissue #42`)

```
Phase 1: github-issue-reader (haiku)
  -> orchestrator presents issue, asks for extra context

Phase 2: github-issue-planner (opus)
  -> orchestrator presents plan, AskUserQuestion: approve/adjust

Phase 3: github-issue-implementer (sonnet)
  -> if existing test conflict: orchestrator asks user via AskUserQuestion
  -> orchestrator presents summary, waits for approval

Phase 4: github-issue-deliver (haiku)
  -> orchestrator shows PR link
```

### ghpr (`/ghpr #42`)

```
Phase 1: github-issue-reader (haiku)
  -> orchestrator presents PR + review comments

Phase 2: github-issue-planner (opus)
  -> orchestrator presents plan, AskUserQuestion: approve/adjust

Phase 3: github-issue-implementer (sonnet)
  -> if existing test conflict: orchestrator asks user via AskUserQuestion
  -> orchestrator presents summary, waits for approval

Phase 4: github-issue-deliver (haiku)
  -> orchestrator confirms comment replies posted
```

## Token Budget

| Agent | Model  | Why                                               |
|-------|--------|---------------------------------------------------|
| 1     | Haiku  | Mechanical: git + gh CLI calls, no reasoning      |
| 2     | Opus   | Strategic: needs to understand codebase + design   |
| 3     | Sonnet | Productive: writes code well, cheaper than Opus    |
| 4     | Haiku  | Mechanical: git + gh CLI calls, no reasoning      |
