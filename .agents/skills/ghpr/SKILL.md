---
name: ghpr
description: >
  Review a GitHub PR, address Copilot review comments, fix code, push and
  reply to each comment. Use this skill whenever the user wants to address
  review feedback on a PR. Triggers on phrases like "address PR #X",
  "ret review kommentarer", "fix review on PR", "tag hånd om PR #X".
argument-hint: "[pr-number]"
---

# GitHub PR Review Workflow

Orchestrate the full workflow for addressing review comments on PR #$ARGUMENTS.

This skill is intentionally thin — it handles only what is unique to the
PR workflow. Shared steps delegate to `ghplan`, `ghbuild`, and `ghcommit`.

---

## Phase 1: Read & Fetch

1. Run `git pull` to ensure we are up to date.
2. Use MCP tools to read PR #$ARGUMENTS: title, description, branch, and current status.
3. Check out the PR branch locally and pull the latest code.
4. Use MCP tools to read all review comments (from Copilot or others) on the PR.
5. Present a clear summary of the PR and all review comments to the user.

## Phase 2: Plan

Hand off to **`ghplan`** with the list of review comments as input.

- If anything is unclear or ambiguous, ask the user for clarification before handing off.
- If the user chooses `yes/clear`: end this session here. Tell the user to start a new session and run `ghpr-implement $ARGUMENTS` (or describe the approved plan) to continue from Phase 3.
- If the user chooses `yes`: continue directly to Phase 3.

## Phase 3: Implement

1. Implement the fixes according to the approved plan.
2. If existing tests need changes, explicitly tell the user what changed and why.
   (Tests should generally **not** need changes — flag this clearly if they do.)

## Phase 4: Build

Hand off to **`ghbuild`**.

## Phase 5: Review & Approve

Present a summary to the user:
- What was changed per review comment
- Files modified
- Tests added/modified (if any)
- Build/test results

**Wait for explicit user approval (yes/no) before continuing.**

If the user says no: address their feedback and re-run Phase 4.

## Phase 6: Commit & Reply

Hand off to **`ghcommit`** with:
- PR number: `$ARGUMENTS`
- Mode: `pr`
