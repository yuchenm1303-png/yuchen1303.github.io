/*
 * AI Ledger Native Bridge
 *
 * This file is the web-side contract for the Android native shell.
 * It keeps the current ai-ledger web app usable in a normal browser, while
 * allowing a Kotlin/Compose host to take over system abilities, haptics,
 * native glass surfaces and high-performance navigation chrome.
 */
(() => {
  'use strict';

  const VERSION = '2026.05.17-native-shell-phase-1';
  const BRIDGE_NAMES = [
    'AiLedgerNative',
    'AiLedgerAndroid',
    'AndroidQuickAi',
    'QuickAiBridge',
  ];

  const state = {
    requestId: 0,
    callbacks: new Map(),
    lastCapabilities: null,
  };

  function getBridge() {
    for (const name of BRIDGE_NAMES) {
      const bridge = window[name];
      if (bridge) return bridge;
    }
    return null;
  }

  function safeJsonParse(value, fallback = null) {
    if (!value || typeof value !== 'string') return fallback;
    try {
      return JSON.parse(value);
    } catch {
      return fallback;
    }
  }

  function safeJsonStringify(value) {
    try {
      return JSON.stringify(value || {});
    } catch {
      return '{}';
    }
  }

  function readCapabilities() {
    const bridge = getBridge();
    if (!bridge) return null;

    try {
      if (typeof bridge.getCapabilities === 'function') {
        const raw = bridge.getCapabilities();
        state.lastCapabilities = typeof raw === 'string' ? safeJsonParse(raw, {}) : raw;
        return state.lastCapabilities || {};
      }
    } catch (error) {
      console.warn('[native-bridge] getCapabilities failed:', error);
    }

    state.lastCapabilities = {
      haptic: typeof bridge.haptic === 'function',
      postMessage: typeof bridge.postMessage === 'function',
      openApp: typeof bridge.openApp === 'function',
      setAlarm: typeof bridge.setAlarm === 'function',
      nativeGlass: true,
    };
    return state.lastCapabilities;
  }

  function dispatchNativeEvent(type, payload = {}) {
    window.dispatchEvent(new CustomEvent('ai-ledger-native-event', {
      detail: { type, payload, at: Date.now() },
    }));
  }

  function settleRequest(id, ok, payload) {
    const callback = state.callbacks.get(String(id));
    if (!callback) return false;
    state.callbacks.delete(String(id));
    if (ok) callback.resolve(payload);
    else callback.reject(payload);
    return true;
  }

  function postToBridge(type, payload = {}, options = {}) {
    const bridge = getBridge();
    if (!bridge) return options.expectReply ? Promise.reject(new Error('Native bridge unavailable')) : false;

    const expectReply = Boolean(options.expectReply);
    const id = String(++state.requestId);
    const message = {
      id,
      type,
      payload,
      version: VERSION,
      href: window.location.href,
      at: Date.now(),
    };

    let promise = null;
    if (expectReply) {
      promise = new Promise((resolve, reject) => {
        state.callbacks.set(id, { resolve, reject });
        window.setTimeout(() => {
          if (!state.callbacks.has(id)) return;
          state.callbacks.delete(id);
          reject(new Error(`Native bridge timeout: ${type}`));
        }, options.timeout || 6000);
      });
    }

    try {
      if (typeof bridge.postMessage === 'function') {
        bridge.postMessage(safeJsonStringify(message));
      } else if (typeof bridge.call === 'function') {
        bridge.call(type, safeJsonStringify(payload));
      } else {
        const direct = bridge[type];
        if (typeof direct !== 'function') throw new Error(`Native method not found: ${type}`);
        direct.call(bridge, safeJsonStringify(payload));
      }
    } catch (error) {
      if (expectReply) {
        state.callbacks.delete(id);
        return Promise.reject(error);
      }
      console.warn('[native-bridge] call failed:', type, error);
      return false;
    }

    return expectReply ? promise : true;
  }

  function call(type, payload = {}, options = {}) {
    return postToBridge(type, payload, options);
  }

  function haptic(style = 'light') {
    const bridge = getBridge();
    if (!bridge) return false;
    try {
      if (typeof bridge.haptic === 'function') {
        bridge.haptic(style);
        return true;
      }
    } catch {}
    return postToBridge('haptic', { style });
  }

  function setGlassMode(mode, reason = 'web-request') {
    document.documentElement.dataset.nativeGlassMode = mode;
    return postToBridge('setGlassMode', { mode, reason });
  }

  function notifyReady() {
    const capabilities = readCapabilities();
    const available = Boolean(getBridge());

    document.documentElement.classList.toggle('native-shell', available);
    document.documentElement.classList.toggle('web-shell', !available);
    document.body?.classList.toggle('native-shell', available);
    document.body?.classList.toggle('web-shell', !available);

    dispatchNativeEvent('ready', { available, capabilities, version: VERSION });
    if (available) postToBridge('webReady', { capabilities, version: VERSION });
  }

  window.AiLedgerNativeBridge = {
    version: VERSION,
    names: BRIDGE_NAMES.slice(),
    getBridge,
    isAvailable: () => Boolean(getBridge()),
    getCapabilities: () => state.lastCapabilities || readCapabilities(),
    call,
    post: postToBridge,
    haptic,
    setGlassMode,
    openApp: (packageName, fallbackName) => call('openApp', { packageName, fallbackName }),
    setAlarm: (alarm) => call('setAlarm', alarm),
    startNavigation: (target) => call('startNavigation', target),
    isAgentAccessibilityEnabled: () => {
      const native = getBridge();
      try { return Boolean(native?.isAgentAccessibilityEnabled?.()); } catch { return false; }
    },
    isAgentInputMethodActive: () => {
      const native = getBridge();
      try { return Boolean(native?.isAgentInputMethodActive?.()); } catch { return false; }
    },
    observeAgentScreen: () => {
      const native = getBridge();
      try {
        const raw = native?.observeAgentScreen?.();
        return typeof raw === 'string' ? safeJsonParse(raw, { ok: false }) : (raw || { ok: false });
      } catch (error) {
        return { ok: false, error: String(error?.message || error || 'observe failed') };
      }
    },
    executeAgentStep: (step) => {
      const native = getBridge();
      try {
        const raw = native?.executeAgentStep?.(safeJsonStringify(step || {}));
        return typeof raw === 'string' ? safeJsonParse(raw, { ok: false }) : (raw || { ok: false });
      } catch (error) {
        return { ok: false, error: String(error?.message || error || 'execute failed') };
      }
    },
    openAccessibilitySettings: () => {
      const native = getBridge();
      try { return Boolean(native?.openAccessibilitySettings?.()); } catch { return call('openAccessibilitySettings'); }
    },
    openInputMethodSettings: () => {
      const native = getBridge();
      try { return Boolean(native?.openInputMethodSettings?.()); } catch { return call('openInputMethodSettings'); }
    },
    showInputMethodPicker: () => {
      const native = getBridge();
      try { return Boolean(native?.showInputMethodPicker?.()); } catch { return call('showInputMethodPicker'); }
    },
    closeQuickAi: () => call('closeQuickAi'),
    openFullApp: () => call('openFullApp'),
    notifyReady,
  };

  window.AiLedgerNativeBridgeReceive = function receiveNativeMessage(message) {
    const data = typeof message === 'string' ? safeJsonParse(message, {}) : (message || {});
    if (data.id && data.reply === true) {
      settleRequest(data.id, data.ok !== false, data.payload || data.error || {});
    }
    if (data.type) {
      dispatchNativeEvent(data.type, data.payload || {});
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', notifyReady, { once: true });
  } else {
    notifyReady();
  }
})();
