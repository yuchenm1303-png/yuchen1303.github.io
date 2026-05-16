# Settings and Motion Ownership Boundaries

This document records the intended boundaries for settings, motion, and WebView stability scripts.

It is documentation-only and should not change the app UI or behavior.

## Why this exists

The app has several polish scripts that run late and can affect multiple pages. This is powerful, but risky:

- One global density rule can deform the tools page.
- One settings overlay fix can block unrelated settings cards.
- One navigation motion change can break keyboard behavior.
- One WebView stability patch can accidentally remove intended glass effects.

The cleanup goal is to keep each script focused and predictable.

## Settings system

### `settings-groups.js`

Target responsibility:

- Render or organize settings group entries.
- Open/close settings group detail views.
- Map group cards to existing settings sections.

Should own:

- Group labels such as account/sync, display/language, phone preferences, appearance, data/budget.
- Group card click handling.
- Detail layer navigation state.

Should avoid:

- Implementing the actual display/language controls.
- Changing AI chat layout.
- Changing tools page layout.
- Creating unrelated background picker behavior.

Risk notes:

- It likely depends on selectors/data attributes in `index.html`.
- Do not wrap settings detail sections in new containers unless `settings-groups.js` queries are updated at the same time.
- Do not hide original settings sections until the detail navigation has been verified.

Verification checklist:

- Account and sync group opens.
- Display and language group opens.
- Phone preferences group opens.
- Background appearance group opens.
- Data and budget group opens.
- Returning/back action works.

### `settings-appearance-plus.js`

Target responsibility:

- Display and language detail page content.
- Language option.
- Font size option.
- Glass opacity offset.
- Glass blur strength.
- Motion effects option.
- Compact mode option.

Should own:

- Form controls and saved values for these appearance/detail preferences.
- Applying appearance settings through CSS variables or body/html classes.

Should avoid:

- Owning the entire settings group navigation system.
- Modifying background picker options.
- Modifying tools or AI chat layout directly.

Risk notes:

- It may write classes/CSS variables that affect the whole app.
- Treat any visible change here as a visual change, not a refactor-only change.

Verification checklist:

- Language row is visible.
- Font size changes do not break layout.
- Glass opacity/blur settings do not cause WebView flicker.
- Animation toggle does not disable necessary state transitions.
- Compact mode does not deform tools/settings cards.

### `settings-preferences.js`

Target responsibility:

- Persist user preferences.
- Restore preferences at startup.
- Provide simple preference helpers for other settings scripts.

Should avoid:

- Rendering large settings UI.
- Owning feature-specific page layout.

Risk notes:

- Preference keys should be stable.
- Renaming preference keys requires migration.

### `settings-detail-polish.js`

Target responsibility:

- Visual polish for settings detail overlays.
- Z-index, backdrop, and text hierarchy fixes inside settings details.

Should own:

- Settings detail overlay style fixes.
- Detail card readability improvements.

Should avoid:

- Creating settings content.
- Hijacking clicks for non-settings pages.
- Applying top-level modal styles to chat/tools unless explicitly scoped.

Risk notes:

- Late-loaded overlay styles can accidentally sit above normal views.
- Keep selectors scoped to settings detail containers.

## Motion and density system

### `ui-motion.js`

Target responsibility:

- Base reveal animations.
- General press feedback.
- Small generic motion helpers.

Should avoid:

- Permanent page layout overrides.
- Feature-specific animations that belong to chat/tools/settings.

Risk notes:

- Animation should prefer `transform` and `opacity`.
- Avoid animating height, width, blur, heavy shadows, and layout-affecting properties.

### `ios-glass-motion.js`

Target responsibility:

- iOS-like liquid interaction polish.
- Bottom navigation liquid indicator.
- Card press feedback.
- Feature card expand/collapse motion.

Should own:

- Motion timing.
- Transform/opacity-based interaction states.
- Liquid nav indicator movement.

Should avoid:

- Changing nav structure.
- Changing chat panel height.
- Changing settings group content.
- Injecting broad layout CSS.

Risk notes:

- Bottom nav animation can conflict with keyboard handling.
- Feature card expansion can conflict with tools detail rendering.
- Keep motion logic separate from DOM ownership.

Verification checklist:

- Bottom nav indicator aligns with active tab.
- Press feedback does not resize cards.
- Feature detail open animation does not distort card shapes.
- Keyboard open state does not leave nav in the wrong position.

### `navigation-polish.js`

Target responsibility:

- Bottom navigation visual polish.
- Navigation active/pressed states.

Should avoid:

- Android native navigation command handling.
- Chat model picker UI.
- Settings group navigation.

Risk notes:

- Keep separate from `navigation-execution-compat.js`.
- UI navigation polish and native navigation execution are different responsibilities.

### `layout-stability-polish.js`

Target responsibility:

- Small layout stability fixes.
- Viewport height and resize helpers if needed.

Should avoid:

- Large visual redesign.
- Creating or removing feature content.
- Long-term feature-specific layout that belongs in CSS.

Risk notes:

- This file can easily become a dumping ground for emergency fixes.
- Any new rule should include a comment explaining which bug it fixes and when it can be removed.

### `ui-density-polish.js`

Target responsibility:

- Global size and spacing tuning.
- Overall compact/youthful UI density.

Should own:

- Broad spacing tokens.
- Reusable density rules that are intentionally global.

Should avoid:

- One-off fixes for a single broken card.
- Feature-specific layout corrections.
- Overriding settings/tools/chat internals without scope.

Risk notes:

- This is one of the riskiest visual files because it affects everything.
- Treat future edits here as visual changes requiring full-page testing.

## WebView stability system

### `glass-stability.js`

Target responsibility:

- Android WebView stability guards.
- Reducing flicker/black blocks/card disappearance.
- Applying performance-safe classes when needed.
- Keyboard and viewport stability only when necessary.

Should own:

- WebView capability detection.
- Performance fallback classes.
- Safe glass reduction on Android if needed.

Should avoid:

- Designing the UI.
- Creating chat/settings/tools content.
- Moving buttons/cards between containers.

Risk notes:

- This file should modify classes/state, not rebuild layout.
- Keep fallback behavior conservative.
- Do not remove intended glass effects globally unless performance mode is active.

## Refactor order for these files

### Step A: Comments and boundaries

- Add top-level ownership comments to each file only if safe.
- Prefer documentation when a file is large or risky.

### Step B: Scope checks

- Check selectors in each file.
- Mark broad selectors that are likely to affect multiple views.
- Do not change them yet.

### Step C: Move obvious misplaced code

Only after APK verification:

- Move model picker UI out of navigation compatibility.
- Move chat-only styles out of background CSS.
- Move quick AI inline styles into `quick-ai.css`.
- Move quick AI runtime block into `quick-ai-entry.js` while keeping early entry detection inline.

### Step D: Delete legacy code

Only after a stable build:

- Remove old visual-design rollback selectors.
- Remove unused manual ledger sheet code.
- Remove unused old stat chip code.
- Remove temporary marker files after no longer needed.

## Global verification checklist

After any settings/motion/stability cleanup, verify:

- AI page still matches the stable capsule baseline.
- Model picker opens and selection persists.
- Tools page cards open and close without deformation.
- Settings group cards open and close.
- Background picker changes theme.
- Bottom nav indicator aligns and animates smoothly.
- Keyboard does not badly distort chat layout.
- Android APK builds successfully.
