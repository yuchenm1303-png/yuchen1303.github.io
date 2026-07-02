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

### Settings dashboard reviewed batch route

The eight settings dashboard tiles continue to enter through the shared `OpenGlShellGlass` component used by
the stock-market Hero. They are eight independent Shell glass items, not one large Shell clipped into eight holes.

The dashboard uses one parent OpenGL batch host only to share expensive resources:

- one `TextureView` and EGL context;
- one shader program;
- one clear texture and one low / medium / high blur pyramid;
- one VSync-coalesced render request for the dashboard.

Within each frame the parent host draws every registered Shell separately with the same modern stock-Hero
fragment shader. Every tile therefore keeps its own:

- rectangle and corner radius;
- background sampling origin;
- short-edge optical scaling;
- refraction and rounded-shoulder field;
- press center, press progress, compression and rebound state;
- edge-flow and press-light overlay.

The batch route must never be replaced with a single large optical rectangle plus a Compose clip mask. Clipping
changes visibility only and cannot create independent optical fields.

Only these reviewed settings dashboard Shells register with `OpenGLShellBatchState`. Ordinary settings controls,
`GlassRole.Card`, `GlassRole.Chip`, sliders, inset slots and frosted panels remain outside every OpenGL registry.

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
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/OpenGlShellGlass.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/OpenGlShellBatch.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/LegacyOpenGLGlassPreviewShell.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/CachedTabHost.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/BackdropCoordinates.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/gl/NewOpenGLGlassCardLayer.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLShellBatchLayer.kt`
- `ai-ledger-android/app/src/main/java/com/yuchen/ailedger/ui/gl/OpenGLGlassCardLayer.kt`
- any file that introduces an OpenGL registry, batched OpenGL layer, or geometry sync

The performance target is: large glass can be OpenGL, ordinary glass must never be pulled into OpenGL by default.
