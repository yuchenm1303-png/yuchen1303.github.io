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

  function sleep(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, Math.max(0, Number(ms || 0))));
  }

  function normalizeForMatch(value) {
    return String(value ?? '').toLowerCase().replace(/\s+/g, '');
  }

  function observeAgentScreen() {
    const native = bridge();
    if (!native?.observeAgentScreen) return { ok: false, error: 'native_observe_unavailable' };
    return native.observeAgentScreen();
  }

  function executeAgentStep(step) {
    const native = bridge();
    if (!native?.executeAgentStep) return { ok: false, error: 'native_agent_executor_unavailable' };
    return native.executeAgentStep(step || {});
  }

  function screenTextBlob(observation) {
    const texts = Array.isArray(observation?.texts) ? observation.texts : [];
    const nodes = Array.isArray(observation?.nodes) ? observation.nodes : [];
    return normalizeForMatch([
      ...texts,
      ...nodes.map((node) => `${node?.text || ''} ${node?.contentDescription || ''}`),
    ].join(' '));
  }

  function verifyAgentStepOutcome(step, execution, before, after) {
    const type = String(step?.type || step?.action || '').toLowerCase();
    const text = String(step?.text || step?.value || '');
    const afterBlob = screenTextBlob(after);
    const beforeBlob = screenTextBlob(before);
    const screenChanged = afterBlob && beforeBlob && afterBlob !== beforeBlob;
    if (execution?.ok === false) {
      return {
        result: 'wrong',
        confidence: 0.82,
        actionSummary: `${type} failed`,
        reason: execution.error || execution.reason || 'Native executor rejected the action.',
        nextHint: 'Do not assume the action succeeded; choose a recovery action from the new observation.',
      };
    }
    if (type === 'input_text') {
      const visible = text && afterBlob.includes(normalizeForMatch(text));
      return {
        result: visible ? 'progress' : 'wrong',
        confidence: visible ? 0.88 : 0.74,
        actionSummary: `input_text ${text}`,
        observedSummary: visible ? 'Requested text is visible after input.' : 'Requested text is not visible after input.',
        reason: visible ? 'Input text appears on screen.' : 'Input was accepted by executor but the requested text is not visible.',
        nextHint: visible ? 'Continue with the next task step.' : 'Refocus an editable field or use another input route before submitting.',
      };
    }
    if (['open_app', 'tap_xy', 'tap_node', 'long_press', 'swipe', 'scroll', 'back', 'home', 'recents', 'notifications', 'quick_settings'].includes(type)) {
      return {
        result: screenChanged ? 'progress' : 'uncertain',
        confidence: screenChanged ? 0.7 : 0.45,
        actionSummary: type,
        observedSummary: screenChanged ? 'Screen content changed after action.' : 'Screen content did not clearly change after action.',
        reason: screenChanged ? 'The action produced a visible state change.' : 'The action may have had no effect or the app state is visually stable.',
        nextHint: screenChanged ? 'Plan from the new screen.' : 'Observe carefully and avoid repeating the same action unless there is evidence.',
      };
    }
    return {
      result: execution?.ok ? 'progress' : 'uncertain',
      confidence: execution?.ok ? 0.55 : 0.35,
      actionSummary: type || 'unknown',
      reason: execution?.ok ? 'Action accepted by executor.' : 'Unable to verify action result.',
      nextHint: 'Use the latest observation before deciding the next action.',
    };
  }

  async function executeAndObserveAgentStep(step, options = {}) {
    const before = options.before || observeAgentScreen();
    const execution = executeAgentStep(step);
    const waitMs = Number(options.waitMs ?? step?.postActionWaitMs ?? step?.durationMs ?? 650);
    await sleep(Math.max(250, Math.min(2400, waitMs)));
    const after = observeAgentScreen();
    const lastOutcome = verifyAgentStepOutcome(step, execution, before, after);
    return {
      ok: execution?.ok !== false,
      step,
      execution,
      before,
      after,
      lastOutcome,
      recentAction: `${step?.type || step?.action || 'action'} ${step?.appName || step?.packageName || step?.targetText || step?.text || ''}`.trim(),
    };
  }

  function normalizeAgentEndpoint(endpoint) {
    return String(endpoint || window.AI_LEDGER_CONFIG?.aiEndpoint || localStorage.getItem('ai-ledger-ai-endpoint-v1') || '').trim().replace(/\/$/, '');
  }

  function agentStepFromResponse(data) {
    if (!data || typeof data !== 'object') return null;
    return data.agentStep || (Array.isArray(data.agentSteps) ? data.agentSteps[0] : null) || (Array.isArray(data.steps) ? data.steps[0] : null) || null;
  }

  function isTerminalAgentStep(step, data) {
    const type = String(step?.type || '').toLowerCase();
    return Boolean(data?.isComplete || data?.agentState?.isComplete || type === 'finish' || type === 'need_user_help');
  }

  async function defaultScreenshotProvider() {
    if (typeof window.AiLedgerAgentRuntime?.captureScreenshot === 'function') {
      return await window.AiLedgerAgentRuntime.captureScreenshot();
    }
    return null;
  }

  async function callAgentStepBackend(options) {
    const endpoint = normalizeAgentEndpoint(options.endpoint);
    if (!endpoint) throw new Error('Missing AI endpoint for agent loop.');
    const screenshot = options.screenshot || null;
    const body = {
      intent: 'agent_step',
      agentMode: true,
      agentGoal: options.goal,
      sessionId: options.sessionId,
      screenSnapshot: options.screenSnapshot || {},
      deviceContext: options.deviceContext || {},
      recentAgentActions: options.recentAgentActions || [],
      lastOutcome: options.lastOutcome || null,
      outcomeVerification: options.lastOutcome || null,
      supportedAgentSteps: options.supportedAgentSteps || [
        'open_app', 'home', 'back', 'recents', 'notifications', 'quick_settings',
        'tap_node', 'tap_xy', 'long_press', 'input_text', 'scroll', 'swipe',
        'wait', 'finish', 'need_user_help',
      ],
      responseFormat: { includeAgentStep: true },
    };
    if (screenshot) {
      body.screenshot = screenshot;
      if (screenshot.base64) body.screenshotBase64 = screenshot.base64;
      if (screenshot.mimeType) body.screenshotMimeType = screenshot.mimeType;
      if (screenshot.width) body.screenshotWidth = screenshot.width;
      if (screenshot.height) body.screenshotHeight = screenshot.height;
      if (screenshot.displayWidth) body.displayWidth = screenshot.displayWidth;
      if (screenshot.displayHeight) body.displayHeight = screenshot.displayHeight;
    }
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
    const text = await response.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch {}
    if (!response.ok) throw new Error(data?.error || data?.message || text || `agent_step_http_${response.status}`);
    return data || {};
  }

  async function runAgentTask(goal, options = {}) {
    const sessionId = options.sessionId || `native-agent-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const maxSteps = Math.max(1, Math.min(24, Number(options.maxSteps || 10)));
    const recentAgentActions = Array.isArray(options.recentAgentActions) ? options.recentAgentActions.slice(-12) : [];
    let screenSnapshot = options.screenSnapshot || observeAgentScreen();
    let lastOutcome = options.lastOutcome || null;
    const trace = [];

    for (let stepIndex = 0; stepIndex < maxSteps; stepIndex += 1) {
      const screenshot = options.getScreenshot
        ? await options.getScreenshot({ goal, sessionId, stepIndex, screenSnapshot, lastOutcome, recentAgentActions })
        : await defaultScreenshotProvider();
      const data = await callAgentStepBackend({
        ...options,
        goal,
        sessionId,
        screenSnapshot,
        screenshot,
        lastOutcome,
        recentAgentActions,
      });
      const step = agentStepFromResponse(data);
      trace.push({ phase: 'plan', stepIndex, data, step });
      if (!step || isTerminalAgentStep(step, data)) {
        return { ok: true, status: data?.isComplete ? 'complete' : 'paused', sessionId, final: data, trace, lastOutcome, screenSnapshot };
      }
      const execution = await executeAndObserveAgentStep(step, { waitMs: options.waitMs, before: screenSnapshot });
      screenSnapshot = execution.after || observeAgentScreen();
      lastOutcome = execution.lastOutcome;
      recentAgentActions.push(execution.recentAction);
      while (recentAgentActions.length > 12) recentAgentActions.shift();
      trace.push({ phase: 'execute', stepIndex, execution });
      if (typeof options.onStep === 'function') {
        try { options.onStep({ stepIndex, planned: data, execution, screenSnapshot, lastOutcome, recentAgentActions: recentAgentActions.slice() }); } catch {}
      }
    }

    return {
      ok: false,
      status: 'max_steps',
      sessionId,
      reason: 'Reached maximum agent loop steps.',
      trace,
      lastOutcome,
      screenSnapshot,
      recentAgentActions,
    };
  }

  window.AiLedgerAgentRuntime = {
    observe: observeAgentScreen,
    execute: executeAgentStep,
    executeAndObserve: executeAndObserveAgentStep,
    callBackend: callAgentStepBackend,
    runTask: runAgentTask,
    verifyOutcome: verifyAgentStepOutcome,
  };

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
