Perform a STRUCTURAL refactoring on the codebase (Tidy First approach).

1. First, run all tests to ensure they pass
2. Identify ONE structural improvement:
   - Extract method/function
   - Rename for clarity
   - Move code to appropriate location
   - Remove duplication
   - Improve code organization
3. Apply ONLY that single structural change
4. Run all tests again to confirm behavior is unchanged
5. Report the structural change made

Rules:
- NO behavioral changes (no new features, no bug fixes)
- ONE structural change at a time
- Tests must pass before AND after
- Keep the change small and focused

$ARGUMENTS - Optional: specific file or area to tidy