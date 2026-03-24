---
name: ghpr
description: >
  Review a GitHub PR, address review comments, fix code, push and reply to
  each comment. Use this skill whenever the user wants to address review
  feedback on a PR. Triggers on phrases like "address PR #X",
  "fix review on PR", "tag hånd om PR #X".
argument-hint: "[pr-number]"
---

# GitHub PR Review Workflow

Orchestrate the full workflow for addressing review comments on PR #$ARGUMENTS.
You are the orchestrator — delegate all work to subagents and handle user interaction between phases.

---

## Phase 1: Read PR

Use agent `github-issue-reader` with prompt:
> Fetch PR #$ARGUMENTS using `gh pr view $ARGUMENTS --json number,title,body,labels,headRefName,comments,reviews` and checkout the PR branch.
> Also fetch all review comments using `gh api repos/{owner}/{repo}/pulls/$ARGUMENTS/comments`.

Present to the user:
- PR title and branch
- All review comments grouped by file

---

## Phase 2: Plan

Use agent `github-issue-planner` with prompt containing:
- The PR details and all review comments from Phase 1

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

**If the agent returns a successful build:**
Present a summary to the user:
- What was changed per review comment
- Files modified
- Build result

**Wait for explicit user approval before continuing.**

---

## Phase 4: Deliver

Use agent `github-issue-deliver` with prompt containing:
- `issue_number`: $ARGUMENTS
- `branch`: from Phase 1
- `commit_type`: fix
- `scope`: review
- `summary`: address PR review comments
- `files_changed`: from Phase 3
- `mode`: pr

Present confirmation that comments have been replied to.
