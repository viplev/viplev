---
name: ghbuild
description: >
  Runs ./gradlew build to verify that the codebase compiles and all tests pass.
  Interprets the output and presents a clear summary of results. Use this skill
  after implementation is complete, before committing. Always use this as a
  shared step in ghissue and ghpr workflows.
---

# GitHub Build Skill

Verify the implementation by running the project build and tests.

## Steps

1. Run `./gradlew build` and capture the full output.

2. Interpret the result:
   - **Success**: All tests pass, no compilation errors.
   - **Test failure**: List which tests failed and why (short summary).
   - **Compilation error**: Show the relevant error lines.

3. Present a concise summary to the user:
   - Build status (passed / failed)
   - Number of tests run / passed / failed
   - Any relevant warnings worth noting

4. If the build **failed**:
   - Diagnose the root cause
   - Fix the issue
   - Re-run `./gradlew build`
   - Repeat until the build passes

5. Once the build passes, inform the calling workflow to continue.
