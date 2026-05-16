(() => {
  'use strict';

  // Deprecated compatibility stub.
  // Mobile command card layout now lives in chat-actions.js as the single source of truth.
  document.getElementById('chat-card-polish-style')?.remove();
  document.querySelectorAll('[data-compact-command-card]').forEach((node) => {
    node.removeAttribute('data-compact-command-card');
  });
})();
