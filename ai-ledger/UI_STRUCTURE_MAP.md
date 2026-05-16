# AI Ledger UI Structure Map

This map documents the current stable UI structure without changing runtime behavior.

## Principle

`index.html` should stay as the static skeleton. It should not become the place for emergency CSS, feature logic, or visual experiments.

When a feature grows beyond simple structure, move it to its own CSS/JS file after a tested APK step.

## Current `index.html` structure

### Head resources

Current role:

- Loads base CSS, liquid glass CSS, chat CSS, auth CSS, background CSS.
- Loads runtime scripts in a fixed order.

Cleanup target:

- Keep only loading order and static metadata here.
- Avoid temporary inline scripts/styles except for boot-critical cases.

### Inline quick AI CSS

Current role:

- Styles quick AI entry mode launched from Android quick entry / notification flow.
- Hides normal nav and non-AI views in quick mode.

Target home:

- Future file: `quick-ai.css`.

Reason to keep for now:

- It is stable and boot-critical for quick AI entry.
- Moving it should be done in one small commit with APK verification.

### Inline quick AI script

Current role:

- Detects `mode=quick_ai`, `quick=ai`, `#ai-chat`, or `#quick-ai`.
- Adds the document entry marker early enough before layout starts.

Target home:

- Keep the tiny early detector inline if needed.
- Move non-critical quick entry runtime logic to future `quick-ai-entry.js`.

Reason to keep for now:

- This early marker affects first paint and avoids layout flash.

### `#view-ai`

Current role:

- AI chat page shell.
- Chat header, chat shell, model picker placeholder, messages, quick tags, composer, hint.

Cleanup target:

- Keep static chat containers only.
- Avoid putting model picker implementation here.
- Avoid duplicate legacy stat chips unless they are actually used.

Related owners:

- Chat behavior: `app-v3.js`.
- Model picker: currently `chat-source-badges.js` + legacy code in `navigation-execution-compat.js`.
- Chat layout: `chat-v2.css`.

### `#view-tools`

Current role:

- Feature center home grid and tool detail panel.

Related owners:

- Tool detail rendering: `tools-center.js`.
- Tool layout should eventually live in a dedicated tools CSS file.

Risk:

- Global layout overrides can deform tool detail screens. Avoid changing tools layout from background or navigation files.

### `#view-stats` and `#view-list`

Current role:

- Stats dashboard and ledger list.

Related owners:

- Data/rendering: `app-v3.js`.
- Chart library: Chart.js.

Cleanup target:

- Keep view structure stable.
- Remove old duplicated statistic cards only after confirming what the stats view still needs.

### `#view-settings`

Current role:

- Settings page structure and settings sections.

Related owners:

- Group navigation: `settings-groups.js`.
- Appearance detail page: `settings-appearance-plus.js`.
- Detail overlay polish: `settings-detail-polish.js`.
- Preferences: `settings-preferences.js`.

Risk:

- `settings-groups.js` depends on discoverable settings sections. Do not wrap or hide detail sections without checking how the script queries them.

Cleanup target:

- Add clear `data-settings-group-target` mapping if the group system requires it.
- Keep account/auth fields stable because `auth.js` depends on their ids.

### Auth overlay

Current role:

- Static login/register dialog.

Related owners:

- Behavior: `auth.js`.
- Styles: `auth.css`.

Cleanup target:

- Avoid moving ids unless `auth.js` is updated at the same time.

### Bottom navigation

Current role:

- Main app navigation: AI, tools, settings.

Related owners:

- View switching: `app-v3.js`.
- Motion/indicator polish: `ios-glass-motion.js`, `navigation-polish.js`.

Risk:

- Several polish files can affect nav size and transform. Future changes should test keyboard behavior and active indicator alignment.

## Script responsibility map

### Boot/runtime scripts

- `config.js`: static runtime config.
- `app-v3.js`: core app runtime.
- `auth.js`: Supabase auth UI behavior.
- `sync.js`: cloud sync.

### Chat capability scripts

- `chat-actions.js`: chat action helpers.
- `assistant-profile.js`: assistant persona/profile helpers.
- `chat-attachments.js`: attachment handling.
- `chat-source-badges.js`: source badges and current model picker.
- `ai-command-router-v2.js`: command routing.
- `cloud-command-bridge.js`: cloud command bridge.

### Navigation/mobile scripts

- `navigation-preferences.js`: navigation preferences.
- `navigation-execution-compat.js`: target owner is Android navigation compatibility only.
- `navigation-polish.js`: navigation UI polish only.

### Settings scripts

- `settings-groups.js`: grouped settings navigation.
- `settings-appearance-plus.js`: display/language detail controls.
- `settings-preferences.js`: preference persistence.
- `settings-detail-polish.js`: visual overlay polish.

### Visual/stability scripts

- `ui-motion.js`: base motion/reveal behavior.
- `background-picker.js`: background theme switching.
- `glass-stability.js`: Android WebView stability/performance.
- `ui-density-polish.js`: global density tuning.
- `ios-glass-motion.js`: iOS-like liquid motion.
- `layout-stability-polish.js`: layout stability fixes.

## Known cleanup candidates

### High priority

- Move model picker UI out of `navigation-execution-compat.js`.
- Move non-background layout rules out of `backgrounds.css`.
- Split injected styles inside `chat-source-badges.js` into static CSS after visual parity check.

### Medium priority

- Extract quick AI inline CSS to `quick-ai.css`.
- Extract quick AI runtime block to `quick-ai-entry.js`, keeping only first-paint detection inline if necessary.
- Mark old manual ledger sheet code before deletion.

### Low priority

- Split `app-v3.js` into smaller runtime modules after behavior tests are stable.
- Deduplicate theme-related glass values across CSS files.

## Verification after each structural cleanup

Check all of these before merging back:

- AI chat loads and scrolls.
- Model picker opens.
- Message source badges display.
- Bottom navigation switches views.
- Tools center opens detail panels.
- Settings groups open detail sections.
- Background picker changes theme.
- Auth dialog opens and closes manually.
- Keyboard does not badly deform AI chat.
