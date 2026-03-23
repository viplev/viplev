---
name: ghplan
description: >
  Creates a detailed implementation plan based on a GitHub issue or PR review comments,
  presents it to the user, and waits for approval. Use this skill whenever a plan needs
  to be made before implementing code changes — whether from an issue or from review feedback.
  Always use this as a shared step in ghissue and ghpr workflows.
argument-hint: "[issue-summary or pr-review-summary as text]"
---

# GitHub Plan Skill

Create and present a detailed implementation plan based on the provided context.

## Input

You will receive either:
- A GitHub issue summary (title + body + comments)
- A list of PR review comments to address

## Steps

1. Enter **plan mode** and create a detailed implementation plan:
   - What files need to be created or modified
   - What OpenAPI changes are needed (if any)
   - What tests need to be written or updated
   - Any existing tests that may break and need adjustment
   - For PR review comments: address each comment explicitly

2. Present the plan clearly to the user.

3. Check current context usage. If it appears high, offer the user three explicit choices:

   > **Ready to proceed?**
   > - `yes/clear` — Godkend og ryd kontekst (anbefalet)
   > - `yes` — Godkend og fortsæt i denne session
   > - `no` — Afvis planen og lav ændringer

4. **Wait for explicit user approval before doing anything else.**
   - If the user says `yes/clear`: confirm the plan is saved in their head (or a note), then tell them to start a new session and reference the plan.
   - If the user says `yes`: proceed directly.
   - If the user says `no`: revise the plan based on feedback and repeat from step 2.
