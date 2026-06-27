'use strict';

renderSectors = function () {
  const sectors = state.snapshot.sectors;
  if (!sectors.length) {
    return sectionHeading('行业板块', '真实行业涨幅、涨跌家数、资金与领涨股')
      + '<div class="empty-line">板块数据暂不可用</div>';
  }
  return sectionHeading('行业板块', '点击进入板块详情 · 真实走势、成分股与资金状态')
    + sectors.slice(0, 8).map(sector => `<button type="button" class="sector-row" data-sector-code="${escapeHtml(sector.code)}" aria-label="查看${escapeHtml(sector.name)}板块详情"><span class="sector-copy"><strong>${escapeHtml(sector.name)}</strong><span>涨 ${escapeHtml(sector.upCount ?? '--')} · 跌 ${escapeHtml(sector.downCount ?? '--')}${sector.leaderName ? ` · 领涨 ${escapeHtml(sector.leaderName)}` : ''}</span></span><span class="sector-flow ${flowTone(sector.mainInflow)}">${escapeHtml(sector.mainInflow || sector.amount || '--')}</span><span class="sector-change ${toneClass(!sector.changePercent.startsWith('-'))}">${escapeHtml(sector.changePercent)}</span></button>`).join('');
};

function openSectorDetail(code) {
  const query = text(code, '');
  if (!query) return;
  location.href = `./stock-sector-web-preview.html?query=${encodeURIComponent(query)}`;
}

document.addEventListener('click', event => {
  const row = event.target.closest('.sector-row[data-sector-code]');
  if (!row) return;
  openSectorDetail(row.dataset.sectorCode);
});

renderToolContent();
