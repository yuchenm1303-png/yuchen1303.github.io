(() => {
  const STYLE_ID = 'navigation-polish-style';
  const NAV_SYNC_EVENT = 'assistant-nav-polished';

  function installStyle() {
    const old = document.querySelector(`#${STYLE_ID}`);
    if (old) old.remove();

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
          linear-gradient(180deg, rgba(255,255,255,.32), rgba(255,255,255,.045) 52%, rgba(0,0,0,.025)),
          radial-gradient(circle at 50% -20%, rgba(255,255,255,.34), transparent 54%);
        opacity: .72;
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
        transition: transform 260ms cubic-bezier(.22,1,.36,1), color 220ms ease, opacity 220ms ease;
      }

      .bottom-nav .nav-btn.active {
        color: rgba(247,253,255,.98) !important;
        background: transparent !important;
        text-shadow: 0 1px 10px rgba(31,123,148,.24);
      }

      .bottom-nav .nav-btn.active span {
        transform: translate3d(0,-1px,0) scale(1.04);
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
          radial-gradient(circle at 26% 14%, rgba(255,255,255,.62), rgba(255,255,255,.18) 34%, transparent 68%),
          linear-gradient(135deg, rgba(30,178,184,.72), rgba(67,142,221,.38) 52%, rgba(255,255,255,.12)) !important;
        box-shadow:
          inset 0 1px 0 rgba(255,255,255,.52),
          inset 0 -1px 0 rgba(0,0,0,.08),
          0 10px 22px rgba(14,101,128,.20) !important;
      }

      @media (hover:hover) {
        .bottom-nav .nav-btn:not(.active):hover {
          background: rgba(255,255,255,.18) !important;
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

  function syncNavState() {
    const nav = getNav();
    if (!nav) return;
    const buttons = [...nav.querySelectorAll('.nav-btn')];
    const active = getActiveButton();
    buttons.forEach((button) => {
      const isActive = button === active;
      if (isActive) button.setAttribute('aria-current', 'page');
      else button.removeAttribute('aria-current');
      const label = button.querySelector('em')?.textContent?.trim() || button.dataset.view || '页面';
      button.setAttribute('aria-label', isActive ? `${label}，当前页` : `切换到${label}`);
    });
    nav.dataset.activeView = active?.dataset.view || '';
    window.dispatchEvent(new CustomEvent(NAV_SYNC_EVENT, {
      detail: { view: nav.dataset.activeView },
    }));
  }

  function watchNav() {
    const nav = getNav();
    if (!nav || nav.dataset.navPolishObserved === 'true') return;
    nav.dataset.navPolishObserved = 'true';
    const observer = new MutationObserver(syncNavState);
    nav.querySelectorAll('.nav-btn').forEach((button) => {
      observer.observe(button, { attributes: true, attributeFilter: ['class'] });
    });
    nav.addEventListener('click', () => window.setTimeout(syncNavState, 0), { passive: true });
    syncNavState();
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
    if (!command || command.type !== 'navigate') return command;
    const prefs = window.AssistantPreferences?.getPreferences?.() || {};
    const provider = explicitMapProvider(sourceText) || prefs.mapProvider || command.params?.mapProvider || 'baidu';
    const rawDestination = String(command.params?.destination || command.params?.destinationAlias || '').trim();
    const homeRequested = isHomeDestination(rawDestination);
    const homeAddress = String(prefs.homeAddress || '').trim();
    const destination = homeRequested && homeAddress ? homeAddress : rawDestination;
    const params = {
      ...(command.params || {}),
      appName: providerLabel(provider),
      mapProvider: provider === 'amap' ? 'amap' : 'baidu',
      destination,
      destinationAlias: rawDestination,
      homeAddressMissing: homeRequested && !homeAddress,
      mode: ['driving', 'walking', 'riding'].includes(command.params?.mode) ? command.params.mode : 'driving',
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
    if (!actions || actions.__navigationPolished) return;
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
        if (next?.type !== 'navigate') return baseCreateReply(command);
        const map = next.params?.appName || '地图';
        const alias = next.params?.destinationAlias || next.params?.destination || '目的地';
        if (next.params?.homeAddressMissing) {
          return `我理解为要用${map}导航回家，但你还没有填写家庭地址。确认后会先按“${alias}”尝试导航；也可以到设置里的“手机偏好”填写家庭地址。`;
        }
        return `我理解为要用${map}导航到“${next.params.destination}”，确认后我再执行。`;
      };
    }
  }

  function boot() {
    installStyle();
    watchNav();
    installMobileNavigationPatch();
    window.setTimeout(() => { watchNav(); installMobileNavigationPatch(); }, 300);
    window.setTimeout(() => { watchNav(); installMobileNavigationPatch(); }, 1200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
