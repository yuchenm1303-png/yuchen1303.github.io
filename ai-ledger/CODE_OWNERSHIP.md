# AI Ledger Code Ownership Inventory

This document records current file responsibilities and cleanup targets. It is intentionally documentation-only and should not change the app UI or behavior.

## Stable baseline

Current refactor branch starts from the stable UI baseline:

- Branch: `refactor-clean-ui-structure`
- Stable marker commit: `38d9a5d51c5c303b744af7c538a1b8a8af70cd51`
- UI restored from: `565c42c9bed6093621730d4bad031420320e690f`

## Ownership target

### `index.html`

Target responsibility:

- Static page structure.
- Script and stylesheet loading order.
- Persistent root containers only.

Should avoid:

- Temporary visual hacks.
- Duplicate old UI blocks.
- Hidden legacy panels that are no longer used.

Cleanup notes:

- Keep AI chat, tools, stats/list, settings, auth overlay, and bottom nav as clear top-level sections.
- Avoid rebuilding the same UI from multiple scripts.

### `app-v3.js`

Target responsibility:

- Core state and local storage.
- Ledger records.
- Chat message lifecycle.
- Rendering records, charts, and chat messages.
- Main nav switching.

Should avoid:

- Injecting large visual style blocks.
- Owning model picker UI.
- Owning Android navigation compatibility.

Cleanup notes:

- Good candidate for later splitting into `records`, `chat-runtime`, and `view-router`, but do not split until behavior tests are stable.

### `chat-source-badges.js`

Current responsibility:

- Chat source badges.
- Model picker list and model preference.
- Fetch patch for model preference.
- Some UI pinning / layout-adjacent behavior.

Target responsibility:

- Source badges.
- Model picker state and sheet.
- Model preference request injection.

Should avoid:

- Moving unrelated layout nodes.
- Styling the entire chat panel.
- Modifying unrelated header/buttons.

Cleanup notes:

- Keep source badges and model picker here for now.
- Later move pure model picker code to `model-picker.js` if it keeps growing.

### `navigation-execution-compat.js`

Current responsibility:

- Android navigation mode normalization.
- Patching Capacitor navigation plugin params.
- Some UI cleanup / visual injection from previous experiments.

Target responsibility:

- Navigation command compatibility only.
- No DOM layout creation.
- No injected CSS.
- No model picker or chat layout code.

Cleanup notes:

- This should be the first real cleanup target after documentation.
- Removing UI injection from this file should be behavior-neutral if the stable UI does not rely on it.

### `backgrounds.css`

Current responsibility:

- Background themes.
- Scene backdrop.
- Background picker previews.
- Some global layout and WebView stabilization rules.
- Some chat/layout emergency overrides from earlier fixes.

Target responsibility:

- Background theme variables.
- `.scene-backdrop` and background animations.
- Background picker previews.

Should avoid:

- AI chat layout.
- Settings layout.
- Bottom navigation layout.
- Keyboard behavior.

Cleanup notes:

- This is the second major cleanup target.
- Move chat-only rules to chat CSS.
- Move WebView stability rules to `glass-stability.js` or a dedicated stability CSS layer.

### `chat-v2.css`

Target responsibility:

- Chat shell layout.
- Chat message bubbles.
- Composer layout.
- Quick tags.
- Chat top control bar.

Cleanup notes:

- After `backgrounds.css` is cleaned, chat-only layout should live here.

### `liquid.css` / `liquid-plus.css`

Target responsibility:

- Glass material.
- Liquid highlights.
- Card/button material language.

Should avoid:

- Page-specific layout decisions.
- Runtime keyboard fixes.

Cleanup notes:

- Do not remove until we verify which classes are still used.

### `glass-stability.js`

Target responsibility:

- Android WebView stability guards.
- Performance mode decisions.
- Keyboard / resize stability behavior when necessary.

Should avoid:

- Rebuilding the UI.
- Styling specific product features.

Cleanup notes:

- Good place for runtime class toggles like `keyboard-open`, but not for one-off UI redesign.

### `settings-groups.js`

Target responsibility:

- Settings group list.
- Settings group detail/open/close behavior.
- Mapping group cards to existing detail sections.

Should avoid:

- Owning settings form logic.
- Owning appearance detail controls.

Cleanup notes:

- Requires stable `index.html` structure: detail sections should be discoverable by data attributes.

### `settings-appearance-plus.js`

Target responsibility:

- Display and language settings detail page.
- Font size, compact mode, glass offset, blur strength, animation settings.

Should avoid:

- Modifying unrelated settings groups.

### `settings-detail-polish.js`

Target responsibility:

- Detail overlay polish.
- Detail panel visual fixes.

Should avoid:

- Creating new settings content.

### `ui-density-polish.js`

Target responsibility:

- Global density tuning.
- Sizing and spacing polish.

Cleanup notes:

- Risky file because it can affect many pages.
- Treat future changes here as visual changes, not refactor-only changes.

### `ios-glass-motion.js`

Target responsibility:

- Bottom nav liquid indicator motion.
- Card press feedback.
- iOS-like motion polish.

Should avoid:

- Permanent layout overrides.
- Chat control bar ownership.

## Legacy / risky areas to mark later

Do not delete immediately. First mark, test, then remove.

- Old manual ledger sheet / floating add button.
- Old AI page statistic chips: today spend and month balance.
- Emergency rollback files or styles from previous visual overlays.
- UI injection inside `navigation-execution-compat.js`.
- Chat/layout rules inside `backgrounds.css`.
- Global density rules that override feature-specific layouts.

## Proposed next commits

1. `Mark navigation compat UI injection as legacy`
2. `Mark background CSS non-background sections as legacy`
3. `Document chat badge and model picker boundary`
4. `Remove unused rollback helper after APK verification`
5. `Move chat-only styles out of background CSS`

Each commit should be tested visually before continuing.
