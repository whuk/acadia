Create a commit following TDD commit discipline.

Before committing, verify:
1. ALL tests are passing
2. ALL compiler/linter warnings are resolved
3. The change is a single logical unit

Determine the commit type:
- STRUCTURAL: Code reorganization without behavior change (refactoring, renaming, moving)
- BEHAVIORAL: New functionality or bug fixes

Format commit message as:
[STRUCTURAL] or [BEHAVIORAL]: Brief description

Examples:
- [STRUCTURAL] Extract calculateTotal method from Order class
- [BEHAVIORAL] Add discount calculation for premium customers

If tests are failing or warnings exist, report them and do NOT commit.

$ARGUMENTS - Optional: specific commit message to use