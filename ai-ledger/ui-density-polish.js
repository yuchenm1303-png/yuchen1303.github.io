(() => {
  'use strict';

  const STYLE_ID = 'ui-density-polish-style';

  function installDensityPolish() {
    if (document.getElementById(STYLE_ID)) return;

    const style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      :root {
        --ui-radius-card: 24px;
        --ui-radius-control: 16px;
        --ui-gap: 12px;
      }

      body {
        padding-bottom: calc(86px + env(safe-area-inset-bottom)) !important;
      }

      .app-shell {
        max-width: 480px !important;
        padding: 18px 14px 102px !important;
      }

      .view,
      .settings-group-list {
        gap: 12px !important;
      }

      .page-header {
        margin-bottom: 14px !important;
      }

      .eyebrow {
        margin-bottom: 6px !important;
        font-size: 13px !important;
        letter-spacing: .18px !important;
        font-weight: 850 !important;
      }

      .page-header h1 {
        font-size: 34px !important;
        line-height: 1.08 !important;
        letter-spacing: -.6px !important;
        font-weight: 900 !important;
      }

      .subtext,
      .helper-text,
      .appearance-plus-desc,
      .settings-group-desc,
      .settings-entry-desc {
        font-size: 13px !important;
        line-height: 1.48 !important;
      }

      .glass-card,
      .chat-shell,
      .summary-card,
      .metric-card,
      .chart-card,
      .tool-card,
      .auth-sheet,
      .mobile-command-card,
      .tools-panel-card,
      .account-row {
        margin-bottom: 12px !important;
        padding: 14px !important;
        border-radius: var(--ui-radius-card) !important;
      }

      .section-head {
        margin-bottom: 10px !important;
        gap: 8px !important;
      }

      .section-head h2 {
        font-size: 17px !important;
        line-height: 1.25 !important;
      }

      .soft-label,
      .field-label,
      .record-meta,
      .progress-text {
        font-size: 12px !important;
      }

      input,
      select {
        height: 44px !important;
        padding: 0 13px !important;
        border-radius: var(--ui-radius-control) !important;
        font-size: 14px !important;
      }

      textarea {
        min-height: 88px !important;
        padding: 12px 13px !important;
        border-radius: var(--ui-radius-control) !important;
        font-size: 14px !important;
      }

      .primary-btn,
      .ghost-btn,
      .danger-btn {
        min-height: 44px !important;
        border-radius: 16px !important;
        font-size: 14px !important;
      }

      .tag-btn,
      .range-chip {
        min-height: 36px !important;
        padding: 0 13px !important;
        font-size: 13px !important;
      }

      .quick-tags,
      .chip-row,
      .settings-actions,
      .record-list,
      .appearance-plus-grid,
      .appearance-toggle-row,
      .summary-grid,
      .mini-grid,
      .metric-grid,
      .form-grid,
      .tools-grid,
      .chat-summary-strip,
      .chat-messages,
      .chat-composer {
        gap: 9px !important;
      }

      .summary-top {
        gap: 12px !important;
      }

      .card-kicker {
        margin-bottom: 8px !important;
        font-size: 12px !important;
      }

      .big-money {
        font-size: 34px !important;
        letter-spacing: -.3px !important;
      }

      .budget-pill {
        padding: 8px 11px !important;
        font-size: 12px !important;
      }

      .progress-wrap {
        margin-top: 14px !important;
      }

      .progress-track {
        height: 10px !important;
      }

      .summary-grid {
        margin-top: 13px !important;
      }

      .summary-box {
        padding: 13px !important;
        border-radius: 18px !important;
      }

      .summary-box span,
      .mini-stat span,
      .metric-card span,
      .summary-chip span {
        margin-bottom: 6px !important;
        font-size: 12px !important;
      }

      .summary-box strong,
      .mini-stat strong,
      .metric-card strong {
        font-size: 17px !important;
      }

      .mini-stat,
      .metric-card {
        min-height: 92px !important;
      }

      .metric-card strong {
        font-size: 21px !important;
      }

      .chart-wrap {
        height: 190px !important;
      }

      .donut-wrap {
        height: 228px !important;
      }

      .record-item {
        padding: 11px 12px !important;
        border-radius: 17px !important;
      }

      .record-title,
      .record-amount {
        font-size: 14px !important;
      }

      .delete-btn,
      .icon-btn {
        width: 32px !important;
        height: 32px !important;
        border-radius: 11px !important;
      }

      .bottom-nav {
        width: min(88vw, 430px) !important;
        padding: 6px !important;
        border-radius: 24px !important;
        bottom: calc(10px + env(safe-area-inset-bottom)) !important;
        grid-template-columns: repeat(3, minmax(0,1fr)) !important;
      }

      .nav-btn {
        min-height: 50px !important;
        border-radius: 18px !important;
        gap: 1px !important;
      }

      .nav-btn span {
        font-size: 17px !important;
      }

      .nav-btn em {
        font-size: 11px !important;
        font-weight: 850 !important;
      }

      .fab {
        width: 52px !important;
        height: 52px !important;
        border-radius: 18px !important;
        font-size: 28px !important;
        bottom: calc(82px + env(safe-area-inset-bottom)) !important;
        right: 18px !important;
      }

      .settings-group-card,
      .settings-group-card:hover,
      .settings-group-card:active,
      .settings-group-card:focus {
        min-height: 86px !important;
        padding: 14px !important;
        gap: 12px !important;
        border-radius: 24px !important;
        grid-template-columns: 40px 1fr 20px !important;
      }

      .settings-group-icon,
      .tool-icon {
        width: 40px !important;
        height: 40px !important;
        border-radius: 15px !important;
        font-size: 18px !important;
      }

      .settings-group-title {
        font-size: 17px !important;
        line-height: 1.18 !important;
        letter-spacing: -.2px !important;
      }

      .settings-group-desc {
        margin-top: 4px !important;
        font-size: 12px !important;
        line-height: 1.35 !important;
      }

      .settings-group-arrow {
        font-size: 24px !important;
      }

      .settings-group-sheet,
      .appearance-detail-panel {
        width: min(100%, 480px) !important;
        padding: 14px !important;
        border-radius: 26px !important;
      }

      .settings-group-head,
      .appearance-detail-head {
        margin-bottom: 12px !important;
      }

      .settings-group-head h2,
      .appearance-detail-head h2,
      .auth-head h2,
      .sheet-head h2 {
        font-size: 20px !important;
        letter-spacing: -.3px !important;
      }

      .settings-group-head p,
      .appearance-detail-head p {
        font-size: 12px !important;
        line-height: 1.45 !important;
      }

      .settings-group-close,
      .appearance-detail-close {
        width: 36px !important;
        height: 36px !important;
        border-radius: 13px !important;
        font-size: 22px !important;
      }

      .appearance-plus-card,
      .appearance-plus-field {
        gap: 12px !important;
      }

      .appearance-plus-field span {
        font-size: 12px !important;
      }

      .appearance-select-wrap select {
        height: 48px !important;
        border-radius: 16px !important;
        padding-left: 13px !important;
        padding-right: 48px !important;
        font-size: 14px !important;
      }

      .appearance-select-wrap b {
        right: 16px !important;
        font-size: 22px !important;
      }

      .appearance-range-row {
        padding: 8px 10px !important;
        border-radius: 16px !important;
      }

      .appearance-range-value {
        min-width: 50px !important;
        font-size: 12px !important;
      }

      .appearance-toggle {
        padding: 8px 12px !important;
        font-size: 13px !important;
      }

      .appearance-preview {
        padding: 12px !important;
        border-radius: 18px !important;
        font-size: 13px !important;
      }

      .tool-card {
        min-height: 118px !important;
        padding: 14px !important;
      }

      .tool-card h3 {
        font-size: 16px !important;
        margin: 8px 0 4px !important;
      }

      .tool-card p {
        font-size: 12px !important;
        line-height: 1.42 !important;
      }

      .summary-chip {
        padding: 12px 13px !important;
        border-radius: 18px !important;
      }

      .summary-chip strong {
        font-size: 18px !important;
      }

      .chat-bubble {
        padding: 11px 12px !important;
        border-radius: 18px !important;
        font-size: 14px !important;
        line-height: 1.48 !important;
      }

      .send-btn {
        width: 44px !important;
        height: 44px !important;
        min-width: 44px !important;
        border-radius: 16px !important;
        font-size: 18px !important;
      }

      .auth-form,
      .sheet-form {
        gap: 10px !important;
      }

      @media (max-width: 390px) {
        .app-shell {
          padding-left: 12px !important;
          padding-right: 12px !important;
        }

        .page-header h1 {
          font-size: 31px !important;
        }

        .glass-card,
        .chat-shell,
        .summary-card,
        .metric-card,
        .chart-card,
        .tool-card,
        .auth-sheet,
        .mobile-command-card,
        .tools-panel-card,
        .account-row {
          padding: 13px !important;
          border-radius: 22px !important;
        }

        .settings-group-card,
        .settings-group-card:hover,
        .settings-group-card:active,
        .settings-group-card:focus {
          min-height: 82px !important;
          padding: 13px !important;
          border-radius: 22px !important;
        }

        .settings-group-title {
          font-size: 16px !important;
        }

        .bottom-nav {
          width: min(90vw, 410px) !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installDensityPolish, { once: true });
  } else {
    installDensityPolish();
  }
})();