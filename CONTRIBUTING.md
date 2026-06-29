# Contributing

This is a portfolio project open to collaboration and extension.

## Adding a New Test Suite

1. Write a plain-English user story in `src/main/resources/stories/` (`.txt`)
2. Generate test cases: set `ANTHROPIC_API_KEY`, point a runner at the `.txt` file, run once
3. Commit the generated `.json` to `src/main/resources/stories/generated/`
4. Create a 3-line runner subclass (see [README — Adding a New Test Runner](README.md#adding-a-new-test-runner))
5. Add a `<test>` block to `testng.xml` and the Docker suite files
6. Tag important test cases with `"smoke": true` in the JSON

## Extending the Allow-List

To add a new UI action or API assertion type:
1. Add the value to `ALLOWED_ACTIONS` or `ALLOWED_ASSERTION_TYPES` in `TestCaseValidator.java`
2. Add a handler branch in `PlaywrightExecutor.java` or `RestAssuredExecutor.java`
3. Update the system prompt in `PromptTemplates.java` so Claude knows the new action exists

## Running Tests Locally

```bash
# Smoke gate only (~5 min)
mvn test -Dsmoke.only=true -Dtestng.suite.file=src/test/resources/testng-smoke.xml

# Full suite
mvn test

# With a specific browser
mvn test -Dbrowser=firefox
```

## Secrets

Never commit real credentials. Use `.env` (gitignored) locally or CI secrets for:
- `ANTHROPIC_API_KEY` — only needed for API mode (live generation)
- `LOGIN_PASS` — test account password for saucedemo.com

## Code Style

- Follow existing package structure
- Sensitive key detection lives only in `SensitiveDataMasker` — do not duplicate masking logic elsewhere
- Page selectors live only in `pages/` — executors use `PageRegistry` for resolution