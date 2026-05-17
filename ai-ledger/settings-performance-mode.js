(() => {
  'use strict';

  const STYLE_ID = 'settings-performance-mode-style';
  const PANEL_ID = 'glassPerformancePanel';
  const PERF_KEY = 'ai-assistant-performance-mode-v1';
  const PERF_VERSION_KEY = 'ai-assistant-performance-mode-version-v1';
  const PERF_VERSION = '2';
  const MODES = [
    { id: 'auto', label: '自动推荐', desc: '优先流畅：安卓 WebView 默认偏保守，性能足够时再保留玻璃。' },
    { id: 'lite', label: '极速', desc: '关闭实时模糊、导航高光和背景动效，优先解决明显卡顿。' },
    { id: 'balanced', label: '流畅玻璃', desc: '保留轻量玻璃和清晰文字，滚动时自动降级，推荐日常使用。' },
    { id: 'quality', label: '精致玻璃', desc: '比流畅档更透明、更细腻，但控制动画和模糊强度。' },
    { id: 'full', label: '高画质 beta', desc: '保留最强液态玻璃和动态效果，适合作为测试档。' },
  ];

  function getPerformance() {
    return window.AssistantPerformance || null;
  }

  function readMode() {
    try {
      const savedVersion = localStorage.getItem(PERF_VERSION_KEY);
      let mode = localStorage.getItem(PERF_KEY) || 'auto';
      mode = MODES.some((item) => item.id === mode) ? mode : 'auto';
      if (savedVersion !== PERF_VERSION) {
        if (mode === 'full') mode = 'balanced';
        localStorage.setItem(PERF_KEY, mode);
        localStorage.setItem(PERF_VERSION_KEY, PERF_VERSION);
      }
      return mode;
    } catch {
      return 'auto';
    }
  }

  function deviceText() {
    const perf = getPerformance();
    const info = perf?.getDeviceInfo?.() || {};
    const resolved = perf?.getResolvedMode?.() || document.documentElement.dataset.performanceMode || 'balanced';
    const memory = info.memory ? `${info.memory}GB 内存` : '内存未知';
    const cores = info.cores ? `${info.cores} 核` : 'CPU 核心未知';
    const platform = info.isAndroid ? '安卓 WebView' : '当前浏览器';
    const width = info.minWidth ? `窄边 ${info.minWidth}px` : '宽度未知';
    const names = { lite: '极速', balanced: '流畅玻璃', quality: '精致玻璃', full: '高画质 beta' };
    return `${platform} · ${memory} · ${cores} · ${width} · 当前生效：${names[resolved] || resolved}`;
  }

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      #glassPerformancePanel {
        display: grid;
        gap: 12px;
      }

      .performance-mode-desc {
        margin: 0;
        color: rgba(214,224,246,.70);
        font-size: 13px;
        line-height: 1.6;
      }

      .performance-mode-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 9px;
      }

      .performance-mode-option {
        min-height: 74px;
        display: grid;
        align-content: start;
        gap: 5px;
        padding: 11px 12px;
        border-radius: 18px;
        border: 1px solid rgba(255,255,255,.16);
        background: rgba(255,255,255,var(--assistant-glass-control-alpha,.042));
        color: rgba(232,240,255,.78);
        text-align: left;
        font: inherit;
        box-shadow: inset 0 .7px 0 rgba(255,255,255,.18);
      }

      .performance-mode-option strong {
        color: rgba(248,250,255,.98);
        font-size: 14px;
        font-weight: 900;
      }

      .performance-mode-option span {
        font-size: 11px;
        line-height: 1.35;
      }

      .performance-mode-option.active {
        border-color: rgba(115,231,255,.42);
        background:
          linear-gradient(135deg, rgba(37,190,210,.24), rgba(102,116,255,.18)),
          rgba(255,255,255,var(--assistant-glass-selected-alpha,.052));
        box-shadow: 0 8px 18px rgba(0,0,0,.12), inset 0 .7px 0 rgba(255,255,255,.30);
      }

      .performance-mode-option[data-performance-mode="full"] strong::after {
        content: "测试";
        display: inline-flex;
        margin-left: 6px;
        padding: 2px 6px;
        border-radius: 999px;
        border: 1px solid rgba(255,255,255,.16);
        color: rgba(255,255,255,.76);
        background: rgba(255,255,255,.06);
        font-size: 10px;
        line-height: 1;
        vertical-align: middle;
      }

      .performance-device-hint {
        padding: 10px 12px;
        border-radius: 16px;
        border: 1px solid rgba(255,255,255,.14);
        background: rgba(255,255,255,.052);
        color: rgba(214,224,246,.74);
        font-size: 12px;
        line-height: 1.5;
      }

      @media (max-width: 420px) {
        .performance-mode-grid { grid-template-columns: 1fr; }
      }
    `;
    document.head.appendChild(style);
  }

  function setActive(mode = readMode()) {
    document.querySelectorAll('[data-performance-mode]').forEach((button) => {
      button.classList.toggle('active', button.dataset.performanceMode === mode);
    });
    const hint = document.querySelector('#performanceDeviceHint');
    if (hint) hint.textContent = deviceText();
  }

  function saveMode(mode) {
    const perf = getPerformance();
    if (perf?.setMode) perf.setMode(mode);
    else {
      try {
        localStorage.setItem(PERF_KEY, mode);
        localStorage.setItem(PERF_VERSION_KEY, PERF_VERSION);
      } catch {}
      document.documentElement.dataset.performanceModeSelected = mode;
    }
    setActive(mode);
    const toast = document.querySelector('#toast');
    if (toast) {
      const label = MODES.find((item) => item.id === mode)?.label || mode;
      toast.textContent = `玻璃性能模式已切换为：${label}`;
      toast.classList.add('show');
      clearTimeout(saveMode.timer);
      saveMode.timer = setTimeout(() => toast.classList.remove('show'), 1800);
    }
  }

  function buildPanel() {
    const html = `
      <section id="${PANEL_ID}" class="glass-card appearance-plus-card" data-settings-group-target="display">
        <div class="section-head"><h2>玻璃性能模式</h2></div>
        <p class="performance-mode-desc">默认建议用“自动推荐”或“流畅玻璃”。高画质 beta 保留最强视觉效果，主要用来测试，不建议作为卡顿设备的日常档位。</p>
        <div class="performance-mode-grid">
          ${MODES.map((item) => `
            <button class="performance-mode-option" type="button" data-performance-mode="${item.id}">
              <strong>${item.label}</strong>
              <span>${item.desc}</span>
            </button>
          `).join('')}
        </div>
        <div id="performanceDeviceHint" class="performance-device-hint">正在读取设备状态…</div>
      </section>
    `;
    const template = document.createElement('template');
    template.innerHTML = html.trim();
    return template.content.firstElementChild;
  }

  function installPanel() {
    if (document.getElementById(PANEL_ID)) {
      setActive();
      return true;
    }
    const appearancePanel = document.getElementById('appearancePlusPanel');
    const settingsView = document.getElementById('view-settings');
    if (!settingsView) return false;
    const panel = buildPanel();
    if (appearancePanel?.parentNode) appearancePanel.after(panel);
    else settingsView.appendChild(panel);
    panel.addEventListener('click', (event) => {
      const button = event.target.closest('[data-performance-mode]');
      if (!button) return;
      saveMode(button.dataset.performanceMode);
    });
    setActive();
    return true;
  }

  function boot() {
    installStyle();
    if (!installPanel()) {
      const observer = new MutationObserver(() => {
        if (installPanel()) observer.disconnect();
      });
      observer.observe(document.body, { childList: true, subtree: true });
    }
    window.addEventListener('assistant-performance-change', () => setActive(readMode()));
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
