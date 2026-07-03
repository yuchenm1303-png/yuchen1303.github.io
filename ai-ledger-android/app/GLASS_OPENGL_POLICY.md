# Glass OpenGL Isolation Policy

## Role boundary

Only `GlassRole.Shell` may enter a card-bound OpenGL renderer.

These roles must remain fully outside OpenGL:

- `GlassRole.Card`
- `GlassRole.Chip`
- `GlassRole.Floating`
- `GlassRole.Nav`
- `GlassRole.Flex`

Isolation means no OpenGL layer call, no OpenGL registry entry, and no OpenGL geometry-sync request.
Frost panels, inset slots, buttons, tabs, sliders and compact information controls remain Compose/Canvas glass.

## Unified Shell batch

The reviewed Settings dashboard and Tools tab share the smart Shell batch architecture.
A page batch owns one TextureView, one EGL context, one render thread, one shader program, one blur-texture pyramid
and one dynamic VBO.

Each Shell still owns its independent:

- rectangle and corner radius;
- background sampling origin;
- intensity and visibility;
- short-edge optical scale;
- refraction, shoulder and dispersion field;
- press center, compression, rebound and edge-flow state.

Mixed card sizes must not use one global optical scale. The renderer supplies each item scale before drawing it,
so the stock Hero and compact cards retain the same optical distances as their standalone renderer route.

## Preserved-buffer damage rule

The Settings dashboard host moves with its eight cards. The Tools host is page-sized while cards move inside its
LazyColumn. Both layouts use the same final-frame packet:

- geometry is read again at PreDraw;
- old and new bounds are compared;
- one moving card updates one VBO range and one union damage area;
- several moving cards clear one combined damage area and redraw all visible Shells;
- root-only movement redraws sampling without clearing unchanged local rectangles;
- visibility changes clear the previously presented rectangle;
- texture or surface replacement performs a safe complete clear;
- devices without preserved-buffer support use the safe complete-clear route.

Never draw new scrolling rectangles into a preserved page surface without clearing their old bounds. That creates
repeated glass trails.

## Settings route

The eight Settings dashboard cards register in one local `OpenGlShellBatchHost`. The personal-space Shell remains
on its reviewed independent route. Account and cloud subscriptions stay below the dashboard boundary so unrelated
state updates do not recompose all eight cards. A single-card press must remain a single-item dirty update.

## Tools route

The stock Hero, ledger, statistics, plan, app-control, storage and operation-learning Shells form one visual
system and register in one page batch. They no longer create separate EGL contexts, render threads or copies of
the backdrop textures.

The batch keeps the standalone Compose frame and each card's original optical scale. Scrolling is solved by
old/new geometry damage tracking, not by reducing shader quality or demoting Shells to ordinary glass.
The packet capacity is larger than the home-card count so a Tools detail page cannot silently lose later Shells.

## Stable implementation boundaries

- `OpenGlShellBatch.kt`: Compose host and policy
- `OpenGlShellBatchSurface.kt`: registration, click and gesture state
- `OpenGlShellBatchContent.kt`: dynamic frame content
- `OpenGlShellBatchOptics.kt`: unchanged press optics
- `OpenGLShellBatchLayer.kt`: registry and Compose/Android bridge
- `SmartOpenGLGlassBatchHost.kt`: final geometry and dirty masks
- `SmartOpenGLGlassBatchSurface.kt`: TextureView and EGL lifecycle
- `SmartOpenGLGlassBatchRenderer.kt`: VBO, textures and damage rendering
- `BatchRenderPolicy.kt`: packet layout and capacity

Dynamic changes must not recreate registry, coordinate, pointer-input or click-state groups.

## Protected chat structure

Do not alter:

- `FixedHeightOverflowSlot`
- `modelPanelVisualHeight`
- `modelExpandDelta`
- `LocalOpenGLGlassSurfaceAnchor`
- `ChatPanelV2(viewportTopInset = modelExpandDelta)`
- `GlassPanel(... viewportTopInset = viewportTopInset)`

The performance target is exact reviewed Shell visuals with shared expensive resources. Ordinary glass must never
be pulled into OpenGL by default.
