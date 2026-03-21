---
name: ghpr
description: 'Review a GitHub PR, address Copilot review comments, fix code, push, and reply to comments.'
argument-hint: "[pr-number]"
---

# GitHub PR Review Workflow

Address review comments on GitHub PR #$ARGUMENTS following this strict multi-phase flow.

## Phase 1: Read & Understand

1. Run `git pull` to ensure we are up to date.
2. Use MCP tools to read PR #$ARGUMENTS: title, description, branch, and current status.
3. Check out the PR branch locally and pull the latest code.
4. Use MCP tools to read all review comments from Copilot on the PR.
5. Present a summary of the PR and all review comments to the user.

## Phase 2: Plan

1. Enter **plan mode** and create a plan for addressing each review comment:
   - What needs to change and where
   - Whether any tests need to be updated (flag this explicitly — tests should generally NOT need changes)
   - If anything is unclear or ambiguous, use `AskUserQuestion` to ask the user for clarification
2. Present the plan to the user. **Wait for explicit approval before continuing.**

## Phase 3: Implement

Once the user approves the plan:

1. Implement the fixes according to the approved plan.
2. If existing tests need changes, explicitly tell the user what changed and why.
3. Run `./gradlew build` to verify everything compiles and tests pass.

## Phase 4: Review & Deliver

When implementation is complete:

1. Present a summary to the user:
   - What was changed per review comment
   - Files modified
   - Tests added/modified (if any)
   - Build/test results
2. **Wait for explicit user approval (yes/no) before continuing.**

## Phase 5: Commit, Push & Reply

Once the user says yes:

1. Stage and commit with a semantic commit message (do NOT mention Claude/AI).
2. Push the branch: `git push`
3. Use MCP tools to reply to each Copilot review comment on GitHub, explaining what was done to address it.
4. Inform the user that all comments have been replied to, and they can now request a new review or merge.
