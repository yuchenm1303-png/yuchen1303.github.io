(() => {
  const isCoarsePointer = window.matchMedia('(pointer: coarse)').matches;
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const pressCancelDistance = isCoarsePointer ? 10 : 14;
  const pressableSelector = isCoarsePointer
    ? ['button', '.auth-tab', '.tag-btn', '.range-chip', '.tools-back'].join(', ')
    : [
        'button',
        '.record-item',
        '.summary-chip',
        '.summary-box',
        '.metric-card',
        '.chart-card',
        '.summary-card',
        '.tool-card',
        '.tools-back',
        '.account-row',
        '.draft-card',
        '.draft-item',
        '.auth-tab'
      ].join(', ');

  let detailChart = null;
  let detailChartTimer = null;
  let activePress = null;
  let scrollTimer = null;

  function money(value) {
    return `¥${Number(value || 0).toFixed(2)}`;
  }

  function beginPress(el, event) {
    if (!el) return;
    el.classList.remove('is-releasing');
    el.classList.add('is-pressed');
    activePress = {
      el,
      pointerId: event?.pointerId,
      startX: event?.clientX || 0,
      startY: event?.clientY || 0,
      canceled: false,
    };
  }

  function cancelPress(el) {
    if (!el) return;
    el.classList.remove('is-pressed');
    el.classList.remove('is-releasing');
    clearPressPoint(el);
    if (activePress?.el === el) activePress = null;
  }

  function endPress(el, animate = true) {
    if (!el) return;
    el.classList.remove('is-pressed');
    if (activePress?.el === el) activePress = null;
    if (prefersReducedMotion) return;
    if (!animate) return;
    el.classList.remove('is-releasing');
    void el.offsetWidth;
    el.classList.add('is-releasing');
    window.setTimeout(() => el.classList.remove('is-releasing'), 760);
  }

  function updatePressPoint(el, event) {
    if (!el || typeof event.clientX !== 'number') return;
    const rect = el.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const x = ((event.clientX - rect.left) / rect.width) * 100;
    const y = ((event.clientY - rect.top) / rect.height) * 100;
    const centerX = Math.max(-1, Math.min(1, (x - 50) / 50));
    const centerY = Math.max(-1, Math.min(1, (y - 50) / 50));
    el.style.setProperty('--press-x', `${Math.max(0, Math.min(100, x)).toFixed(1)}%`);
    el.style.setProperty('--press-y', `${Math.max(0, Math.min(100, y)).toFixed(1)}%`);
    el.style.setProperty('--press-shift-x', `${(centerX * 1.8).toFixed(2)}px`);
    el.style.setProperty('--press-shift-y', `${(Math.max(.35, centerY + 1) * 1.2).toFixed(2)}px`);
  }

  function clearPressPoint(el) {
    if (!el) return;
    window.setTimeout(() => {
      if (el.classList.contains('is-pressed')) return;
      el.style.removeProperty('--press-shift-x');
      el.style.removeProperty('--press-shift-y');
    }, 760);
  }

  function markScrolling() {
    document.body.classList.add('is-scrolling');
    if (scrollTimer) window.clearTimeout(scrollTimer);
    scrollTimer = window.setTimeout(() => {
      document.body.classList.remove('is-scrolling');
      scrollTimer = null;
    }, 140);
  }

  function pulseHaptic() {
    if (!isCoarsePointer || prefersReducedMotion || !navigator.vibrate) return;
    navigator.vibrate(8);
  }

  function ensureDetailOverlay() {
    let overlay = document.querySelector('#detailOverlay');
    if (overlay) return overlay;

    overlay = document.createElement('div');
    overlay.id = 'detailOverlay';
    overlay.className = 'detail-overlay';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.innerHTML = `
      <section class="detail-panel" role="dialog" aria-modal="true" aria-labelledby="detailTitle">
        <div class="detail-head">
          <div>
            <h2 id="detailTitle">详细信息</h2>
            <p id="detailSubtitle">轻点空白处即可收起</p>
          </div>
          <button id="detailCloseBtn" class="detail-close" type="button" aria-label="关闭">×</button>
        </div>
        <div id="detailBody"></div>
        <div id="detailSummary" class="detail-summary"></div>
      </section>
    `;
    document.body.appendChild(overlay);

    overlay.addEventListener('click', (event) => {
      if (event.target === overlay || event.target.closest('#detailCloseBtn')) closeDetailOverlay();
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && overlay.classList.contains('open')) closeDetailOverlay();
    });

    return overlay;
  }

  function clearDetailChart() {
    if (detailChartTimer) {
      window.clearTimeout(detailChartTimer);
      detailChartTimer = null;
    }
    if (detailChart) {
      detailChart.destroy();
      detailChart = null;
    }
  }

  function openDetailOverlay({ title, subtitle, bodyHtml, summaryHtml, renderChart }) {
    clearDetailChart();
    const overlay = ensureDetailOverlay();
    overlay.querySelector('#detailTitle').textContent = title;
    overlay.querySelector('#detailSubtitle').textContent = subtitle;
    overlay.querySelector('#detailBody').innerHTML = bodyHtml;
    overlay.querySelector('#detailSummary').innerHTML = summaryHtml;
    document.body.classList.add('detail-open');
    overlay.classList.add('open');
    overlay.setAttribute('aria-hidden', 'false');

    if (typeof renderChart === 'function') {
      detailChartTimer = window.setTimeout(() => {
        requestAnimationFrame(() => {
          renderChart();
          detailChartTimer = null;
        });
      }, 80);
    }
  }

  function closeDetailOverlay() {
    const overlay = ensureDetailOverlay();
    overlay.classList.remove('open');
    overlay.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('detail-open');
    clearDetailChart();
  }

  function getChartByCanvasId(id) {
    const canvas = document.querySelector(`#${id}`);
    return canvas && window.Chart ? Chart.getChart(canvas) : null;
  }

  function openTrendDetail() {
    const chart = getChartByCanvasId('trendChart');
    if (!chart) return;
    const expense = chart.data.datasets[0]?.data || [];
    const income = chart.data.datasets[1]?.data || [];
    const totalExpense = expense.reduce((sum, value) => sum + Number(value || 0), 0);
    const totalIncome = income.reduce((sum, value) => sum + Number(value || 0), 0);
    const maxExpense = Math.max(0, ...expense.map((value) => Number(value || 0)));
    const maxIncome = Math.max(0, ...income.map((value) => Number(value || 0)));
    const labels = [...chart.data.labels];
    const datasets = chart.data.datasets.map((dataset) => ({
      label: dataset.label,
      data: [...dataset.data],
      borderColor: dataset.borderColor,
      backgroundColor: dataset.backgroundColor,
      tension: dataset.tension,
      fill: dataset.fill,
    }));

    openDetailOverlay({
      title: '近 7 天趋势',
      subtitle: '收入与支出放大查看',
      bodyHtml: '<div class="detail-chart-wrap"><canvas id="detailChartCanvas"></canvas></div>',
      summaryHtml: `
        <article class="detail-chip"><span>7 天支出</span><strong>${money(totalExpense)}</strong></article>
        <article class="detail-chip"><span>7 天收入</span><strong>${money(totalIncome)}</strong></article>
        <article class="detail-chip"><span>单日最高支出</span><strong>${money(maxExpense)}</strong></article>
        <article class="detail-chip"><span>单日最高收入</span><strong>${money(maxIncome)}</strong></article>
      `,
      renderChart: () => {
        const ctx = document.querySelector('#detailChartCanvas');
        if (!ctx) return;
        detailChart = new Chart(ctx, {
          type: 'line',
          data: {
            labels,
            datasets: datasets.map((dataset) => ({
              ...dataset,
              pointRadius: 4,
              pointHoverRadius: 6,
            })),
          },
          options: {
            animation: false,
            responsive: true,
            maintainAspectRatio: false,
            interaction: { intersect: false, mode: 'index' },
            plugins: { legend: { labels: { color: '#607083' } } },
            scales: {
              y: { beginAtZero: true, ticks: { color: '#607083' }, grid: { color: 'rgba(16,32,50,.08)' } },
              x: { ticks: { color: '#607083' }, grid: { display: false } },
            },
          },
        });
      },
    });
  }

  function openCategoryDetail() {
    const chart = getChartByCanvasId('categoryChart');
    if (!chart) return;
    const data = chart.data.datasets[0]?.data || [];
    const labels = [...(chart.data.labels || [])];
    const colors = [...(chart.data.datasets[0]?.backgroundColor || [])];
    const total = data.reduce((sum, value) => sum + Number(value || 0), 0);
    const maxValue = Math.max(0, ...data.map((value) => Number(value || 0)));
    const maxIndex = data.findIndex((value) => Number(value || 0) === maxValue);
    const maxLabel = labels[maxIndex] || '暂无';

    openDetailOverlay({
      title: '支出分类',
      subtitle: '分类结构放大查看',
      bodyHtml: '<div class="detail-chart-wrap"><canvas id="detailChartCanvas"></canvas></div>',
      summaryHtml: `
        <article class="detail-chip"><span>分类总支出</span><strong>${money(total)}</strong></article>
        <article class="detail-chip"><span>最大分类</span><strong>${maxLabel}</strong></article>
        <article class="detail-chip"><span>最大分类金额</span><strong>${money(maxValue)}</strong></article>
        <article class="detail-chip"><span>分类数量</span><strong>${labels.length} 类</strong></article>
      `,
      renderChart: () => {
        const ctx = document.querySelector('#detailChartCanvas');
        if (!ctx) return;
        detailChart = new Chart(ctx, {
          type: 'doughnut',
          data: {
            labels,
            datasets: [{
              data: [...data],
              backgroundColor: colors,
              borderWidth: 0,
            }],
          },
          options: {
            animation: false,
            responsive: true,
            maintainAspectRatio: false,
            cutout: '60%',
            plugins: { legend: { position: 'bottom', labels: { color: '#607083', boxWidth: 12, padding: 16 } } },
          },
        });
      },
    });
  }

  function openSummaryDetail() {
    const balance = document.querySelector('#summaryBalance')?.textContent || '¥0.00';
    const income = document.querySelector('#summaryIncome')?.textContent || '+¥0.00';
    const expense = document.querySelector('#summaryExpense')?.textContent || '-¥0.00';
    const budget = document.querySelector('#budgetBadge')?.textContent || '¥0.00';
    const budgetText = document.querySelector('#budgetText')?.textContent || '预算已使用 0%';

    openDetailOverlay({
      title: '周期总览',
      subtitle: '本期收支与预算详情',
      bodyHtml: `
        <div class="detail-summary">
          <article class="detail-chip"><span>当前结余</span><strong>${balance}</strong></article>
          <article class="detail-chip"><span>预算额度</span><strong>${budget}</strong></article>
          <article class="detail-chip"><span>本期收入</span><strong>${income}</strong></article>
          <article class="detail-chip"><span>本期支出</span><strong>${expense}</strong></article>
        </div>
      `,
      summaryHtml: `
        <article class="detail-chip"><span>预算状态</span><strong>${budgetText}</strong></article>
        <article class="detail-chip"><span>提示</span><strong>继续轻点查看</strong></article>
      `,
    });
  }

  function markExpandableCards() {
    const trendCard = document.querySelector('#trendChart')?.closest('.chart-card');
    const categoryCard = document.querySelector('#categoryChart')?.closest('.chart-card');
    const summaryCard = document.querySelector('.summary-card');

    [trendCard, categoryCard, summaryCard].filter(Boolean).forEach((card) => {
      card.classList.add('expandable-card');
      card.tabIndex = 0;
      card.setAttribute('role', 'button');
    });
  }

  function bindExpandableCards() {
    document.addEventListener('click', (event) => {
      const chartCard = event.target.closest('.chart-card');
      if (chartCard) {
        if (chartCard.querySelector('#trendChart')) openTrendDetail();
        if (chartCard.querySelector('#categoryChart')) openCategoryDetail();
      }
      if (event.target.closest('.summary-card')) openSummaryDetail();
    });

    document.addEventListener('keydown', (event) => {
      const card = event.target.closest?.('.expandable-card');
      if (!card || !['Enter', ' '].includes(event.key)) return;
      event.preventDefault();
      card.click();
    });
  }

  function decorateRecordItems() {
    document.querySelectorAll('.record-item').forEach((item) => {
      if (item.querySelector('.record-extra')) return;
      const title = item.querySelector('.record-title')?.textContent || '未命名账单';
      const meta = item.querySelector('.record-meta')?.textContent || '';
      const amount = item.querySelector('.record-amount')?.textContent || '';
      const extra = document.createElement('div');
      extra.className = 'record-extra';
      extra.textContent = `账单：${title} ｜ ${meta} ｜ 金额 ${amount}`;
      item.appendChild(extra);
      item.tabIndex = 0;
      item.setAttribute('role', 'button');
    });
  }

  function bindRecordExpansion() {
    document.addEventListener('click', (event) => {
      const item = event.target.closest('.record-item');
      if (!item || event.target.closest('.delete-btn')) return;
      item.classList.toggle('expanded');
    });

    document.addEventListener('keydown', (event) => {
      const item = event.target.closest?.('.record-item');
      if (!item || !['Enter', ' '].includes(event.key)) return;
      event.preventDefault();
      item.classList.toggle('expanded');
    });

    const list = document.querySelector('#recordList');
    if (list) {
      const observer = new MutationObserver(() => decorateRecordItems());
      observer.observe(list, { childList: true, subtree: true });
    }
  }

  function bindPressFeedback() {
    document.addEventListener('pointerdown', (event) => {
      if (event.button !== undefined && event.button > 0) return;
      const el = event.target.closest(pressableSelector);
      updatePressPoint(el, event);
      beginPress(el, event);
      pulseHaptic();
    }, { passive: true });

    document.addEventListener('pointermove', (event) => {
      const el = activePress?.el;
      if (!el?.classList.contains('is-pressed')) return;
      if (activePress.pointerId !== undefined && event.pointerId !== activePress.pointerId) return;
      const dx = event.clientX - activePress.startX;
      const dy = event.clientY - activePress.startY;
      if (Math.hypot(dx, dy) > pressCancelDistance) {
        activePress.canceled = true;
        markScrolling();
        cancelPress(el);
        return;
      }
      updatePressPoint(el, event);
    }, { passive: true });

    ['pointerup', 'pointercancel'].forEach((type) => {
      document.addEventListener(type, (event) => {
        const el = activePress?.el || event.target.closest?.(pressableSelector);
        const animate = type === 'pointerup' && !activePress?.canceled && !document.body.classList.contains('is-scrolling');
        updatePressPoint(el, event);
        endPress(el, animate);
        clearPressPoint(el);
      }, { passive: true });
    });

    document.addEventListener('pointerleave', (event) => {
      const el = activePress?.el || event.target.closest?.(pressableSelector);
      cancelPress(el);
    }, true);

    document.addEventListener('scroll', () => {
      markScrolling();
      if (activePress?.el) cancelPress(activePress.el);
    }, { passive: true, capture: true });
  }

  function init() {
    markExpandableCards();
    decorateRecordItems();
    bindExpandableCards();
    bindRecordExpansion();
    bindPressFeedback();
    ensureDetailOverlay();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
