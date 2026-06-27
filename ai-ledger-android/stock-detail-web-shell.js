'use strict';

(function installDetailShell(){
  const params = new URLSearchParams(location.search);
  const query = params.get('query')?.trim();
  const queryInput = document.getElementById('query');
  if (query && queryInput) queryInput.value = query;

  const backButton = document.getElementById('backButton');
  backButton?.addEventListener('click', () => {
    if (history.length > 1) history.back();
    else location.href = './stock-home-web-preview.html';
  });

  const updateClock = () => {
    const clock = document.getElementById('clock');
    if (!clock) return;
    clock.textContent = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(new Date());
  };
  updateClock();
  setInterval(updateClock, 30000);
})();
