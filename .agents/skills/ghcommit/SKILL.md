---
name: ghcommit
description: >
  Stages, commits, and pushes code changes with a semantic commit message.
  Depending on the workflow context, either creates a new PR (for issues) or
  replies to each review comment on GitHub (for PRs). Use this as the final
  shared step in ghissue and ghpr workflows. Always use after build passes
  and user has approved the implementation.
argument-hint: "[issue-number or pr-number] [mode: issue|pr]"
---

# GitHub Commit Skill

Commit, push and deliver the implementation.

## Input

You need to know:
- The issue or PR number
- The mode: `issue` (create PR) or `pr` (reply to review comments)
- The branch name currently checked out

## Step 1: Determine commit type

Look at the GitHub labels on the linked issue:
- `bug` → `fix(<scope>): ...`
- `enhancement` or `feature` → `feat(<scope>): ...`
- `documentation` → `docs(<scope>): ...`
- Multiple labels: `bug` (fix) takes priority over `enhancement` (feat)
- **No matching label**: Ask the user which commit type to use. Do not silently default to `chore`.

## Step 2: Commit and push

```bash
git add -A
git commit -m "<type>(<scope>): <short description>"
git push
```

Do **not** mention Claude or AI in the commit message.

## Step 3: Deliver

### If mode is `issue` — create a PR

Use `gh pr create` with:
- **Title**: same semantic prefix as commit, ending with `(#<issue-number>)`
  Example: `feat(agent): implement store metrics endpoint (#12)`
- **Body**: must start with `Closes #<issue-number>` on the first line, followed by a summary of changes and test plan.

```bash
gh pr create \
  --title "<type>(<scope>): <description> (#<issue-number>)" \
  --body "Closes #<issue-number>\n\n## Changes\n...\n\n## Test plan\n..."
```

### If mode is `pr` — reply to each review comment

Use MCP tools to reply to each Copilot review comment on the PR, explaining specifically what was done to address it. Keep replies concise and factual.

## Step 4: Confirm

Inform the user:
- What was committed and pushed
- PR link (if created) or that all comments have been replied to
- Next suggested action (request new review, merge, etc.)
