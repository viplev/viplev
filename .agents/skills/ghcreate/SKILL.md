---
name: ghcreate
description: 'Create a well-structured GitHub issue from a short description or conversation.'
argument-hint: "[short description of what you want]"
---

# Create GitHub Issue Workflow

Create a GitHub issue based on the user's input: `$ARGUMENTS`

## Phase 1: Understand & Research

1. Analyze the user's description to understand the intent.
2. If needed, explore the codebase to understand what already exists and what would need to change.
3. Use `AskUserQuestion` to clarify anything ambiguous before writing the issue.

## Phase 2: Draft

1. Draft the issue with:
   - **Title**: concise, action-oriented (e.g. "Implement POST endpoint for services")
   - **Body**: a clear description of what needs to be done, broken into concrete steps where possible. Reference relevant files, endpoints, or entities if applicable.
   - **Labels**: suggest appropriate labels if any exist on the repo.
2. Present the draft to the user. **Wait for explicit approval or edits before continuing.**

## Phase 3: Create

Once the user approves:

1. Create the issue using `gh issue create` with the approved title and body.
2. Return the issue URL to the user.
