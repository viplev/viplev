---
name: ghissue
description: 'Read a GitHub issue, plan implementation, develop, test, commit, push and create PR.'
argument-hint: "[issue-number]"
---

# GitHub Issue Workflow

Work on GitHub issue #$ARGUMENTS following this strict multi-phase flow.
The repo is `viplev/viplev` on GitHub.

## Phase 1: Read & Plan

1. Run `git pull` to ensure we are up to date.
2. Use `gh issue view $ARGUMENTS` to fetch the issue title, body, and comments.
3. Present a brief summary of the issue, then use `AskUserQuestion` to ask:
   "Er der noget yderligere kontekst jeg skal vide om dette issue? (tryk Enter for at fortsætte)"
   - If the user provides context, factor it into the plan.
   - If blank, proceed as normal.
4. Enter **plan mode** and create a detailed implementation plan:
   - What files need to be created or modified
   - What OpenAPI changes are needed (if any)
   - What tests need to be written or updated
   - Any existing tests that may break and need adjustment
5. Present the plan to the user. **Wait for explicit approval before continuing.**

## Phase 2: Branch & Implement

Once the user approves the plan:

1. Get or create a branch linked to the issue:
   - Run `gh issue develop --list $ARGUMENTS` to check if a branch already exists
   - If a branch exists: `git fetch origin && git checkout <branch-name>`
   - If no branch exists: run `gh issue develop $ARGUMENTS` to create one, then checkout the new branch
2. Implement the solution according to the approved plan.
3. Write tests (unit and/or integration as appropriate).
4. If existing tests need changes, explicitly tell the user what changed and why.
5. Run `./gradlew build` to verify everything compiles and tests pass.

## Phase 3: Review & Deliver

When implementation is complete:

1. Present a summary to the user:
   - What was implemented
   - Files created/modified
   - Tests added/modified
   - Build/test results
2. **Wait for explicit user approval (yes/no) before continuing.**

## Phase 4: Commit, Push & PR

Once the user says yes:

1. Stage and commit with a semantic commit message (do NOT mention Claude/AI).
2. Push the branch: `git push -u origin <branch-name>`
3. Create a PR using `gh pr create`:
   - Title: short description ending with `(#<issue-number>)`
   - Body must start with `Closes #<issue-number>` on the first line
   - Include a summary of changes and test plan
