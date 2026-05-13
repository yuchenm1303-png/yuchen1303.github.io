(() => {
  const pressableSelector = [
    "button",
    ".record-item",
    ".summary-chip",
    ".summary-box",
    ".metric-card",
    ".chart-card",
    ".summary-card",
    ".account-row",
    ".draft-card",
    ".draft-item",
    ".auth-tab"
  ].join(", ");

  let activePressElement = null;
  let userRequestedAuth = false;

  function setPressPoint(el, event) {
    if (!el || !event || typeof event.clientX !== "number") return;
    const rect = el.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const x = Math.min(Math.max(((event.clientX - rect.left) / rect.width) * 100, 0), 100);
    const y = Math.min(Math.max(((event.clientY - rect.top) / rect.height) * 100, 0), 100);
    el.style.setProperty("--press-x", `${x.toFixed(1)}%`);
    el.style.setProperty("--press-y", `${y.toFixed(1)}%`);
  }

  function preferAiView() {
    const aiButton = document.querySelector('.nav-btn[data-view="ai"]');
    if (aiButton && !aiButton.classList.contains("active")) aiButton.click();
  }

  function suppressAutoAuth() {
    const overlay = document.querySelector("#authOverlay");
    if (!overlay || userRequestedAuth) return;
    if (overlay.classList.contains("open")) {
      overlay.classList.remove("open");
      overlay.setAttribute("aria-hidden", "true");
    }
  }

  document.addEventListener("pointerdown", (event) => {
    if (event.target.closest("#openAuthBtn, #logoutBtn, #authSubmitBtn, .auth-tab, #authOverlay .text-btn")) {
      userRequestedAuth = true;
    }

    activePressElement = event.target.closest(pressableSelector);
    setPressPoint(activePressElement, event);
  }, { passive: true, capture: true });

  document.addEventListener("pointermove", (event) => {
    setPressPoint(activePressElement, event);
  }, { passive: true });

  ["pointerup", "pointercancel"].forEach((type) => {
    document.addEventListener(type, () => {
      activePressElement = null;
    }, { passive: true });
  });

  requestAnimationFrame(preferAiView);
  window.addEventListener("load", () => {
    preferAiView();
    window.setTimeout(preferAiView, 120);
  });

  const authGuard = window.setInterval(suppressAutoAuth, 80);
  window.setTimeout(() => window.clearInterval(authGuard), 5000);
})();
