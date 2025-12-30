# Contributing - Code style and guidelines

This project prefers readable, maintainable code over clever one-liners. Follow these concise conventions:

- Variable names
  - Use short, descriptive names: `req`, `res`, `ctx`, `userData`.
  - Avoid extremely long names.

- Logic flow
  - Use guard clauses and early returns.
  - Minimize nesting.

- Comments
  - Only comment the "why" when it's not obvious from the code.
  - Use sentence-case fragments, no emojis.
  - Add small TODOs for non-critical scaling or edge cases.

- Small helpers
  - Keep helpers local when only used once.

- Formatting
  - Follow `.editorconfig` rules.
  - Max line length 120.

Examples

Kotlin (preferred style):

```kotlin
fun handleUser(req: Request): Result {
    if (!req.isValid()) return Result.error("invalid")

    val user = repo.find(req.id) ?: return Result.error("not found")

    // persist reason: keep transaction short
    repo.update(user.copy(active = true))

    return Result.ok(user)
}
```

Java (preferred style):

```java
public Response handle(Request req) {
    if (req == null || !req.isValid()) return Response.badRequest();

    User user = repo.find(req.getId());
    if (user == null) return Response.notFound();

    // TODO: add retry logic for repo.update if DB is flaky
    repo.update(user.withActive(true));
    return Response.ok(user);
}
```

Why these rules

- Keep code easy to scan and maintain.
- Favor small, local changes over wide refactors.

If you want stricter rules (lint checks, autoformat on commit), open an issue or PR and we can add them.

