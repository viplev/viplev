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

This skill is intentionally thin — it handles only what is unique to the
issue workflow. Shared steps delegate to `ghplan`, `ghbuild`, and `ghcommit`.

---

## Phase 1: Read & Fetch

1. Run `git pull` to ensure we are up to date.
2. Run `gh issue view $ARGUMENTS` to fetch the issue title, body, and comments.
3. Present a brief summary of the issue to the user.
4. Ask: *"Er der noget yderligere kontekst jeg skal vide om dette issue? (tryk Enter for at fortsætte)"*
   - If the user provides context, factor it into the next phase.

## Phase 2: Plan

Hand off to **`ghplan`** with the issue summary as input.

- If the user chooses `yes/clear`: end this session here. Tell the user to start a new session and run `ghissue-implement $ARGUMENTS` (or describe the approved plan) to continue from Phase 3.
- If the user chooses `yes`: continue directly to Phase 3.

## Phase 3: Branch & Implement

1. Get or create a branch linked to the issue:
   - Run `gh issue develop --list $ARGUMENTS` to check if a branch already exists.
   - If a branch exists: `git fetch origin && git checkout <branch-name>`
   - If no branch exists: `gh issue develop $ARGUMENTS`, then checkout the new branch.

2. Implement the solution according to the approved plan.

3. Write tests (unit and integration as appropriate).

4. If existing tests need changes, explicitly tell the user what changed and why.

## Phase 4: Build

Hand off to **`ghbuild`**.

## Phase 5: Review & Approve

Present a summary to the user:
- What was implemented
- Files created/modified
- Tests added/modified
- Build/test results

**Wait for explicit user approval (yes/no) before continuing.**

If the user says no: address their feedback and re-run Phase 4.

## Phase 6: Commit & PR

Hand off to **`ghcommit`** with:
- Issue number: `$ARGUMENTS`
- Mode: `issue`
