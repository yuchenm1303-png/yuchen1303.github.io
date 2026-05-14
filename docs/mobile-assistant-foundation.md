# Mobile Assistant Foundation

This branch adds the first foundation layer for turning the app into a mobile AI assistant while keeping the current UI and animation style unchanged.

## What changed

The existing `ai-ledger/chat-actions.js` now intercepts a small set of phone-control style commands before the normal ledger assistant handles the message.

Currently supported command drafts:

- Set alarm style command
  - Example: `明天早上8点叫我起床`
  - Example: `今晚9点提醒我复习`
- Open app style command
  - Example: `打开微信`
  - Example: `打开支付宝`

When one of these commands is detected, the chat page shows a confirmation card. This follows the same safety idea as ledger draft confirmation: the app should not execute real phone actions without the user confirming first.

## Current behavior

On the web page or GitHub Pages preview, the confirmation card can be created and cancelled.

If you tap Confirm on the web page, it will show that the Android native plugin is not available yet. This is expected.

Real phone control requires the next step: adding a Capacitor Android plugin inside `ai-ledger-android` and exposing methods such as:

```js
MobileAssistant.setAlarm({ hour, minute, label, date })
MobileAssistant.openApp({ appName })
```

## Testing checklist

Open the AI chat tab and try these messages:

```text
明天早上8点叫我起床
今晚9点提醒我复习
打开微信
今天午饭28
我这个月餐饮花了多少
```

Expected result:

- The alarm/open-app commands should become mobile command cards.
- Normal ledger commands should still go through the original ledger flow.
- The visual style should remain consistent with the existing glass-card UI.

## Next step

The next implementation step is to generate or open the Capacitor Android project, then add a native plugin that can call Android intents such as `AlarmClock.ACTION_SET_ALARM` and app launch intents.
