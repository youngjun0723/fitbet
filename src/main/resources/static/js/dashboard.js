(() => {
  const roomId = document.getElementById('app').dataset.roomId;
  const MAX_FILE_SIZE = 10 * 1024 * 1024;
  let timerId = null;

  // ---------------------------------------------------------------- 대시보드 렌더링
  async function load() {
    try {
      render(await api(`/api/rooms/${roomId}/dashboard`));
    } catch (err) {
      document.getElementById('room-title').textContent = err.message;
    }
  }

  function render(d) {
    document.title = `${d.room.title} · FitBet`;
    document.getElementById('room-title').textContent = d.room.title;
    document.getElementById('invite-code').textContent = d.room.inviteCode;
    document.getElementById('room-meta').textContent =
      `멤버 ${d.room.memberCount}명 · 미인증 1회 ${won(d.room.penaltyAmount)}`;
    document.getElementById('penalty-pool').textContent = won(d.penaltyPool);
    document.getElementById('my-streak').textContent = `${d.me.currentStreak}일`;
    document.getElementById('my-max-streak').textContent = `${d.me.maxStreak}일`;

    renderMissed(d.missedMembers);
    renderFeed(d.feed);
    renderCta(d.me.verifiedToday);
    startTimer(d.deadline, d.serverNow);
  }

  function renderMissed(members) {
    const box = document.getElementById('missed-list');
    box.replaceChildren();
    if (members.length === 0) {
      box.append(el('p', 'text-sm text-slate-400', '모두 인증 완료! 🎉'));
      return;
    }
    for (const m of members) {
      // 오늘 참여한 멤버는 벌금 면제라 회색으로 구분 (PRD 2.6 MVP 제안)
      const badge = m.joinedToday
        ? el('span', 'rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-500', `${m.username} · 오늘 참여`)
        : el('span', 'rounded-full bg-red-100 px-3 py-1 text-sm font-semibold text-red-600', `${m.username} 🔥${m.currentStreak}`);
      box.append(badge);
    }
  }

  function renderFeed(feed) {
    const box = document.getElementById('feed');
    box.replaceChildren();
    if (feed.length === 0) {
      box.append(el('p', 'rounded-2xl bg-white p-8 text-center text-sm text-slate-400 shadow-sm',
        '아직 오늘 인증이 없어요. 첫 번째 주인공이 되어 보세요!'));
      return;
    }
    for (const item of feed) box.append(feedCard(item));
  }

  function feedCard(item) {
    const card = el('article', 'overflow-hidden rounded-2xl bg-white shadow-sm');

    const head = el('div', 'flex items-center gap-3 p-3');
    head.append(
      el('div', 'flex h-9 w-9 items-center justify-center rounded-full bg-orange-100 font-bold text-orange-600',
        item.username.charAt(0)),
      el('p', 'flex-1 font-semibold', item.mine ? `${item.username} (나)` : item.username),
      el('span', 'text-sm font-semibold text-orange-500', `🔥 ${item.currentStreak}일`),
    );

    const img = el('img', 'aspect-square w-full bg-slate-100 object-cover');
    img.src = item.photoUrl;
    img.alt = `${item.username}의 운동 인증 사진`;
    img.loading = 'lazy';

    const body = el('div', 'p-3');
    if (item.memo) body.append(el('p', 'mb-2', item.memo));

    const actions = el('div', 'flex items-center gap-2');
    const time = el('time', 'ml-auto text-xs text-slate-400', formatTime(item.createdAt));
    renderReactions(actions, item, time);
    body.append(actions);

    card.append(head, img, body);
    return card;
  }

  // ---------------------------------------------------------------- 인정/의심 리액션 (PRD 2.5)
  const REACTIONS = [
    { type: 'APPROVE', label: '👍 인정', countKey: 'approveCount', active: 'bg-emerald-100 text-emerald-700 ring-1 ring-emerald-300' },
    { type: 'DOUBT', label: '🤔 의심', countKey: 'doubtCount', active: 'bg-amber-100 text-amber-700 ring-1 ring-amber-300' },
  ];

  function renderReactions(container, item, trailing) {
    container.replaceChildren();
    for (const r of REACTIONS) {
      const selected = item.myReaction === r.type;
      const btn = el('button',
        `rounded-full px-3 py-1 text-sm transition ${selected ? r.active : 'bg-slate-100 text-slate-600'} disabled:opacity-60`,
        `${r.label} ${item[r.countKey]}`);
      btn.type = 'button';
      btn.setAttribute('aria-pressed', String(selected));
      if (item.mine) {
        btn.disabled = true; // 본인 글에는 리액션 불가
        btn.title = '내 인증 글에는 리액션할 수 없어요';
      } else {
        btn.addEventListener('click', () => react(container, item, r.type, trailing));
      }
      container.append(btn);
    }
    container.append(trailing);
  }

  async function react(container, item, type, trailing) {
    if (item.myReaction === type) return; // 같은 걸 또 누르면 요청 생략
    container.querySelectorAll('button').forEach((b) => { b.disabled = true; });
    try {
      const result = await api(`/api/challenges/${item.logId}/reactions`, { method: 'POST', body: { type } });
      Object.assign(item, {
        approveCount: result.approveCount,
        doubtCount: result.doubtCount,
        myReaction: result.myReaction,
      });
    } catch (err) {
      alert(err.message);
    }
    renderReactions(container, item, trailing); // 전체 새로고침 없이 이 카드 버튼만 다시 그림
  }

  function renderCta(verifiedToday) {
    const cta = document.getElementById('cta');
    cta.disabled = verifiedToday;
    cta.textContent = verifiedToday ? '오늘 인증 완료 ✅' : '📸 오늘 인증하기';
  }

  function formatTime(isoLocalDateTime) {
    return isoLocalDateTime.substring(11, 16); // "2026-10-06T09:12:33" → "09:12"
  }

  // ---------------------------------------------------------------- 마감 타이머 (PRD 6.1)
  function startTimer(deadlineIso, serverNowIso) {
    clearInterval(timerId);
    const deadline = new Date(deadlineIso).getTime();
    // 휴대폰 시계가 몇 분 틀려 있어도 서버 시각 기준으로 계산하기 위한 보정값
    const skew = new Date(serverNowIso).getTime() - Date.now();
    const timer = document.getElementById('timer');
    const card = document.getElementById('timer-card');

    const tick = () => {
      const left = deadline - (Date.now() + skew);
      if (left <= 0) {
        timer.textContent = '마감!';
        clearInterval(timerId);
        return;
      }
      const s = Math.floor(left / 1000);
      timer.textContent = [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60]
        .map((n) => String(n).padStart(2, '0')).join(':');
      // 1시간 미만이면 빨간색 강조
      card.classList.toggle('bg-red-600', left < 60 * 60 * 1000);
      card.classList.toggle('bg-slate-900', left >= 60 * 60 * 1000);
    };
    tick();
    timerId = setInterval(tick, 1000);
  }

  // ---------------------------------------------------------------- 초대 링크 복사
  document.getElementById('invite-btn').addEventListener('click', async () => {
    const code = document.getElementById('invite-code').textContent;
    const link = `${location.origin}/join?code=${code}`;
    try {
      await navigator.clipboard.writeText(link);
      alert('초대 링크를 복사했어요!\n' + link);
    } catch {
      prompt('아래 링크를 복사해서 친구에게 보내세요', link);
    }
  });

  // ---------------------------------------------------------------- 업로드 모달 (PRD 6.2)
  const modal = document.getElementById('upload-modal');
  const form = document.getElementById('upload-form');
  const photoInput = document.getElementById('photo');
  const preview = document.getElementById('preview');
  const submitBtn = document.getElementById('upload-submit');
  const errorBox = document.getElementById('upload-error');

  function openModal() {
    form.reset();
    preview.classList.add('hidden');
    document.getElementById('picker-hint').classList.remove('hidden');
    document.getElementById('memo-count').textContent = '0';
    submitBtn.disabled = true;
    hideError();
    modal.classList.replace('hidden', 'flex');
  }

  function closeModal() {
    modal.classList.replace('flex', 'hidden');
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.classList.remove('hidden');
  }

  function hideError() {
    errorBox.classList.add('hidden');
  }

  function setUploading(uploading) {
    submitBtn.disabled = uploading;
    document.getElementById('upload-spinner').classList.toggle('hidden', !uploading);
    document.getElementById('upload-label').textContent = uploading ? '올리는 중…' : '인증하기';
  }

  document.getElementById('cta').addEventListener('click', openModal);
  document.getElementById('modal-close').addEventListener('click', closeModal);
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

  photoInput.addEventListener('change', () => {
    hideError();
    const file = photoInput.files[0];
    if (!file) return;
    // 서버도 검사하지만, 10MB 넘는 파일은 업로드 시간 낭비 없이 바로 알려준다
    if (file.size > MAX_FILE_SIZE) {
      showError('사진은 10MB 이하만 올릴 수 있어요.');
      submitBtn.disabled = true;
      return;
    }
    preview.src = URL.createObjectURL(file);
    preview.classList.remove('hidden');
    document.getElementById('picker-hint').classList.add('hidden');
    submitBtn.disabled = false;
  });

  form.memo.addEventListener('input', () => {
    document.getElementById('memo-count').textContent = form.memo.value.length;
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitBtn.disabled) return; // 연타 방지
    setUploading(true);
    hideError();
    try {
      const body = new FormData();
      body.append('photo', photoInput.files[0]);
      body.append('memo', form.memo.value);
      await api(`/api/rooms/${roomId}/challenges`, { method: 'POST', body });
      closeModal();
      await load();
    } catch (err) {
      showError(err.message);
      if (err.code === 'ALREADY_VERIFIED_TODAY') {
        closeModal();
        await load();
      }
    } finally {
      setUploading(false);
    }
  });

  load();
})();
