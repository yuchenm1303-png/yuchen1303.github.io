(() => {
  'use strict';

  const STYLE_ID = 'navigation-polish-style';
  const NAV_SYNC_EVENT = 'assistant-nav-polished';
  let navObserver = null;
  let syncFrame = 0;
  let patchFrame = 0;

  function installStyle() {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .bottom-nav {
        grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
        isolation: isolate !important;
      }

      .bottom-nav::after {
        content: "";
        position: absolute;
        inset: 1px;
        z-index: 0;
        border-radius: inherit;
        pointer-events: none;
        background:
          linear-gradient(180deg, rgba(255,255,255,.24), rgba(255,255,255,.038) 52%, rgba(0,0,0,.025)),
          radial-gradient(circle at 50% -20%, rgba(255,255,255,.22), transparent 54%);
        opacity: .62;
      }

      .bottom-nav .nav-btn {
        min-width: 0 !important;
        user-select: none;
        -webkit-user-select: none;
      }

      .bottom-nav .nav-btn span,
      .bottom-nav .nav-btn em {
        position: relative;
        z-index: 4;
        transition: none !important;
        transform: none !important;
      }

      .bottom-nav .nav-btn.active {
        color: rgba(247,253,255,.98) !important;
        background: transparent !important;
        text-shadow: 0 1px 8px rgba(31,123,148,.16);
      }

      .bottom-nav .nav-btn.active span {
        transform: none !important;
      }

      .bottom-nav .nav-btn:not(.active) em {
        opacity: .72;
      }

      .bottom-nav .nav-btn:focus-visible {
        outline: 2px solid rgba(134,236,226,.72) !important;
        outline-offset: 3px;
      }

      .bottom-nav .liquid-nav-indicator {
        border-radius: 18px !important;
        background:
          radial-gradient(circle at 26% 14%, rgba(255,255,255,.48), rgba(255,255,255,.15) 34%, transparent 68%),
          linear-gradient(135deg, rgba(92,164,196,.38), rgba(98,128,190,.26) 52%, rgba(255,255,255,.10)) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.44),
          inset 0 -1px 0 rgba(0,0,0,.08),
          0 8px 18px rgba(14,101,128,.13) !important;
      }

      @media (hover:hover) {
        .bottom-nav .nav-btn:not(.active):hover {
          background: rgba(255,255,255,.10) !important;
        }
      }

      @media (max-width: 360px) {
        .bottom-nav {
          width: min(92vw, 400px) !important;
        }
        .bottom-nav .nav-btn em {
          font-size: 10px !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function getNav() {
    return document.querySelector('.bottom-nav');
  }

  function getActiveButton() {
    const nav = getNav();
    return nav?.querySelector('.nav-btn.active') || null;
  }

  function scheduleSyncNavState() {
    cancelAnimationFrame(syncFrame);
    syncFrame = requestAnimationFrame(syncNavState);
  }

  function syncNavState() {
    const nav = getNav();
    if (!nav) return;
    const buttons = [...nav.querySelectorAll('.nav-btn')];
    const active = getActiveButton();
    let changed = nav.dataset.activeView !== (active?.dataset.view || '');

    buttons.forEach((button) => {
      const isActive = button === active;
      const nextCurrent = isActive ? 'page' : null;
      if (isActive && button.getAttribute('aria-current') !== nextCurrent) button.setAttribute('aria-current', nextCurrent);
      if (!isActive && button.hasAttribute('aria-current')) button.removeAttribute('aria-current');

      const label = button.querySelector('em')?.textContent?.trim() || button.dataset.view || '页面';
      const nextLabel = isActive ? `${label}，当前页` : `切换到${label}`;
      if (button.getAttribute('aria-label') !== nextLabel) button.setAttribute('aria-label', nextLabel);
    });

    nav.dataset.activeView = active?.dataset.view || '';
    if (changed) {
      window.dispatchEvent(new CustomEvent(NAV_SYNC_EVENT, { detail: { view: nav.dataset.activeView } }));
    }
  }

  function watchNav() {
    const nav = getNav();
    if (!nav || nav.dataset.navPolishObserved === 'true') return Boolean(nav);
    nav.dataset.navPolishObserved = 'true';
    navObserver?.disconnect();
    navObserver = new MutationObserver(scheduleSyncNavState);
    nav.querySelectorAll('.nav-btn').forEach((button) => {
      navObserver.observe(button, { attributes: true, attributeFilter: ['class'] });
    });
    nav.addEventListener('click', scheduleSyncNavState, { passive: true });
    scheduleSyncNavState();
    return true;
  }

  function isPreferenceCard(command) {
    return command?.commandKind === 'navigation_preference'
      || command?.params?.intent === 'navigation_preference'
      || Boolean(command?.params?.updates);
  }

  function explicitMapProvider(text) {
    if (/高德|amap/i.test(text)) return 'amap';
    if (/百度|baidu/i.test(text)) return 'baidu';
    return '';
  }

  function providerLabel(provider) {
    if (window.AssistantPreferences?.getMapLabel) return window.AssistantPreferences.getMapLabel(provider);
    return provider === 'amap' ? '高德地图' : '百度地图';
  }

  function isHomeDestination(destination) {
    if (window.AssistantPreferences?.isHomeDestination) {
      return window.AssistantPreferences.isHomeDestination(destination);
    }
    return /^(家|回家|我家|家里|到家)$/u.test(String(destination || '').trim());
  }

  function enhanceNavigationCommand(command, sourceText = '') {
    if (!command || command.type !== 'navigate' || isPreferenceCard(command)) return command;

    if (window.AssistantPreferences?.decorateNavigationParams) {
      const params = window.AssistantPreferences.decorateNavigationParams(command.params || {}, { sourceText });
      return {
        ...command,
        title: `${params.appName || '地图'}导航`,
        summary: params.placeAddressMissing ? `${params.destinationAlias || params.destination}（未填写地址）` : `到 ${params.destination}`,
        params,
      };
    }

    const prefs = window.AssistantPreferences?.getPreferences?.() || {};
    const provider = explicitMapProvider(sourceText) || prefs.mapProvider || command.params?.mapProvider || 'baidu';
    const rawDestination = String(command.params?.destinationAlias || command.params?.destination || '').trim();
    const homeRequested = isHomeDestination(rawDestination);
    const homeAddress = String(prefs.places?.home || prefs.homeAddress || '').trim();
    const destination = homeRequested && homeAddress ? homeAddress : rawDestination;
    const params = {
      ...(command.params || {}),
      appName: providerLabel(provider),
      mapProvider: provider === 'amap' ? 'amap' : 'baidu',
      destination,
      destinationAlias: rawDestination,
      homeAddressMissing: homeRequested && !homeAddress,
      mode: ['driving', 'walking', 'riding', 'transit'].includes(command.params?.mode) ? command.params.mode : (prefs.defaultMode || 'driving'),
    };
    return {
      ...command,
      title: `${params.appName}导航`,
      summary: params.homeAddressMissing ? '回家（未填写家庭地址）' : `到 ${destination}`,
      params,
    };
  }

  function installMobileNavigationPatch() {
    const actions = window.MobileCommandActions;
    if (!actions || actions.__navigationPolished) return Boolean(actions);
    actions.__navigationPolished = true;

    const baseParse = actions.parse;
    if (typeof baseParse === 'function') {
      actions.parse = (text) => enhanceNavigationCommand(baseParse(text), text);
    }

    const baseRenderCard = actions.renderCard;
    if (typeof baseRenderCard === 'function') {
      actions.renderCard = (command, state, message) => baseRenderCard(enhanceNavigationCommand(command), state, message);
    }

    const baseCreateReply = actions.createReply;
    if (typeof baseCreateReply === 'function') {
      actions.createReply = (command) => {
        const next = enhanceNavigationCommand(command);
        if (isPreferenceCard(next)) return baseCreateReply(next);
        if (next?.type !== 'navigate') return baseCreateReply(command);
        const map = next.params?.appName || '地图';
        const alias = next.params?.destinationAlias || next.params?.destination || '目的地';
        if (next.params?.homeAddressMissing || next.params?.placeAddressMissing) {
          return `我知道你想去“${alias}”，但这个常用地址还没填写。你可以先确认尝试打开${map}，也可以说“把${alias}设为具体地址”。`;
        }
        const modeLabel = window.AssistantPreferences?.getModeLabel?.(next.params?.mode) || '驾车';
        return `我理解为要用${map}${modeLabel}导航到“${next.params.destination}”，确认后我再执行。`;
      };
    }
    return true;
  }

  function schedulePatchRetries() {
    if (installMobileNavigationPatch()) return;
    cancelAnimationFrame(patchFrame);
    patchFrame = requestAnimationFrame(() => {
      if (!installMobileNavigationPatch()) window.setTimeout(installMobileNavigationPatch, 500);
    });
  }

  function boot() {
    if (document.documentElement.dataset.navigationPolishReady === 'true') return;
    document.documentElement.dataset.navigationPolishReady = 'true';
    installStyle();
    watchNav();
    schedulePatchRetries();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();