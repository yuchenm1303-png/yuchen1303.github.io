# AI Ledger UI Refactor Plan

This branch is for safe cleanup only. The current stable UI baseline is `38d9a5d51c5c303b744af7c538a1b8a8af70cd51`, restored from `565c42c9bed6093621730d4bad031420320e690f`.

## Core rule

Do not change visible UI or app behavior during cleanup commits unless the commit explicitly says it is a visual change.

Every cleanup step should be small, easy to review, and reversible.

## Current goal

Make the project easier to modify by separating responsibilities:

- `index.html`: page structure only.
- `app-v3.js`: core app state, records, rendering, chat submission.
- `chat-source-badges.js`: chat source badges and model picker only.
- `navigation-execution-compat.js`: Android navigation parameter compatibility only.
- `backgrounds.css`: background themes only.
- `chat-v2.css`: chat page visual layout.
- `liquid.css` / `liquid-plus.css`: glass material and liquid visual effects.
- `glass-stability.js`: Android WebView stability and performance guards.
- `settings-groups.js`: settings group navigation.
- `settings-appearance-plus.js`: display and language settings detail page.
- `ios-glass-motion.js`: iOS-style motion and press feedback.

## Cleanup order

### Phase 0: Safety

- Keep `dev-update-1` untouched while cleaning.
- Work on `refactor-clean-ui-structure`.
- Keep each commit focused.
- Test APK after each phase.

### Phase 1: Inventory and labels

- Add comments marking file responsibility.
- Mark legacy code blocks with `legacy:` comments.
- Do not delete large blocks yet.

### Phase 2: Remove UI injection from utility scripts

- `navigation-execution-compat.js` should not create DOM nodes, styles, or layout.
- Utility scripts should not alter unrelated pages.

### Phase 3: CSS responsibility split

- Background theme CSS stays in `backgrounds.css`.
- Chat-only layout stays in chat CSS.
- Settings-only layout stays in settings CSS.
- Performance fallback styles stay in stability CSS.

### Phase 4: Legacy removal

- Remove unused old manual ledger sheet code only after a stable APK test.
- Remove unused old stat cards only after a stable APK test.
- Remove temporary rollback or emergency files after confirming they are not loaded.

## Verification checklist

Before merging back to `dev-update-1`, check:

- AI chat page looks the same as stable baseline.
- Model picker opens normally.
- Bottom navigation works.
- Feature center cards open normally.
- Settings groups open normally.
- Background picker works.
- Keyboard does not deform the chat page badly.
- Android APK builds successfully.

## Commit message style

Use clear, narrow commit messages:

- `Document UI refactor boundaries`
- `Mark legacy manual ledger sheet code`
- `Keep navigation compat free of UI injection`
- `Move chat-only styles out of background CSS`
- `Remove unused rollback helper after APK test`
