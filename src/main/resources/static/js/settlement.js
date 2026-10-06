(() => {
  const roomId = document.getElementById('app').dataset.roomId;
  let report = null;

  async function load() {
    try {
      report = await api(`/api/rooms/${roomId}/settlement`);
      render(report);
    } catch (err) {
      document.getElementById('room-title').textContent = err.message;
    }
  }

  function render(r) {
    document.title = `${r.title} 정산 · FitBet`;
    document.getElementById('room-title').textContent = r.title;
    document.getElementById('period').textContent = r.periodFrom
      ? `정산 기간 ${r.periodFrom} ~ ${r.periodTo}`
      : '미정산 벌금 없음';
    document.getElementById('host-name').textContent = r.hostName;
    document.getElementById('total-pool').textContent = won(r.totalPool);
    document.getElementById('guide-text').textContent = r.guideText;

    renderPenaltyRanking(r.penaltyRanking);
    renderStreakRanking(r.streakRanking);

    // 정산 완료 버튼은 방장에게만 (서버도 HOST가 아니면 403으로 막는다)
    const hostActions = document.getElementById('host-actions');
    hostActions.classList.toggle('hidden', !r.meHost);
    document.getElementById('settle-btn').disabled = r.totalPool === 0;
  }

  function renderPenaltyRanking(list) {
    const box = document.getElementById('penalty-ranking');
    box.replaceChildren();
    if (list.length === 0) {
      box.append(el('li', 'py-3 text-center text-sm text-slate-400', '🎉 미정산 벌금이 없어요'));
      return;
    }
    for (const p of list) {
      const li = el('li', 'flex items-center gap-3 py-2');
      li.append(
        el('span', 'w-8 text-center font-bold text-slate-400', `${p.rank}위`),
        el('span', 'flex-1 font-semibold', p.host ? `${p.username} (방장)` : p.username),
        el('span', 'text-xs text-slate-400', `${p.missedDays}일`),
        el('span', 'w-20 text-right font-bold text-red-500', won(p.total)),
      );
      box.append(li);
    }
  }

  function renderStreakRanking(list) {
    const box = document.getElementById('streak-ranking');
    box.replaceChildren();
    list.forEach((s, i) => {
      const li = el('li', 'flex items-center gap-3 py-2');
      li.append(
        el('span', 'w-8 text-center font-bold text-slate-400', `${i + 1}`),
        el('span', 'flex-1 font-semibold', s.username),
        el('span', 'font-bold text-orange-500', `🔥 ${s.currentStreak}일`),
        el('span', 'w-16 text-right text-xs text-slate-400', `최고 ${s.maxStreak}일`),
      );
      box.append(li);
    });
  }

  // 카톡 공유용 복사 (PRD 6.3)
  document.getElementById('copy-btn').addEventListener('click', async () => {
    const text = document.getElementById('guide-text').textContent;
    const btn = document.getElementById('copy-btn');
    try {
      await navigator.clipboard.writeText(text);
      btn.textContent = '복사됨 ✓';
      setTimeout(() => { btn.textContent = '복사하기'; }, 1500);
    } catch {
      prompt('아래 내용을 복사하세요', text);
    }
  });

  document.getElementById('settle-btn').addEventListener('click', async () => {
    if (!report?.periodTo) return;
    if (!confirm(`${report.periodFrom} ~ ${report.periodTo} 벌금 ${won(report.totalPool)}을 정산 완료 처리할까요?`)) return;
    const btn = document.getElementById('settle-btn');
    btn.disabled = true;
    try {
      // 화면에서 본 기간(periodTo)까지만 정산 → 그 사이 새로 생긴 벌금은 다음 정산으로
      const result = await api(`/api/rooms/${roomId}/settlement`, { method: 'PATCH', body: { until: report.periodTo } });
      alert(`${result.settledCount}건 정산 완료!`);
      await load();
    } catch (err) {
      alert(err.message);
      btn.disabled = false;
    }
  });

  load();
})();
