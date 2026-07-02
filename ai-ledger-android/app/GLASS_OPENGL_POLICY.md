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

## Production Shell route

Assistant and Settings production Shells enter the legacy renderer through `LegacyOpenGLShellHost` by default.
The old `LegacyOpenGLGlassPreviewShell` name remains only as a compatibility entry for laboratory previews.

A production Shell has one coordinate owner. `GlassPanel` owns the `GlassCoordinateSource` placement, so
`LegacyOpenGLShellHost` is called with `manageCoordinatePlacement = false`. Standalone previews may keep
host-owned placement. Do not reintroduce adjacent duplicate `onPlaced` writers for the same coordinate source.

### Settings dashboard exception

The eight visible settings dashboard tiles are rendered through one deliberately promoted outer
`GlassRole.Shell`, not eight OpenGL cards. The Shell is clipped into eight rounded windows while text,
selection state and click handling remain ordinary Compose children.

This reviewed container may opt into the modern multi-level renderer with
`LocalForceNewOpenGlShellRenderer`. The opt-in must remain local to that outer Shell:

- exactly one EGL / `TextureView` host for the complete dashboard;
- no tile calls `OpenGLGlassCardLayer` directly;
- no tile registers OpenGL geometry;
- no tile triggers geometry synchronization;
- ordinary settings controls remain non-OpenGL.

The stable chat structure must remain intact:

- `FixedHeightOverflowSlot`
- `modelPanelVisualHeight`
- `modelExpandDelta`
- `LocalOpenGLGlassSurfaceAnchor`
- `ChatPanelV2(viewportTopInset = modelExpandDelta)`
- `GlassPanel(... viewportTopInset = viewportTopInset)`

## Why `Card` is not OpenGL here

The rollback baseline reused `GlassRole.Card` for many ordinary surfaces: message bubbles, text inputs, list rows, hint cards, budget cards and compact tool entries. Some of these are only 46dp to 88dp tall.

Because of that history, treating every `Card` as OpenGL would make the rendering path depend on naming accidents, not real architecture. `Card` is therefore a normal Compose/unified-backdrop role on this branch.

## Legacy registry status

The old full-screen `GlassItemRegistry` is not propagated into cached pages. Its compatibility storage is
allocated lazily only if an old caller actually inserts a node. `UnifiedGlassBackdropLayer` is an inline,
non-Compose compatibility function and must not recreate a Canvas or a Composition Group.

Production ordinary glass uses `OrdinaryGlassItemRegistry`; it is not an OpenGL registry.

## Geometry cache rule

Do not disable or expand `LegacyOpenGLGlassGeometryCache` only from source-code intuition. Its direct-render
fallback is visually equivalent, but the performance break-even depends on GPU, viewport area and frame pattern.
Any default-policy change requires device A/B evidence covering GPU frame time, memory, texture upload count and
jank. The cache must never be used as a reason to alter Shell bounds, anchor or viewport inset behavior.

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
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/LegacyOpenGLGlassPreviewShell.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/CachedTabHost.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/BackdropCoordinates.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/gl/NewOpenGLGlassCardLayer.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLGlassCardLayer.kt`
- any file that introduces an OpenGL registry, batched OpenGL layer, or geometry sync

The performance target is: large glass can be OpenGL, ordinary glass must never be pulled into OpenGL by default.
