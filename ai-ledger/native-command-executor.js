/*
 * Native command executor
 *
 * Runs after chat-actions.js. In normal browser mode it stays quiet. In the
 * Android native shell, it intercepts confirmed mobile command cards and sends
 * them to AiLedgerNativeBridge instead of the older Capacitor-only path.
 */
(() => {
  'use strict';

  const CHAT_KEY = 'ai-ledger-chat-v2';
  const HANDLED_ATTR = 'data-native-executor-handled';

  const APP_PACKAGES = {
    微信: 'com.tencent.mm',
    WeChat: 'com.tencent.mm',
    支付宝: 'com.eg.android.AlipayGphone',
    高德地图: 'com.autonavi.minimap',
    高德: 'com.autonavi.minimap',
    百度地图: 'com.baidu.BaiduMap',
    百度: 'com.baidu.BaiduMap',
    QQ: 'com.tencent.mobileqq',
    淘宝: 'com.taobao.taobao',
    京东: 'com.jingdong.app.mall',
    哔哩哔哩: 'tv.danmaku.bili',
    B站: 'tv.danmaku.bili',
    抖音: 'com.ss.android.ugc.aweme',
    小红书: 'com.xingin.xhs',
  };

  function bridge() {
    return window.AiLedgerNativeBridge?.isAvailable?.() ? window.AiLedgerNativeBridge : null;
  }

  function isNativeShell() {
    return Boolean(bridge()) || document.documentElement.classList.contains('native-shell');
  }

  function escapeCss(value) {
    if (window.CSS?.escape) return CSS.escape(String(value));
    return String(value).replace(/[^a-zA-Z0-9_-]/g, '\\$&');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function readChatMessages() {
    try {
      const parsed = JSON.parse(localStorage.getItem(CHAT_KEY) || '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  function findCommand(commandId) {
    const messages = readChatMessages();
    return [...messages].reverse().find((item) => item?.mobileCommand?.id === commandId)?.mobileCommand || null;
  }

  function getStatusText(state) {
    if (state === 'done') return '已执行';
    if (state === 'cancelled') return '已取消';
    if (state === 'failed') return '执行失败';
    return '待确认';
  }

  function updateCard(commandId, state, message) {
    const card = document.querySelector(`[data-mobile-card="${escapeCss(commandId)}"]`);
    if (!card) return;
    const status = card.querySelector('.mobile-command-status');
    if (status) {
      status.className = `mobile-command-status ${state}`;
      status.textContent = getStatusText(state);
    }
    card.querySelector('.mobile-command-actions')?.remove();
    card.querySelector('.mobile-command-message')?.remove();
    if (message) card.insertAdjacentHTML('beforeend', `<div class="mobile-command-message">${escapeHtml(message)}</div>`);
  }

  function isNavigationPreferenceCommand(command) {
    return command?.commandKind === 'navigation_preference'
      || command?.params?.intent === 'navigation_preference'
      || Boolean(command?.params?.updates);
  }

  function resolvePackageName(command) {
    const params = command?.params || {};
    const explicit = params.packageName || params.package || '';
    if (explicit) return explicit;
    const name = params.appName || command.summary || '';
    return APP_PACKAGES[name] || APP_PACKAGES[String(name).replace(/地图$/u, '')] || '';
  }

  async function executeViaNative(command) {
    const native = bridge();
    if (!native) return { ok: false, message: '原生壳还没有准备好。' };

    native.haptic?.('tick');

    if (isNavigationPreferenceCommand(command)) {
      const result = window.AssistantPreferences?.applyPreferenceUpdate?.(command.params?.updates || {});
      return result || { ok: true, message: '已保存导航偏好。' };
    }

    if (command.type === 'set_alarm') {
      const params = command.params || {};
      const ok = native.setAlarm?.({
        hour: Number(params.hour),
        minute: Number(params.minute || 0),
        message: params.label || params.message || 'AI 助手提醒',
        date: params.date || '',
      });
      return ok === false
        ? { ok: false, message: '原生闹钟接口暂时不可用。' }
        : { ok: true, message: '已交给系统闹钟处理。' };
    }

    if (command.type === 'open_app') {
      const packageName = resolvePackageName(command);
      const appName = command.params?.appName || command.summary || packageName;
      if (!packageName) return { ok: false, message: `暂时不知道“${appName}”的安卓包名。` };
      const ok = native.openApp?.(packageName, appName);
      return ok === false
        ? { ok: false, message: `没有找到${appName}。` }
        : { ok: true, message: `已请求打开${appName}。` };
    }

    if (command.type === 'navigate') {
      const destination = command.params?.destination || command.params?.destinationAlias || command.summary || '';
      if (!destination) return { ok: false, message: '缺少导航目的地。' };
      const ok = native.startNavigation?.({
        target: destination,
        destination,
        mapProvider: command.params?.mapProvider || '',
        mode: command.params?.mode || command.params?.travelMode || 'driving',
      });
      return ok === false
        ? { ok: false, message: '原生导航接口暂时不可用。' }
        : { ok: true, message: '已交给系统地图处理。' };
    }

    return { ok: false, message: '这个动作还没有原生执行器。' };
  }

  function installHandler() {
    document.addEventListener('click', async (event) => {
      if (!isNativeShell()) return;
      const runBtn = event.target.closest?.('[data-mobile-run]');
      const cancelBtn = event.target.closest?.('[data-mobile-cancel]');
      if (!runBtn && !cancelBtn) return;

      const button = runBtn || cancelBtn;
      if (button?.hasAttribute(HANDLED_ATTR)) return;
      button?.setAttribute(HANDLED_ATTR, '1');

      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();

      const commandId = runBtn?.dataset.mobileRun || cancelBtn?.dataset.mobileCancel;
      const command = findCommand(commandId);
      if (!command) return;

      if (cancelBtn) {
        updateCard(commandId, 'cancelled', '已取消执行。');
        return;
      }

      updateCard(commandId, 'pending', isNavigationPreferenceCommand(command) ? '正在保存导航偏好……' : '正在调用 Android 原生能力……');
      try {
        const result = await executeViaNative(command);
        updateCard(commandId, result?.ok ? 'done' : 'failed', result?.message || (result?.ok ? '已执行。' : '执行失败。'));
      } catch (error) {
        updateCard(commandId, 'failed', String(error?.message || error || '执行失败'));
      }
    }, true);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', installHandler, { once: true });
  else installHandler();
})();
