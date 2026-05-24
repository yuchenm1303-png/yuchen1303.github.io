# Glass OpenGL Isolation Policy

This app intentionally keeps ordinary Compose glass separate from OpenGL glass.

## Current rule on this branch

Only `GlassRole.Shell` may create a card-bound OpenGL layer through `OpenGLGlassCardLayer`.

The following roles must stay fully isolated from OpenGL:

- `GlassRole.Card`
- `GlassRole.Chip`
- `GlassRole.Floating`
- `GlassRole.Nav`
- `GlassRole.Flex`

Fully isolated means all three conditions must hold:

1. Do not call `OpenGLGlassCardLayer`.
2. Do not register into any OpenGL registry.
3. Do not trigger OpenGL geometry sync or `requestGeometrySync()`.

## Why `Card` is not OpenGL here

The rollback baseline reused `GlassRole.Card` for many ordinary surfaces: message bubbles, text inputs, list rows, hint cards, budget cards and compact tool entries. Some of these are only 46dp to 88dp tall.

Because of that history, treating every `Card` as OpenGL would make the rendering path depend on naming accidents, not real architecture. `Card` is therefore a normal Compose/unified-backdrop role on this branch.

## How to add future OpenGL glass safely

Do not re-enable OpenGL for `GlassRole.Card` globally.

If a future screen needs a genuinely large OpenGL glass container, first audit the call site and promote only that outer container deliberately. Prefer using `GlassRole.Shell` for large page-level containers.

Small UI must remain Compose glass:

- buttons
- tabs
- chips
- sliders
- text inputs
- message bubbles
- list rows
- metric cards
- navigation bars
- floating buttons

Special glass components such as frosted panels, inset slots and backdrop-crop surfaces should stay Compose/Canvas based unless there is a deliberate architecture review.

## Files to check before changing this rule

- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/Glass.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/App.kt`
- any file that imports `OpenGLGlassCardLayer`
- any file that introduces an OpenGL registry, batched OpenGL layer, or geometry sync

The performance target is: large glass can be OpenGL, ordinary glass must never be pulled into OpenGL by default.
