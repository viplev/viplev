---
name: ghissue
description: >
  Work on a GitHub issue end-to-end: read the issue, plan implementation,
  develop, test, commit, push and create a PR. Use this skill whenever the
  user wants to implement a GitHub issue. Triggers on phrases like "work on
  issue #X", "implement issue", "løs issue", "tag issue #X".
argument-hint: "[issue-number]"
---

# GitHub Issue Workflow

Orchestrate the full workflow for implementing GitHub issue #$ARGUMENTS.
You are the orchestrator — delegate all work to subagents and handle user interaction between phases.

---

## Phase 1: Read & Branch

Use agent `github-issue-reader` with prompt:
> Fetch GitHub issue #$ARGUMENTS and ensure the correct branch is checked out.

Present the agent's result to the user:
- Issue title and labels
- Which branch was checked out (new or existing)
- The full issue body (as returned by the agent)

Then ask: *"Any additional context I should know about this issue? (press Enter to continue)"*

---

## Phase 2: Plan

Use agent `github-issue-planner` with prompt containing:
- The full issue content from Phase 1
- Any extra context the user provided

Present the plan to the user. Then ask for approval using `AskUserQuestion` with options:
- "Yes, approve the plan"
- "No, adjust the plan"

If the user says no: ask what should change, pass their feedback to the planner agent, and present the revised plan.

---

## Phase 3: Implement

Use agent `github-issue-implementer` with prompt containing:
- The approved plan from Phase 2

**If the agent returns a request to modify an existing test:**
Ask the user for approval using `AskUserQuestion` with options:
- "Yes, allow the test change"
- "No, find another solution"

If approved, send the agent back with permission. If denied, send the agent back with instruction to find an alternative.

**If the agent returns a successful build:**
Present a summary to the user:
- Files created/modified
- Tests added
- Build result

**Wait for explicit user approval before continuing.**

---

## Phase 4: Deliver

Determine the commit type:
- If the plan has `commit_type: unknown`, ask the user which type to use.

Use agent `github-issue-deliver` with prompt containing:
- `issue_number`: $ARGUMENTS
- `branch`: from Phase 1
- `commit_type`: from plan or user input
- `scope`: from plan
- `summary`: short description from Phase 3
- `files_changed`: from Phase 3
- `mode`: issue

Present the PR link to the user when done.
