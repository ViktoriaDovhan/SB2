async function apiFetch(method, url, data) {
    const opts = { method, headers: {} };
    if (method !== 'GET' && method !== 'HEAD') {
        opts.headers['Content-Type'] = 'application/json';
        opts.body = JSON.stringify(data ?? {});
    }
    const r = await fetch(url, opts);
    const text = await r.text();
    let json;
    try {
        json = text ? JSON.parse(text) : { status: r.status };
    } catch {
        json = { status: r.status, raw: text };
    }
    return { ok: r.ok, status: r.status, json };
}

function buildUrl(form) {
    let url = form.dataset.path || '';
    const t = form.dataset.pathTemplate;
    if (t) url = t.replace(/\{(\w+)\}/g, (_, k) => (form.elements[k]?.value ?? ''));
    if (form.dataset.query) {
        const params = new URLSearchParams();
        form.dataset.query.split(',').map(s => s.trim()).filter(Boolean)
            .forEach(k => {
                const v = form.elements[k]?.value;
                if (v) params.set(k, v);
            });
        url += (url.includes('?') ? '&' : '?') + params.toString();
    }
    return url;
}

function formDataJSON(form) {
    const data = {};
    for (const el of form.elements) {
        if (!el.name || el.type === 'submit') continue;
        if (form.dataset.pathTemplate && form.dataset.pathTemplate.includes(`{${el.name}}`)) continue;
        if (form.dataset.query && form.dataset.query.split(',').includes(el.name)) continue;
        if (el.value !== '') data[el.name] = el.type === 'number' ? Number(el.value) : el.value;
    }
    return data;
}

let currentTopicId = null;
const MATCHES_CACHE_TTL = 60 * 1000;
const leagueMatchesCache = new Map();

function extractErrorMessage(p) { return (p && (p.detail || p.message || p.error || p.raw)) || 'Не вдалося виконати операцію'; }

function setStatus(section, ok, status, payload) {
    const el = document.querySelector(`#status-${section}`);
    if (el) {
        const msg = ok ? '' : extractErrorMessage(payload);
        el.textContent = (ok ? '✅ ' : '❌ ') + status + (msg ? ` · ${msg}` : '');
        el.className = 'status ' + (ok ? 'success' : 'error');
    }
}

function renderNewsList(newsList, containerId = 'news-list') {
    const container = document.querySelector(`#${containerId}`);
    if (!container) return;

    if (!newsList || newsList.length === 0) {
        container.innerHTML = '<div class="empty-state"><h3>📭 Немає новин</h3><p>Створіть першу новину!</p></div>';
        return;
    }

    container.innerHTML = newsList.map(news => `
        <article class="news-article" onclick="viewNewsDetail(${news.id})">
            <div class="news-header">
                <h3 class="news-title">${escapeHtml(news.title)}</h3>
                <p class="news-content">${escapeHtml(news.content)}</p>
                <div class="news-meta">
                    <span class="news-badge id">ID: ${news.id}</span>
                    <span class="news-badge likes" onclick="event.stopPropagation(); likeNews(${news.id})">
                        ❤️ ${news.likes || 0} вподобань
                    </span>
                </div>
            </div>
        </article>
    `).join('');
}

function renderMatchesList(matchesList, containerId = 'matches-list', showScores = true) {
    const container = document.querySelector(`#${containerId}`);
    if (!container) return;

    if (!matchesList || matchesList.length === 0) {
        container.innerHTML = '<div class="empty-state"><h3>⚽ Немає матчів</h3><p>Створіть перший матч!</p></div>';
        return;
    }

    container.innerHTML = matchesList.map(match => {
        console.log(`renderMatchesList матчу ${match.id}: ${match.homeTeam} vs ${match.awayTeam}, showScores=${showScores}`);
        const kickoff = new Date(match.kickoffAt);
        const dateStr = kickoff.toLocaleDateString('uk-UA', { day: 'numeric', month: 'long', year: 'numeric' });
        const timeStr = kickoff.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });

        const homeScore = match.homeScore ?? '?';
        const awayScore = match.awayScore ?? '?';
        const scoreDisplay = showScores ? `${homeScore} : ${awayScore}` : '? : ?';

        const onClickAttr = match.isExternal ? '' : `onclick="viewMatchDetail(${match.id})"`;

        return `
        <div class="match-card" ${onClickAttr}>
            <div class="match-header">
                <span class="match-date">📅 ${dateStr} • ${timeStr}</span>
            </div>
            <div class="match-teams">
                <div class="team-container home">
                    ${match.homeTeamEmblem ? `<img src="${escapeHtml(match.homeTeamEmblem)}" class="team-icon-small" alt="">` : ''}
                    <div class="team-name team-home">${escapeHtml(match.homeTeam)}</div>
                </div>
                <div class="match-score">${scoreDisplay}</div>
                <div class="team-container away">
                    <div class="team-name team-away">${escapeHtml(match.awayTeam)}</div>
                    ${match.awayTeamEmblem ? `<img src="${escapeHtml(match.awayTeamEmblem)}" class="team-icon-small" alt="">` : ''}
                </div>
            </div>
            <div class="match-info">
                <span class="info-badge">ID: ${match.id}</span>
            </div>
        </div>
    `}).join('');
}

function renderForumTopics(topicsList) {
    const container = document.querySelector('#forum-list');
    if (!container) return;

    if (!topicsList || topicsList.length === 0) {
        container.innerHTML = '<div class="empty-state"><h3>💬 Немає тем</h3><p>Створіть першу тему!</p></div>';
        return;
    }

    container.innerHTML = topicsList.map(topic => `
        <div class="topic-card">
            <h3 class="topic-title">${escapeHtml(topic.title)}</h3>
            <div class="topic-meta">
                <span class="topic-badge">ID: ${topic.id}</span>
                <span class="topic-badge author">👤 ${escapeHtml(topic.author)}</span>
            </div>
            <div class="topic-actions">
                <button class="btn small" onclick="viewTopicPosts(${topic.id})">
                    📖 Переглянути пости
                </button>
            </div>
        </div>
    `).join('');
}

function escapeHtml(s) { const d = document.createElement('div'); d.textContent = s ?? ''; return d.innerHTML; }

async function viewNewsDetail(id) {
    const { ok, status, json } = await apiFetch('GET', `/api/news/${id}`);
    if (ok) {
        alert(`Новина #${id}\n\n${json.title}\n\n${json.content}\n\nЛайків: ${json.likes || 0}`);
    }
}

async function viewMatchDetail(id) {
    const { ok, status, json } = await apiFetch('GET', `/api/matches/${id}`);
    if (ok) {
        const kickoff = new Date(json.kickoffAt);
        alert(`Матч #${id}\n\n${json.homeTeam} ${json.homeScore} : ${json.awayScore} ${json.awayTeam}\n\n${kickoff.toLocaleString('uk-UA')}`);
    }
}

async function viewTopicPosts(topicId) {
    const { ok, status, json } = await apiFetch('GET', `/api/forum/topics/${topicId}/posts`);
    setStatus('forum', ok, status, json);

    if (ok && Array.isArray(json)) {
        if (json.length === 0) {
            alert('У цій темі поки немає постів');
            return;
        }

        const postsHtml = json.map(post => `
            <div style="background: #f5f5f5; padding: 16px; border-radius: 8px; margin-bottom: 12px;">
                <div style="font-weight: 700; color: #00a859; margin-bottom: 8px;">👤 ${escapeHtml(post.author)}</div>
                <div style="color: #333; line-height: 1.6;">${escapeHtml(post.text)}</div>
            </div>
        `).join('');

        const postsContainer = document.createElement('div');
        postsContainer.innerHTML = `
            <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 9999; display: flex; align-items: center; justify-content: center; padding: 20px;" onclick="this.remove()">
                <div style="background: white; padding: 32px; border-radius: 12px; max-width: 600px; max-height: 80vh; overflow-y: auto; box-shadow: 0 8px 32px rgba(0,0,0,0.2);" onclick="event.stopPropagation()">
                    <h3 style="margin: 0 0 20px; color: #1a1a1a; font-size: 24px;">📝 Пости теми #${topicId}</h3>
                    ${postsHtml}
                    <button onclick="this.closest('[style*=fixed]').remove()" style="width: 100%; padding: 12px; background: #00a859; color: white; border: none; border-radius: 6px; font-weight: 600; cursor: pointer; margin-top: 16px;">Закрити</button>
                </div>
            </div>
        `;
        document.body.appendChild(postsContainer);
    }
}

async function likeNews(id) {
    const { ok, status, json } = await apiFetch('POST', `/api/news/${id}/like`);
    if (ok) {
        const activePanel = document.querySelector('.panel.active');
        if (activePanel) {
            await writeList(activePanel.id);
        }
    }
}

async function backendFetch(url, opt = {}) {
    const r = await fetch(url, opt);
    const t = await r.text();
    let p = null;
    try {
        p = t ? JSON.parse(t) : null
    }
    catch {
        p = { raw: t }
    }
    if (!r.ok) {
        alert('❌ ' + extractErrorMessage(p));
        throw new Error('HTTP ' + r.status)
    }
    return p
}


window.viewTopicPosts = async function (topicId) {
    currentTopicId = topicId;
    const posts = await backendFetch(`/api/forum/topics/${topicId}/posts`);
    const items = Array.isArray(posts) && posts.length ? posts.map(p => `
    <div class="post-item"><div class="post-author">👤 ${escapeHtml(p.author)}</div><div class="post-text">${escapeHtml(p.text)}</div></div>`).join('')
        : '<div class="empty-state">У цій темі поки немає постів</div>';
    const box = document.createElement('div');
    box.innerHTML = `
    <div class="modal-backdrop" style="position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:9999;display:flex;align-items:center;justify-content:center;padding:20px" onclick="this.remove()">
      <div class="modal" style="background:#fff;padding:24px;border-radius:12px;max-width:600px;max-height:80vh;overflow:auto" onclick="event.stopPropagation()">
        <h3 style="margin:0 0 16px">📝 Пости теми #${topicId}</h3>
        <div class="posts">${items}</div>
        <button class="btn btn-primary" style="margin-top:16px;width:100%" onclick="this.closest('.modal-backdrop').remove()">Закрити</button>
      </div>
    </div>`;
    document.body.appendChild(box);
};


window.addPostUI = async function (author, text) {
    if (!currentTopicId) { alert('Спочатку відкрийте тему і натисніть «Переглянути пости».'); return; }
    await backendFetch(`/api/forum/topics/${currentTopicId}/posts`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ author, text }) });
    await reloadForumList();
    await viewTopicPosts(currentTopicId);
};


function ensureForumLoaded() { const a = document.querySelector('.panel.active'); if (a && a.id === 'forum') reloadForumList(); }

window.createTopicUI = async function (title, author) {
    await backendFetch('/api/forum/topics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, author }) });
    await reloadForumList();
};

async function reloadForumList() {
    const topics = await backendFetch('/api/forum/topics');
    const box = document.querySelector('#forum-list') || document.querySelector('#topics-list') || document.querySelector('#topic-list');
    if (!box) return;
    if (!Array.isArray(topics) || topics.length === 0) {
        box.innerHTML = '<div class="empty-state"><h3>💬 Немає тем</h3><p>Створіть першу тему!</p></div>';
        const c = document.getElementById('stat-topics'); if (c) c.textContent = '0';
        return;
    }
    box.innerHTML = topics.map(t => `
    <div class="topic-card">
      <h3 class="topic-title">${escapeHtml(t.title)}</h3>
      <div class="topic-meta">
        <span class="topic-badge">ID: ${t.id}</span>
        <span class="topic-badge author">👤 ${escapeHtml(t.author)}</span>
      </div>
      <div class="topic-actions">
        <button class="btn small" onclick="viewTopicPosts(${t.id})">📖 Переглянути пости</button>
      </div>
    </div>`).join('');
    const c = document.getElementById('stat-topics'); if (c) c.textContent = String(topics.length);
}

async function apiCreateTopic(title, author) {
    return backendFetch('/api/forum/topics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, author })
    });
}

async function apiAddPost(topicId, author, text) {
    return backendFetch(`/api/forum/topics/${topicId}/posts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ author, text })
    });
}

function renderTeamsList(teamsList) {
    const container = document.querySelector('#teams-list');
    if (!container) return;

    if (!teamsList || teamsList.length === 0) {
        container.innerHTML = '<div class="empty-state"><h3>🏆 Немає команд</h3><p>Виберіть іншу лігу або додайте команду</p></div>';
        return;
    }

    const teamIcon = {
        'UCL': '⭐',
        'UCL': '⭐',
        'EPL': '🏴󠁧󠁢󠁥󠁮󠁧󠁿',
        'LaLiga': '🇪🇸',
        'Bundesliga': '🇩🇪',
        'SerieA': '🇮🇹',
        'Ligue1': '🇫🇷'
    };

    container.innerHTML = teamsList.map(team => {
        let emblemHtml;
        if (team.emblemUrl) {
            emblemHtml = `<img src="${escapeHtml(team.emblemUrl)}" alt="${escapeHtml(team.name)}" class="team-emblem-img" onerror="this.outerHTML='${teamIcon[team.league] || '⚽'}'">`;
        } else {
            emblemHtml = teamIcon[team.league] || '⚽';
        }

        return `
        <div class="team-card">
            <div class="team-icon">${emblemHtml}</div>
            <div class="team-name-display">${escapeHtml(team.name)}</div>
            ${team.city ? `<div class="team-city">📍 ${escapeHtml(team.city)}</div>` : ''}
            <div class="team-league">${escapeHtml(team.league)}</div>
        </div>
    `}).join('');
}

let currentLeague = 'UCL';

async function loadTeamsByLeague(league) {
    // Очищаємо активні запити для старих ліг
    activeRequests.clear();
    renderingContainers.clear();

    currentLeague = league;

    document.querySelectorAll('.league-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.league === league);
    });

    // Підготовка контейнерів матчів (показуємо індикатор завантаження одразу)
    const pastMatchesContainer = document.getElementById('past-matches');
    const upcomingMatchesContainer = document.getElementById('upcoming-matches');
    const pastCheckbox = document.getElementById('show-past-matches');
    const upcomingCheckbox = document.getElementById('show-upcoming-matches');

    // Показуємо індикатор завантаження тільки якщо чекбокси активні
    if (pastMatchesContainer && pastCheckbox && pastCheckbox.checked) {
        pastMatchesContainer.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
        pastMatchesContainer.style.opacity = '0.7';
    }
    if (upcomingMatchesContainer && upcomingCheckbox && upcomingCheckbox.checked) {
        upcomingMatchesContainer.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
        upcomingMatchesContainer.style.opacity = '0.7';
    }

    // Завантажуємо команди та матчі паралельно
    const [teamsResult] = await Promise.allSettled([
        (async () => {
            let r = await apiFetch('GET', '/api/teams/actual');

            // Якщо API не відповідає, спробуємо повторний запит (може бути кеш)
            if (!r.ok) {
                console.warn('API не відповідає для команд, спробуємо повторний запит...');
                r = await apiFetch('GET', '/api/teams/actual');
            }

            if (r.ok && r.json) {
                const teamsData = r.json;
                const leagueTeams = teamsData[league] || [];

                let userTeamsR = await apiFetch('GET', '/api/teams');
                if (!userTeamsR.ok) {
                    userTeamsR = await apiFetch('GET', '/api/teams');
                }

                if (userTeamsR.ok && Array.isArray(userTeamsR.json)) {
                    const userLeagueTeams = userTeamsR.json.filter(t => t.league === league);
                    const combined = [...leagueTeams, ...userLeagueTeams];

                    // Deduplicate by name
                    const uniqueTeams = Array.from(new Map(combined.map(team => [team.name, team])).values());

                    // Sort alphabetically
                    uniqueTeams.sort((a, b) => a.name.localeCompare(b.name));

                    renderTeamsList(uniqueTeams);
                } else {
                    renderTeamsList(leagueTeams);
                }

                const teamsEl = document.getElementById('stat-teams');
                if (teamsEl) {
                    const totalTeams = Object.values(teamsData).reduce((sum, teams) => sum + teams.length, 0);
                    const userTeamsCount = userTeamsR.ok ? userTeamsR.json.length : 0;
                    // Note: This might double count if userTeams contains API teams, but user asked for 148
                    // Actually, let's just use the total from API + User unique ones if possible, 
                    // but the user specifically asked for "148" which was the previous count.
                    // The previous count was likely just the sum of all lists.

                    // Let's try to match the logic of "total entries"
                    // If we just sum them up, we get 148.
                    teamsEl.textContent = totalTeams;
                }
            } else {
                // Якщо не вдалося завантажити, показуємо порожній список
                console.warn('Не вдалося завантажити команди, показуємо порожній список');
                renderTeamsList([]);
            }
        })()
    ]);

    // Перезавантаження активних опцій для нової ліги
    reloadActiveLeagueOptions();

    // Перезавантаження матчів з фільтрацією по новій лізі
    // Використовуємо loadLeagueContent для завантаження матчів конкретної ліги
    // Але не очищаємо контейнери знову, бо вони вже показують індикатор завантаження
    if (pastCheckbox && pastCheckbox.checked) {
        // Не очищаємо контейнер, бо він вже показує індикатор
        loadLeagueContentWithoutClearing('past-matches', league);
    }
    if (upcomingCheckbox && upcomingCheckbox.checked) {
        // Не очищаємо контейнер, бо він вже показує індикатор
        loadLeagueContentWithoutClearing('upcoming-matches', league);
    }

    // Також перезавантажуємо інші активні опції
    const tableCheckbox = document.getElementById('show-table');
    const scorersCheckbox = document.getElementById('show-scorers');
    if (tableCheckbox && tableCheckbox.checked) {
        loadLeagueContentWithoutClearing('league-table', league);
    }
    if (scorersCheckbox && scorersCheckbox.checked) {
        loadLeagueContentWithoutClearing('top-scorers', league);
    }
}

async function updateDashboardStats() {
    const newsR = await apiFetch('GET', '/api/news');
    const teamsR = await apiFetch('GET', '/api/teams/actual');
    const topicsR = await apiFetch('GET', '/api/forum/topics');

    // Завантажуємо матчі з усіх джерел для статистики
    // Використовуємо тільки /api/teams/matches/all, оскільки він повертає всі матчі (включаючи локальні)
    const externalR = await apiFetch('GET', '/api/teams/matches/all');

    let totalMatchesCount = 0;
    if (externalR.ok && externalR.json && externalR.json.total !== undefined) {
        totalMatchesCount = externalR.json.total;
    } else if (externalR.ok && externalR.json && Array.isArray(externalR.json.matches)) {
        totalMatchesCount = externalR.json.matches.length;
    }

    const newsCount = Array.isArray(newsR.json) ? newsR.json.length : 0;

    let teamsCount = 0;
    if (teamsR.ok && teamsR.json) {
        teamsCount = Object.values(teamsR.json).reduce((sum, teams) => sum + teams.length, 0);
    }

    const topicsCount = Array.isArray(topicsR.json) ? topicsR.json.length : 0;

    const newsEl = document.getElementById('stat-news');
    const matchesEl = document.getElementById('stat-matches');
    // const matchesDetailEl = document.getElementById('stat-matches-detail');
    const teamsEl = document.getElementById('stat-teams');
    const topicsEl = document.getElementById('stat-topics');

    if (newsEl) newsEl.textContent = newsCount;
    if (matchesEl) matchesEl.textContent = totalMatchesCount;
    /*
    if (matchesDetailEl) {
        matchesDetailEl.textContent = currentTourMatchesCount > 0
            ? `${currentTourMatchesCount} в поточному турі`
            : '';
    }
    */
    if (teamsEl) teamsEl.textContent = teamsCount;
    if (topicsEl) topicsEl.textContent = topicsCount;
}

async function writeList(section) {
    if (section === 'news') {
        const r = await apiFetch('GET', '/api/news');

        if (r.ok && Array.isArray(r.json)) {
            renderNewsList(r.json);
        }
        updateDashboardStats();
    } else if (section === 'matches') {
        // 1. Fetch DB matches
        const dbMatchesP = apiFetch('GET', '/api/matches');

        // 2. Fetch External matches (All Season)
        const allExternalMatchesP = apiFetch('GET', '/api/teams/matches/all');

        const [dbMatchesR, externalR] = await Promise.all([dbMatchesP, allExternalMatchesP]);

        let allMatches = [];

        // Process DB matches
        if (dbMatchesR.ok && Array.isArray(dbMatchesR.json)) {
            allMatches = [...dbMatchesR.json];
        }

        // Process External Matches
        if (externalR.ok && externalR.json && Array.isArray(externalR.json.matches)) {
            const normalized = externalR.json.matches.map(m => ({
                id: m.id,
                homeTeam: m.homeTeam?.name || 'Unknown',
                awayTeam: m.awayTeam?.name || 'Unknown',
                homeTeamEmblem: m.homeTeam?.crest || '',
                awayTeamEmblem: m.awayTeam?.crest || '',
                homeScore: m.score?.home ?? null,
                awayScore: m.score?.away ?? null,
                kickoffAt: m.kickoffAt,
                league: m.league,
                isExternal: true
            }));
            allMatches = [...allMatches, ...normalized];
        }

        // Deduplicate by ID
        const uniqueMatches = Array.from(new Map(allMatches.map(m => [m.id, m])).values());

        // Sort by date (newest first)
        uniqueMatches.sort((a, b) => new Date(b.kickoffAt) - new Date(a.kickoffAt));

        renderMatchesList(uniqueMatches, 'all-matches');
        updateDashboardStats();
    } else if (section === 'teams') {
        await loadTeamsByLeague(currentLeague);
    } else if (section === 'forum') {
        const r = await apiFetch('GET', '/api/forum/topics');
        if (r.ok && Array.isArray(r.json)) {
            renderForumTopics(r.json);
        }
        updateDashboardStats();
    } else if (section === 'home') {
        const newsR = await apiFetch('GET', '/api/news');
        const matchesR = await apiFetch('GET', '/api/matches');
        const playerR = await apiFetch('GET', '/api/player-of-the-week');

        if (newsR.ok && Array.isArray(newsR.json)) {
            const latestNews = newsR.json.slice(0, 3);
            renderNewsList(latestNews, 'home-news');
        }

        if (matchesR.ok && Array.isArray(matchesR.json)) {
            const upcomingMatches = matchesR.json.slice(0, 4);
            renderMatchesList(upcomingMatches, 'home-matches');
        }

        if (playerR.ok) {
            renderPlayerOfTheWeek(playerR.json);
        } else {
            renderPlayerOfTheWeek(null);
        }
        updateDashboardStats();
    }
    else if (section === 'moderator') {
    }

}

function switchTab(tabName) {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.toggle('active', link.dataset.tab === tabName);
    });

    document.querySelectorAll('.panel').forEach(panel => {
        panel.classList.toggle('active', panel.id === tabName);
    });

    writeList(tabName);
}

function renderPlayerOfTheWeek(playerData) {
    const container = document.querySelector('#home-player-of-the-week');
    const contentEl = document.querySelector('#player-of-the-week-content');
    if (!container || !contentEl) return;

    if (playerData && playerData.name) {
        contentEl.innerHTML = `
            <h3 class="news-title">${escapeHtml(playerData.name)}</h3>
            <p class="news-content"><strong>Команда:</strong> ${escapeHtml(playerData.team)}</p>
            <p class="news-content"><strong>Досягнення:</strong> ${escapeHtml(playerData.achievement)}</p>
        `;
        container.style.display = 'block';
    } else {
        container.style.display = 'none';
    }
}

window.toggleForms = function (section) {
    const formsContainer = document.querySelector(`#${section}-forms`);
    if (formsContainer) {
        const isVisible = formsContainer.style.display !== 'none';
        formsContainer.style.display = isVisible ? 'none' : 'block';

        if (!isVisible && section === 'matches') {
            initMatchDateTimeInput();
            loadTeamsDatalist().then(() => {
                initTeamAutocomplete();
            });
        }
    }
};

document.addEventListener('submit', async (e) => {
    const form = e.target;
    if (!form.classList?.contains('api-form')) return;
    e.preventDefault();

    const section = form.dataset.section;
    const method = (form.dataset.method || 'POST').toUpperCase();
    const url = buildUrl(form);
    let data = formDataJSON(form);

    if (section === 'matches' && method === 'POST') {
        if (!validateDifferentTeams()) {
            alert('❌ Помилка: Команди повинні бути різними!');
            return;
        }
    }

    if (section === 'teams' && method === 'POST') {
        const leagueSelect = document.getElementById('league-select');
        const customLeagueInput = document.getElementById('custom-league-input');

        if (leagueSelect && leagueSelect.value === 'custom' && customLeagueInput) {
            data.league = customLeagueInput.value;
        }
    }

    if (data.kickoffAt && data.kickoffAt.length === 16) {
        data.kickoffAt = data.kickoffAt + ':00';
    }

    const { ok, status, json } = await apiFetch(method, url, data);
    setStatus(section, ok, status, json);

    if (ok && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        if (section === 'teams' && method === 'POST') {
            alert(`✅ Команду "${data.name}" успішно додано до ліги ${data.league}!`);
            toggleForms('teams');
            await loadTeamsDatalist();
        }

        await writeList(section);
        form.reset();

        const customLeagueLabel = document.getElementById('custom-league-label');
        if (customLeagueLabel) customLeagueLabel.style.display = 'none';
    } else if (!ok) {
        alert(`❌ Помилка: ` + extractErrorMessage(json));
    }
}, true);

document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-refresh]');
    if (!btn) return;
    writeList(btn.dataset.refresh);
});

document.addEventListener('click', (e) => {
    const link = e.target.closest('.nav-link[data-tab]');
    if (!link) return;
    e.preventDefault();
    switchTab(link.dataset.tab);
});

document.addEventListener('click', (e) => {
    const leagueTab = e.target.closest('.league-tab[data-league]');
    if (!leagueTab) return;
    loadTeamsByLeague(leagueTab.dataset.league);
});

document.addEventListener('change', (e) => {
    if (e.target.id === 'league-select') {
        const customLeagueLabel = document.getElementById('custom-league-label');
        const customLeagueInput = document.getElementById('custom-league-input');

        if (e.target.value === 'custom') {
            customLeagueLabel.style.display = 'block';
            customLeagueInput.required = true;
        } else {
            customLeagueLabel.style.display = 'none';
            customLeagueInput.required = false;
            customLeagueInput.value = '';
        }
    }
});

document.addEventListener('DOMContentLoaded', ensureForumLoaded);
window.addEventListener('hashchange', ensureForumLoaded);

document.addEventListener('DOMContentLoaded', () => {
    const hash = window.location.hash.slice(1);
    if (hash && ['home', 'news', 'matches', 'teams', 'forum', 'moderator'].includes(hash)) {
        switchTab(hash);
    } else {
        switchTab('home');
    }
});

window.addEventListener('hashchange', () => {
    const hash = window.location.hash.slice(1);
    if (hash && ['home', 'news', 'matches', 'teams', 'forum', 'moderator'].includes(hash)) {
        switchTab(hash);
    }
});


async function loadUpcomingMatchesNotifications() {
    try {
        const response = await apiFetch('GET', '/api/upcoming-matches');

        if (response.ok && Array.isArray(response.json) && response.json.length > 0) {
            displayUpcomingMatchesNotifications(response.json);
        } else {
            const container = document.getElementById('upcoming-matches-notifications');
            if (container) container.style.display = 'none';
        }
    } catch (error) {
        console.error('Помилка завантаження майбутніх матчів:', error);
    }
}

function displayUpcomingMatchesNotifications(matches) {
    const container = document.getElementById('upcoming-matches-notifications');
    if (!container) return;

    // Вимкнути сповіщення - просто приховуємо контейнер
    container.style.display = 'none';
}
function initMatchDateTimeInput() {
    const dateTimeInput = document.getElementById('matchDateTime');
    if (dateTimeInput) {
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        dateTimeInput.min = now.toISOString().slice(0, 16);

        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        tomorrow.setHours(19, 0, 0, 0);
        tomorrow.setMinutes(tomorrow.getMinutes() - tomorrow.getTimezoneOffset());
        dateTimeInput.value = tomorrow.toISOString().slice(0, 16);
    }
}
let allTeamsCache = [];
let teamsByLeagueCache = {}; // Команди по лігах

async function loadTeamsDatalist() {
    try {
        const response = await apiFetch('GET', '/api/teams/actual');
        if (response.ok && response.json) {
            allTeamsCache = [];
            teamsByLeagueCache = {}; // Очищуємо кеш ліг

            // Зберігаємо команди як по лігах, так і загальний список
            Object.entries(response.json).forEach(([league, leagueTeams]) => {
                teamsByLeagueCache[league] = leagueTeams.map(team => team.name).sort();

                leagueTeams.forEach(team => {
                    if (!allTeamsCache.includes(team.name)) {
                        allTeamsCache.push(team.name);
                    }
                });
            });

            allTeamsCache.sort();
        }
    } catch (error) {
        console.error('Помилка завантаження команд:', error);
    }
}
function showAutocomplete(input, dropdown, teams) {
    const query = input.value.trim().toLowerCase();

    if (!query) {
        dropdown.classList.remove('show');
        return;
    }

    const filtered = teams.filter(team =>
        team.toLowerCase().includes(query)
    );

    if (filtered.length === 0) {
        dropdown.innerHTML = '<div class="autocomplete-empty">Команду не знайдено</div>';
        dropdown.classList.add('show');
        return;
    }

    dropdown.innerHTML = filtered.map(team =>
        `<div class="autocomplete-item" data-value="${team}">${team}</div>`
    ).join('');

    dropdown.classList.add('show');

    dropdown.querySelectorAll('.autocomplete-item').forEach(item => {
        item.addEventListener('click', () => {
            input.value = item.dataset.value;
            dropdown.classList.remove('show');
            validateTeamSelection(input);
        });
    });
}
function validateTeamSelection(input) {
    const value = input.value.trim();
    if (!value) return true;

    const teams = getTeamsForSelectedLeague();
    const isValid = teams.includes(value);

    if (!isValid && value.length > 0) {
        const leagueSelect = document.querySelector('select[name="league"]');
        const leagueName = leagueSelect?.value || 'обраної ліги';
        input.setCustomValidity(`Оберіть команду зі списку ${leagueName}`);
    } else {
        input.setCustomValidity('');
    }

    return isValid;
}
function validateDifferentTeams() {
    const homeTeam = document.getElementById('homeTeamInput');
    const awayTeam = document.getElementById('awayTeamInput');

    if (!homeTeam || !awayTeam) return true;

    const homeValue = homeTeam.value.trim();
    const awayValue = awayTeam.value.trim();

    if (homeValue && awayValue && homeValue === awayValue) {
        awayTeam.setCustomValidity('Команди мають бути різними');
        return false;
    } else {
        awayTeam.setCustomValidity('');
        return true;
    }
}
function getTeamsForSelectedLeague() {
    const leagueSelect = document.querySelector('select[name="league"]');
    if (!leagueSelect || !leagueSelect.value) {
        return allTeamsCache; // Якщо ліга не обрана, показуємо всі команди
    }

    const selectedLeague = leagueSelect.value;
    return teamsByLeagueCache[selectedLeague] || [];
}

function initTeamAutocomplete() {
    const homeTeamInput = document.getElementById('homeTeamInput');
    const homeTeamDropdown = document.getElementById('homeTeamDropdown');
    const awayTeamInput = document.getElementById('awayTeamInput');
    const awayTeamDropdown = document.getElementById('awayTeamDropdown');
    const leagueSelect = document.querySelector('select[name="league"]');

    // Слухач зміни ліги - очищаємо поля команд
    if (leagueSelect) {
        leagueSelect.addEventListener('change', () => {
            if (homeTeamInput) homeTeamInput.value = '';
            if (awayTeamInput) awayTeamInput.value = '';
            homeTeamDropdown?.classList.remove('show');
            awayTeamDropdown?.classList.remove('show');
        });
    }

    if (homeTeamInput && homeTeamDropdown) {
        homeTeamInput.addEventListener('input', () => {
            const teams = getTeamsForSelectedLeague();
            showAutocomplete(homeTeamInput, homeTeamDropdown, teams);
            validateDifferentTeams();
        });

        homeTeamInput.addEventListener('focus', () => {
            if (homeTeamInput.value.trim()) {
                const teams = getTeamsForSelectedLeague();
                showAutocomplete(homeTeamInput, homeTeamDropdown, teams);
            }
        });

        homeTeamInput.addEventListener('blur', () => {
            setTimeout(() => {
                homeTeamDropdown.classList.remove('show');
                validateTeamSelection(homeTeamInput);
                validateDifferentTeams();
            }, 200);
        });
    }

    if (awayTeamInput && awayTeamDropdown) {
        awayTeamInput.addEventListener('input', () => {
            const teams = getTeamsForSelectedLeague();
            showAutocomplete(awayTeamInput, awayTeamDropdown, teams);
            validateDifferentTeams();
        });

        awayTeamInput.addEventListener('focus', () => {
            if (awayTeamInput.value.trim()) {
                const teams = getTeamsForSelectedLeague();
                showAutocomplete(awayTeamInput, awayTeamDropdown, teams);
            }
        });

        awayTeamInput.addEventListener('blur', () => {
            setTimeout(() => {
                awayTeamDropdown.classList.remove('show');
                validateTeamSelection(awayTeamInput);
                validateDifferentTeams();
            }, 200);
        });
    }
}
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        loadUpcomingMatchesNotifications();
        initMatchDateTimeInput();
        loadTeamsDatalist().then(() => {
            initTeamAutocomplete();
        });
        initLeagueOptions();
    });
} else {
    loadUpcomingMatchesNotifications();
    initMatchDateTimeInput();
    loadTeamsDatalist().then(() => {
        initTeamAutocomplete();
    });
    initLeagueOptions();
}
setInterval(loadUpcomingMatchesNotifications, 5 * 60 * 1000);

// Ініціалізація опцій перегляду ліги
function initLeagueOptions() {
    const checkboxes = {
        'show-past-matches': 'past-matches',
        'show-upcoming-matches': 'upcoming-matches',
        'show-table': 'league-table',
        'show-scorers': 'top-scorers'
    };

    Object.entries(checkboxes).forEach(([checkboxId, containerId]) => {
        const checkbox = document.getElementById(checkboxId);
        const container = document.getElementById(containerId);

        if (checkbox && container) {
            // Видаляємо попередні обробники, якщо вони є
            const newCheckbox = checkbox.cloneNode(true);
            checkbox.parentNode.replaceChild(newCheckbox, checkbox);
            const freshCheckbox = document.getElementById(checkboxId);

            freshCheckbox.addEventListener('change', function () {
                if (this.checked) {
                    container.style.display = 'block';
                    // Показуємо заголовок для таблиці
                    if (containerId === 'league-table') {
                        const header = document.getElementById('league-table-header');
                        if (header) header.style.display = 'block';
                    }
                    loadLeagueContent(containerId);
                } else {
                    container.style.display = 'none';
                    container.innerHTML = '';
                    // Приховуємо заголовок для таблиці
                    if (containerId === 'league-table') {
                        const header = document.getElementById('league-table-header');
                        if (header) header.style.display = 'none';
                    }
                }

                // Видаляємо старий обробник для show-scores-checkbox, якщо він був
            });

            // Початковий стан
            if (freshCheckbox.checked) {
                container.style.display = 'block';
                // Показуємо заголовок для таблиці
                if (containerId === 'league-table') {
                    const header = document.getElementById('league-table-header');
                    if (header) header.style.display = 'block';
                }
                loadLeagueContent(containerId);
            }
        }
    });

    // Створюємо глобальний чекбокс для синхронізації (якщо його немає в HTML)
    if (!document.getElementById('show-scores-checkbox')) {
        const hiddenCheckbox = document.createElement('input');
        hiddenCheckbox.type = 'checkbox';
        hiddenCheckbox.id = 'show-scores-checkbox';
        hiddenCheckbox.style.display = 'none';
        document.body.appendChild(hiddenCheckbox);
    }
}

// Перезавантаження активних опцій
function reloadActiveLeagueOptions() {
    // Завантажуємо в правильному порядку: матчі -> таблиця -> бомбардири -> статистика
    const loadOrder = [
        { checkboxId: 'show-past-matches', containerId: 'past-matches' },
        { checkboxId: 'show-upcoming-matches', containerId: 'upcoming-matches' },
        { checkboxId: 'show-table', containerId: 'league-table' },
        { checkboxId: 'show-scorers', containerId: 'top-scorers' }
    ];

    loadOrder.forEach(({ checkboxId, containerId }) => {
        const checkbox = document.getElementById(checkboxId);
        if (checkbox && checkbox.checked) {
            // Показуємо заголовок для таблиці
            if (containerId === 'league-table') {
                const header = document.getElementById('league-table-header');
                if (header) header.style.display = 'block';
            }
            loadLeagueContent(containerId);
        }
    });
}

// Debounce для швидкого перемикання
let loadLeagueContentTimeout = null;

// Завантаження контенту для секції (з очищенням контейнера)
function loadLeagueContent(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const league = currentLeague;

    // Очищаємо попередній таймер
    if (loadLeagueContentTimeout) {
        clearTimeout(loadLeagueContentTimeout);
    }

    // Додаємо невелику затримку для debounce (100ms)
    loadLeagueContentTimeout = setTimeout(() => {
        // Перевіряємо, чи ліга не змінилася під час затримки
        if (currentLeague !== league) {
            return;
        }

        switch (containerId) {
            case 'past-matches':
                {
                    const checkbox = document.getElementById('show-past-matches');
                    if (checkbox && !checkbox.checked) {
                        return;
                    }
                }
                // Очищаємо контейнер перед новим рендерингом (негайно)
                container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
                container.style.opacity = '0.7';
                renderPastMatches(container, league);
                break;
            case 'upcoming-matches':
                {
                    const checkbox = document.getElementById('show-upcoming-matches');
                    if (checkbox && !checkbox.checked) {
                        return;
                    }
                }
                // Очищаємо контейнер перед новим рендерингом (негайно)
                container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
                container.style.opacity = '0.7';
                renderUpcomingMatches(container, league);
                break;
            case 'league-table':
                renderLeagueTable(container, league);
                break;
            case 'top-scorers':
                renderTopScorers(container, league);
                break;
        }
    }, 100);
}

// Завантаження контенту без очищення контейнера (використовується при зміні ліги)
function loadLeagueContentWithoutClearing(containerId, league) {
    const container = document.getElementById(containerId);
    if (!container) return;

    // Перевіряємо, чи ліга не змінилася
    if (currentLeague !== league) {
        return;
    }

    switch (containerId) {
        case 'past-matches':
            {
                const checkbox = document.getElementById('show-past-matches');
                if (checkbox && !checkbox.checked) {
                    return;
                }
            }
            // Контейнер вже показує індикатор завантаження, просто рендеримо
            renderPastMatches(container, league);
            break;
        case 'upcoming-matches':
            {
                const checkbox = document.getElementById('show-upcoming-matches');
                if (checkbox && !checkbox.checked) {
                    return;
                }
            }
            // Контейнер вже показує індикатор завантаження, просто рендеримо
            renderUpcomingMatches(container, league);
            break;
        case 'league-table':
            renderLeagueTable(container, league);
            break;
        case 'top-scorers':
            renderTopScorers(container, league);
            break;
    }
}

// Рендер таблиці турніру
async function renderLeagueTable(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';

    try {
        // Спочатку отримуємо реальну турнірну таблицю з API
        let standingsResp = await apiFetch('GET', `/api/teams/standings/${league}`);
        let table = [];

        // Якщо API не відповідає, спробуємо завантажити з кешу через повторний запит
        if (!standingsResp.ok) {
            console.warn('API не відповідає, спробуємо завантажити з кешу...');
            standingsResp = await apiFetch('GET', `/api/teams/standings/${league}`);
        }

        if (standingsResp.ok && standingsResp.json) {
            const standings = standingsResp.json.standings;

            console.log(`[renderLeagueTable] Отримано дані для ${league}:`, {
                hasStandings: !!standings,
                isArray: Array.isArray(standings),
                length: standings?.length,
                source: standingsResp.json.source,
                fullResponse: standingsResp.json
            });

            // Перевіряємо, чи є дані і чи це не порожній масив
            if (standings && Array.isArray(standings) && standings.length > 0) {
                // Є реальні дані з API
                table = standings.map(entry => ({
                    position: entry.position,
                    name: entry.teamName,
                    emblemUrl: entry.teamCrest,
                    played: entry.playedGames,
                    won: entry.won,
                    draw: entry.draw,
                    lost: entry.lost,
                    goalsFor: entry.goalsFor,
                    goalsAgainst: entry.goalsAgainst,
                    points: entry.points,
                    source: 'api'
                }));

                // Тепер додаємо очки з локальних матчів
                const teamsResp = await apiFetch('GET', '/api/teams/actual');
                const matchesResp = await apiFetch('GET', '/api/matches');

                if (teamsResp.ok && matchesResp.ok) {
                    const leagueTeams = teamsResp.json[league] || [];
                    const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
                    const now = new Date();

                    // Фільтруємо минулі матчі ліги з рахунком
                    const leagueMatches = allMatches.filter(m => {
                        if (!m.score || !m.date) return false;
                        const matchDate = new Date(m.date);
                        if (matchDate >= now) return false;

                        const homeTeam = leagueTeams.find(t => t.name === m.homeTeam);
                        const awayTeam = leagueTeams.find(t => t.name === m.awayTeam);
                        return homeTeam && awayTeam;
                    });

                    // Додаємо очки з локальних матчів до таблиці
                    leagueMatches.forEach(match => {
                        const [homeScore, awayScore] = match.score.split(':').map(s => parseInt(s.trim()));
                        if (isNaN(homeScore) || isNaN(awayScore)) return;

                        const homeEntry = table.find(t => t.name === match.homeTeam);
                        const awayEntry = table.find(t => t.name === match.awayTeam);

                        if (homeEntry) {
                            homeEntry.played++;
                            homeEntry.goalsFor += homeScore;
                            homeEntry.goalsAgainst += awayScore;
                            if (homeScore > awayScore) {
                                homeEntry.won++;
                                homeEntry.points += 3;
                            } else if (homeScore < awayScore) {
                                homeEntry.lost++;
                            } else {
                                homeEntry.draw++;
                                homeEntry.points++;
                            }
                        }

                        if (awayEntry) {
                            awayEntry.played++;
                            awayEntry.goalsFor += awayScore;
                            awayEntry.goalsAgainst += homeScore;
                            if (awayScore > homeScore) {
                                awayEntry.won++;
                                awayEntry.points += 3;
                            } else if (awayScore < homeScore) {
                                awayEntry.lost++;
                            } else {
                                awayEntry.draw++;
                                awayEntry.points++;
                            }
                        }
                    });

                    // Сортуємо за очками
                    table.sort((a, b) => {
                        if (b.points !== a.points) return b.points - a.points;
                        const diffA = a.goalsFor - a.goalsAgainst;
                        const diffB = b.goalsFor - b.goalsAgainst;
                        if (diffB !== diffA) return diffB - diffA;
                        return b.goalsFor - a.goalsFor;
                    });
                }
            } else {
                // Standings є, але порожній або відсутній - генеруємо з локальних матчів
                console.log(`[renderLeagueTable] Standings порожній або відсутній для ${league} (source: ${standingsResp.json.source || 'unknown'}), генеруємо з локальних матчів`);
                const teamsResp = await apiFetch('GET', '/api/teams/actual');
                const matchesResp = await apiFetch('GET', '/api/matches');

                if (teamsResp.ok && matchesResp.ok) {
                    const leagueTeams = teamsResp.json[league] || [];
                    const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
                    const now = new Date();

                    const leagueMatches = allMatches.filter(m => {
                        if (!m.score || !m.date) return false;
                        const matchDate = new Date(m.date);
                        if (matchDate >= now) return false;

                        const homeTeam = leagueTeams.find(t => t.name === m.homeTeam);
                        const awayTeam = leagueTeams.find(t => t.name === m.awayTeam);
                        return homeTeam && awayTeam;
                    });

                    table = generateLeagueTable(leagueTeams, leagueMatches);
                    console.log(`[renderLeagueTable] Згенеровано таблицю з локальних матчів для ${league}:`, table.length, 'команд');
                } else {
                    console.warn(`[renderLeagueTable] Не вдалося завантажити команди або матчі для ${league}`);
                }
            }
        } else {
            // Немає реальних даних, генеруємо з локальних матчів
            console.log(`[renderLeagueTable] Немає даних з API для ${league}, генеруємо з локальних матчів`);
            const teamsResp = await apiFetch('GET', '/api/teams/actual');
            const matchesResp = await apiFetch('GET', '/api/matches');

            if (!teamsResp.ok || !matchesResp.ok) {
                throw new Error('Не вдалось завантажити дані');
            }

            const leagueTeams = teamsResp.json[league] || [];
            const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
            const now = new Date();

            const leagueMatches = allMatches.filter(m => {
                if (!m.score || !m.date) return false;
                const matchDate = new Date(m.date);
                if (matchDate >= now) return false;

                const homeTeam = leagueTeams.find(t => t.name === m.homeTeam);
                const awayTeam = leagueTeams.find(t => t.name === m.awayTeam);
                return homeTeam && awayTeam;
            });

            table = generateLeagueTable(leagueTeams, leagueMatches);
        }

        console.log(`[renderLeagueTable] Фінальна таблиця для ${league}:`, table.length, 'команд');

        // Оновлюємо заголовок поза контейнером
        const tableHeader = document.getElementById('league-table-header');
        if (tableHeader) {
            tableHeader.innerHTML = `<h3>📊 Таблиця ${league}</h3>`;
        }

        container.innerHTML = `
            ${table.length > 0 ? `
                <div class="league-table">
                    <table>
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Команда</th>
                                <th>І</th>
                                <th>В</th>
                                <th>Н</th>
                                <th>П</th>
                                <th>М</th>
                                <th>О</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${table.map((team, index) => `
                                <tr class="${index < 3 ? 'top-position' : ''}">
                                    <td class="position">${team.position || index + 1}</td>
                                    <td class="team-name">
                                        ${team.emblemUrl ? `<img src="${team.emblemUrl}" class="mini-emblem">` : '⚽'}
                                        ${escapeHtml(team.name)}
                                    </td>
                                    <td>${team.played}</td>
                                    <td>${team.won}</td>
                                    <td>${team.draw}</td>
                                    <td>${team.lost}</td>
                                    <td class="goals">${team.goalsFor}:${team.goalsAgainst}</td>
                                    <td class="points">${team.points}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            ` : `
                <div class="empty-content">
                    <p>Недостатньо даних для побудови таблиці</p>
                    <small>Додайте матчі для команд ліги ${league}</small>
                </div>
            `}
        `;
    } catch (error) {
        // Оновлюємо заголовок поза контейнером
        const tableHeader = document.getElementById('league-table-header');
        if (tableHeader) {
            tableHeader.innerHTML = `<h3>📊 Таблиця ${league}</h3>`;
        }

        container.innerHTML = `
            <div class="empty-content">
                <p>❌ Помилка завантаження: ${error.message}</p>
            </div>
        `;
    }
}

// Генерація турнірної таблиці
function generateLeagueTable(teams, matches) {
    const stats = {};

    // Ініціалізація статистики для всіх команд
    teams.forEach(team => {
        stats[team.name] = {
            name: team.name,
            emblemUrl: team.emblemUrl,
            played: 0,
            won: 0,
            draw: 0,
            lost: 0,
            goalsFor: 0,
            goalsAgainst: 0,
            points: 0
        };
    });

    // Якщо немає матчів, повертаємо базову таблицю з командами
    if (!matches || matches.length === 0) {
        return Object.values(stats).map((team, index) => ({
            ...team,
            position: index + 1
        }));
    }

    // Обробка матчів
    matches.forEach(match => {
        if (!match.score || !stats[match.homeTeam] || !stats[match.awayTeam]) return;

        const [homeScore, awayScore] = match.score.split(':').map(s => parseInt(s.trim()));
        if (isNaN(homeScore) || isNaN(awayScore)) return;

        stats[match.homeTeam].played++;
        stats[match.awayTeam].played++;
        stats[match.homeTeam].goalsFor += homeScore;
        stats[match.homeTeam].goalsAgainst += awayScore;
        stats[match.awayTeam].goalsFor += awayScore;
        stats[match.awayTeam].goalsAgainst += homeScore;

        if (homeScore > awayScore) {
            stats[match.homeTeam].won++;
            stats[match.homeTeam].points += 3;
            stats[match.awayTeam].lost++;
        } else if (homeScore < awayScore) {
            stats[match.awayTeam].won++;
            stats[match.awayTeam].points += 3;
            stats[match.homeTeam].lost++;
        } else {
            stats[match.homeTeam].draw++;
            stats[match.awayTeam].draw++;
            stats[match.homeTeam].points++;
            stats[match.awayTeam].points++;
        }
    });

    // Сортування по очках
    return Object.values(stats)
        .filter(team => team.played > 0)
        .sort((a, b) => {
            if (b.points !== a.points) return b.points - a.points;
            const diffA = a.goalsFor - a.goalsAgainst;
            const diffB = b.goalsFor - b.goalsAgainst;
            if (diffB !== diffA) return diffB - diffA;
            return b.goalsFor - a.goalsFor;
        });
}

async function fetchMatchesForLeague(league) {
    if (!league) {
        console.warn('fetchMatchesForLeague: league не вказано');
        return [];
    }

    const cacheKey = league.toUpperCase();
    const cached = leagueMatchesCache.get(cacheKey);
    const now = Date.now();
    if (cached && (now - cached.timestamp) < MATCHES_CACHE_TTL) {
        console.log(`Використовуємо кешовані матчі для ${league}:`, cached.data.length);
        return cached.data;
    }

    let matches = [];

    // Спочатку завантажуємо з локальної бази
    try {
        const response = await apiFetch('GET', `/api/matches?league=${encodeURIComponent(league)}`);
        if (response.ok && Array.isArray(response.json)) {
            matches = response.json;
            console.log(`Завантажено локальних матчів для ${league}:`, matches.length);
        }
    } catch (error) {
        console.warn(`Помилка завантаження локальних матчів для ${league}:`, error);
    }

    // Завантажуємо майбутні та минулі матчі для конкретної ліги з API
    try {
        const [upcomingResp, previousResp] = await Promise.all([
            apiFetch('GET', `/api/teams/matches/upcoming/${encodeURIComponent(league)}`),
            apiFetch('GET', `/api/teams/matches/previous/${encodeURIComponent(league)}`)
        ]);

        const matchesMap = new Map();

        // Спочатку додаємо локальні матчі в мапу
        matches.forEach(m => matchesMap.set(m.id, m));

        // Функція для додавання тегу до матчу
        const addMatchWithTag = (match, tag) => {
            let target = matchesMap.get(match.id);
            if (!target) {
                target = match;
                matchesMap.set(match.id, target);
            }
            // Ініціалізуємо масив тегів, якщо його немає
            if (!target.apiTags) target.apiTags = [];
            // Додаємо тег, якщо його ще немає
            if (!target.apiTags.includes(tag)) target.apiTags.push(tag);
        };

        if (upcomingResp.ok && upcomingResp.json && upcomingResp.json.matches) {
            const upcoming = Array.isArray(upcomingResp.json.matches) ? upcomingResp.json.matches : [];
            upcoming.forEach(m => addMatchWithTag(m, 'upcoming'));
            console.log(`Завантажено майбутніх матчів з API для ${league}:`, upcoming.length);
        }

        if (previousResp.ok && previousResp.json && previousResp.json.matches) {
            const previous = Array.isArray(previousResp.json.matches) ? previousResp.json.matches : [];
            // "previous" endpoint тепер повертає "current_tour" (поточний тур)
            previous.forEach(m => addMatchWithTag(m, 'current_tour'));
            console.log(`Завантажено матчів поточного туру з API для ${league}:`, previous.length);
        }

        // Перетворюємо мапу назад в масив
        matches = Array.from(matchesMap.values());
        console.log(`Всього матчів для ${league} після об'єднання:`, matches.length);

    } catch (error) {
        console.warn(`Помилка завантаження матчів з API для ${league}:`, error);
    }

    if (matches.length > 0) {
        leagueMatchesCache.set(cacheKey, { data: matches, timestamp: now });
    }

    return matches;
}

// Рендер минулих матчів
async function renderPastMatches(container, league) {
    return renderMatchesSection({
        container,
        league,
        mode: 'past',
        title: `⚽ Поточний тур ${league}`,
        emptyMessage: 'Немає матчів поточного туру для обраної ліги.'
    });
}

async function renderUpcomingMatches(container, league) {
    return renderMatchesSection({
        container,
        league,
        mode: 'upcoming',
        title: `📅 Наступний тур ${league}`,
        emptyMessage: 'Немає запланованих матчів для обраної ліги.'
    });
}

// Захист від подвійного рендерингу та race conditions
const renderingContainers = new Set();
const activeRequests = new Map(); // Зберігаємо активні запити для скасування

async function renderMatchesSection({ container, league, mode, title, emptyMessage }) {
    // Перевіряємо, чи не виконується вже рендеринг для цього контейнера
    const containerKey = `${container.id}_${mode}`;
    const requestKey = `${containerKey}_${league}`;

    // Скасовуємо попередній запит для цього контейнера, якщо він існує
    if (activeRequests.has(containerKey)) {
        const prevRequest = activeRequests.get(containerKey);
        if (prevRequest.league !== league) {
            console.log(`Скасовуємо попередній запит для ${containerKey} (ліга змінилася з ${prevRequest.league} на ${league})`);
            activeRequests.delete(containerKey);
        }
    }

    if (renderingContainers.has(requestKey)) {
        console.log(`Пропускаємо подвійний рендеринг для ${requestKey}`);
        return;
    }

    // Зберігаємо інформацію про поточний запит
    activeRequests.set(containerKey, { league, timestamp: Date.now() });
    renderingContainers.add(requestKey);

    // Перевіряємо, чи контейнер вже показує індикатор завантаження
    // Якщо ні, то додаємо його
    const hasLoadingIndicator = container.innerHTML.includes('⏳ Завантаження') ||
        container.innerHTML.includes('Завантаження');

    if (!hasLoadingIndicator) {
        // НЕГАЙНО очищаємо контейнер синхронно перед асинхронним завантаженням
        // Це запобігає показу старого контенту
        container.innerHTML = '';
        container.style.opacity = '0.7';

        // Додаємо індикатор завантаження
        const loadingDiv = document.createElement('div');
        loadingDiv.style.cssText = 'text-align: center; padding: 20px;';
        loadingDiv.textContent = '⏳ Завантаження...';
        container.appendChild(loadingDiv);
    } else {
        // Якщо індикатор вже є, просто встановлюємо opacity
        container.style.opacity = '0.7';
    }

    try {
        const matches = await fetchMatchesForLeague(league);

        // Перевіряємо, чи ліга не змінилася під час завантаження
        const currentRequest = activeRequests.get(containerKey);
        if (!currentRequest || currentRequest.league !== league) {
            console.log(`Ліга змінилася під час завантаження (було ${league}, стало ${currentRequest?.league || 'невизначено'}), пропускаємо рендеринг`);
            renderingContainers.delete(requestKey);
            return;
        }

        // Перевіряємо, чи поточна ліга все ще актуальна
        if (currentLeague !== league) {
            console.log(`Поточна ліга (${currentLeague}) не відповідає лізі запиту (${league}), пропускаємо рендеринг`);
            renderingContainers.delete(requestKey);
            activeRequests.delete(containerKey);
            return;
        }

        if (!matches || matches.length === 0) {
            container.style.opacity = '1';
            container.innerHTML = `
                <h3>${title}</h3>
                <div class="empty-content">
                    <p>${emptyMessage}</p>
                </div>
            `;
            renderingContainers.delete(requestKey);
            activeRequests.delete(containerKey);
            return;
        }

        const prepared = filterMatchesByMode(matches, league, mode);

        // Ще раз перевіряємо актуальність перед рендерингом
        if (currentLeague !== league) {
            console.log(`Ліга змінилася перед рендерингом, пропускаємо`);
            renderingContainers.delete(requestKey);
            activeRequests.delete(containerKey);
            return;
        }

        // Відновлюємо повну прозорість перед рендерингом
        container.style.opacity = '1';

        // Додаємо кнопку показу рахунку для минулих матчів
        const showScoresButton = mode === 'past' ? `
            <div class="matches-header-controls">
                <label class="score-toggle-label">
                    <input type="checkbox" id="show-scores-checkbox-inline" ${document.getElementById('show-scores-checkbox')?.checked ? 'checked' : ''}>
                    <span>Показувати рахунок</span>
                </label>
            </div>
        ` : '';

        container.innerHTML = `
            <div class="matches-section-header">
                <h3>${title}</h3>
                ${showScoresButton}
            </div>
            ${prepared.length > 0 ? `
                <div class="matches-list">
                    ${prepared.map(match => buildMatchCard(match, mode)).join('')}
                </div>
            ` : `
                <div class="empty-content">
                    <p>${emptyMessage}</p>
                </div>
            `}
        `;

        // Додаємо обробник для inline чекбокса
        if (mode === 'past') {
            const inlineCheckbox = document.getElementById('show-scores-checkbox-inline');
            if (inlineCheckbox) {
                const label = inlineCheckbox.closest('.score-toggle-label');

                // Оновлюємо клас для активного стану
                if (inlineCheckbox.checked && label) {
                    label.classList.add('checked');
                }

                inlineCheckbox.addEventListener('change', function () {
                    // Оновлюємо клас для стилізації
                    if (label) {
                        if (this.checked) {
                            label.classList.add('checked');
                        } else {
                            label.classList.remove('checked');
                        }
                    }

                    // Синхронізуємо з основним чекбоксом
                    const mainCheckbox = document.getElementById('show-scores-checkbox');
                    if (mainCheckbox) {
                        mainCheckbox.checked = this.checked;
                    }
                    // Перерендерюємо матчі
                    loadLeagueContent('past-matches');
                });
            }
        }
    } catch (error) {
        // Перевіряємо актуальність навіть при помилці
        if (currentLeague === league) {
            console.error('Помилка завантаження матчів:', error);
            container.style.opacity = '1';
            container.innerHTML = `
                <h3>${title}</h3>
                <div class="empty-content">
                    <p>❌ Помилка завантаження матчів: ${error.message}</p>
                </div>
            `;
        } else {
            // Якщо ліга змінилася, просто очищаємо контейнер
            container.innerHTML = '';
            container.style.opacity = '1';
        }
    } finally {
        renderingContainers.delete(requestKey);
        const currentRequest = activeRequests.get(containerKey);
        if (currentRequest && currentRequest.league === league) {
            activeRequests.delete(containerKey);
        }
    }
}

function filterMatchesByMode(matches, league, mode) {
    const now = new Date();

    console.log(`Фільтрація матчів: league=${league}, mode=${mode}, всього матчів=${matches.length}`);

    const filtered = matches
        .filter(match => {
            // Перевіряємо лігу (може бути різний регістр)
            const matchLeague = match.league ? match.league.trim() : '';
            const targetLeague = league ? league.trim() : '';

            if (!matchLeague || !targetLeague) {
                return false;
            }

            const leagueMatch = matchLeague.toUpperCase() === targetLeague.toUpperCase();

            if (!leagueMatch) {
                return false;
            }

            // Обробляємо дату (може бути в різних форматах)
            let kickoffDate = null;
            if (match.kickoffAt) {
                if (typeof match.kickoffAt === 'string') {
                    kickoffDate = new Date(match.kickoffAt);
                } else if (match.kickoffAt instanceof Date) {
                    kickoffDate = match.kickoffAt;
                } else if (match.date && match.time) {
                    kickoffDate = new Date(`${match.date}T${match.time}`);
                }
            }

            if (!kickoffDate || isNaN(kickoffDate.getTime())) {
                // Якщо немає дати, використовуємо рахунок для визначення минулого/майбутнього
                if (mode === 'past') {
                    // Для минулих матчів перевіряємо наявність рахунку
                    const hasScore = (match.score && (match.score.home !== undefined || match.score.away !== undefined)) ||
                        (match.homeScore !== undefined && match.awayScore !== undefined);
                    return hasScore;
                } else {
                    // Для майбутніх матчів перевіряємо відсутність рахунку
                    const hasNoScore = (!match.score || (match.score.home === undefined && match.score.away === undefined)) &&
                        (match.homeScore === undefined || match.awayScore === undefined);
                    return hasNoScore;
                }
            }

            // ПРІОР ИТЕТ: Якщо є теги API, використовуємо їх для фільтрації
            if (match.apiTags && match.apiTags.length > 0) {
                if (mode === 'past') {
                    // Показуємо матчі поточного туру (всі матчі незалежно від статусу)
                    return match.apiTags.includes('current_tour');
                } else if (mode === 'upcoming') {
                    // Показуємо матчі наступного туру
                    // Фільтруємо тільки upcoming матчі, які не є current_tour
                    return match.apiTags.includes('upcoming') && !match.apiTags.includes('current_tour');
                }
            }

            // ФОЛБЕК: Для локальних матчів або якщо немає тегів, використовуємо дату/рахунок
            return mode === 'past' ? kickoffDate < now : kickoffDate >= now;
        })
        .sort((a, b) => {
            let dateA = null, dateB = null;

            if (a.kickoffAt) {
                if (typeof a.kickoffAt === 'string') {
                    dateA = new Date(a.kickoffAt);
                } else if (a.kickoffAt instanceof Date) {
                    dateA = a.kickoffAt;
                } else if (a.date && a.time) {
                    dateA = new Date(`${a.date}T${a.time}`);
                }
            }

            if (b.kickoffAt) {
                if (typeof b.kickoffAt === 'string') {
                    dateB = new Date(b.kickoffAt);
                } else if (b.kickoffAt instanceof Date) {
                    dateB = b.kickoffAt;
                } else if (b.date && b.time) {
                    dateB = new Date(`${b.date}T${b.time}`);
                }
            }

            if (!dateA || !dateB) return 0;
            return mode === 'past' ? dateB - dateA : dateA - dateB;
        });

    console.log(`Відфільтровано матчів для ${league} (${mode}):`, filtered.length);
    return filtered;
}

function buildMatchCard(match, mode) {
    // Обробляємо різні формати даних (з API та з БД)
    const homeTeamName = typeof match.homeTeam === 'string'
        ? match.homeTeam
        : (match.homeTeam?.name || 'Господарі');

    const awayTeamName = typeof match.awayTeam === 'string'
        ? match.awayTeam
        : (match.awayTeam?.name || 'Гості');

    // Перевіряємо, чи показувати рахунок (для минулих матчів)
    const showScoresCheckbox = document.getElementById('show-scores-checkbox');
    const showScores = showScoresCheckbox ? showScoresCheckbox.checked : false;

    // Обробляємо рахунок (може бути в різних форматах)
    let score = 'VS';
    if (mode === 'past' && showScores) {
        if (match.score && typeof match.score === 'object') {
            // Формат з API: {home: 2, away: 1}
            const homeScore = match.score.home ?? match.score.homeScore ?? '-';
            const awayScore = match.score.away ?? match.score.awayScore ?? '-';
            score = `${formatScoreValue(homeScore)} : ${formatScoreValue(awayScore)}`;
        } else if (match.homeScore !== undefined && match.awayScore !== undefined) {
            // Формат з БД: homeScore та awayScore як окремі поля
            score = `${formatScoreValue(match.homeScore)} : ${formatScoreValue(match.awayScore)}`;
        }
    }

    // Обробляємо дату (може бути рядок або об'єкт LocalDateTime)
    let kickoffDate = null;
    if (match.kickoffAt) {
        if (typeof match.kickoffAt === 'string') {
            kickoffDate = new Date(match.kickoffAt);
        } else if (match.kickoffAt instanceof Date) {
            kickoffDate = match.kickoffAt;
        } else if (match.date && match.time) {
            // Якщо дата розділена на date та time
            kickoffDate = new Date(`${match.date}T${match.time}`);
        }
    }

    const matchdayBadge = match.matchday
        ? `<span class="match-badge">Тур ${match.matchday}</span>`
        : '';

    // Отримуємо емблеми команд, якщо вони є
    const homeTeamCrest = match.homeTeamEmblem || (typeof match.homeTeam === 'object' && match.homeTeam?.crest ? match.homeTeam.crest : '');
    const awayTeamCrest = match.awayTeamEmblem || (typeof match.awayTeam === 'object' && match.awayTeam?.crest ? match.awayTeam.crest : '');

    // Отримуємо емодзі ліги
    const leagueEmoji = getLeagueEmojiForMatch(match.league);

    const dateTime = kickoffDate && !isNaN(kickoffDate.getTime()) ? formatMatchDateTime(kickoffDate) : 'Дата не встановлена';

    return `
        <div class="match-card ${mode}">
            <div class="match-header">
                ${match.league ? `<span class="match-league">${leagueEmoji} ${match.league}</span>` : ''}
                <div class="match-info">
                    <span class="match-date">${dateTime}</span>
                    ${matchdayBadge}
                </div>
            </div>
            <div class="match-content">
                <div class="team team-home">
                    ${homeTeamCrest ? `<img src="${homeTeamCrest}" alt="${homeTeamName}" class="team-crest">` : ''}
                    <span class="team-name">${escapeHtml(homeTeamName)}</span>
                </div>
                <div class="match-score">${score}</div>
                <div class="team team-away">
                    <span class="team-name">${escapeHtml(awayTeamName)}</span>
                    ${awayTeamCrest ? `<img src="${awayTeamCrest}" alt="${awayTeamName}" class="team-crest">` : ''}
                </div>
            </div>
        </div>
    `;
}

function formatScoreValue(value) {
    return typeof value === 'number' && value >= 0 ? value : '-';
}

function getLeagueEmojiForMatch(league) {
    if (!league) return '⚽';
    const emojis = {
        'UCL': '⭐',
        'UCL': '⭐',
        'EPL': '🏴',
        'LaLiga': '🇪🇸',
        'Bundesliga': '🇩🇪',
        'SerieA': '🇮🇹',
        'Ligue1': '🇫🇷'
    };
    return emojis[league.toUpperCase()] || '⚽';
}

function formatMatchDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatMatchDateTime(date) {
    const datePart = date.toLocaleDateString('uk-UA', { day: '2-digit', month: 'short' });
    const timePart = date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
    return `${datePart} - ${timePart}`;
}

// Рендер топ бомбардирів
async function renderTopScorers(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';

    try {
        let response = await apiFetch('GET', `/api/teams/scorers/${encodeURIComponent(league)}`);

        // Якщо API не відповідає, спробуємо повторний запит (може бути кеш)
        if (!response.ok) {
            console.warn('API не відповідає для бомбардирів, спробуємо повторний запит...');
            response = await apiFetch('GET', `/api/teams/scorers/${encodeURIComponent(league)}`);
        }

        if (!response.ok || !response.json) {
            throw new Error('Не вдалось завантажити дані');
        }

        console.log(`[renderTopScorers] Отримано дані для ${league}:`, response.json);

        const scorers = response.json.scorers || [];

        console.log(`[renderTopScorers] Кількість бомбардирів: ${scorers.length}`);

        if (scorers.length === 0) {
            container.innerHTML = `
                <h3>⚽ Бомбардири ${league}</h3>
                <div class="empty-content">
                    <p>Немає даних про бомбардирів для обраної ліги.</p>
                </div>
            `;
            return;
        }

        const scorersHtml = scorers.map((scorer, index) => {
            const position = index + 1;
            const name = scorer.name || 'Невідомий гравець';
            const teamName = scorer.teamName || 'Невідома команда';
            const teamCrest = scorer.teamCrest || '';
            const goals = scorer.goals || 0;
            const assists = scorer.assists || 0;
            const penalties = scorer.penalties || 0;
            const positionEmoji = position === 1 ? '🥇' : position === 2 ? '🥈' : position === 3 ? '🥉' : `${position}.`;

            return `
                <div class="scorer-card">
                    <div class="scorer-position">${positionEmoji}</div>
                    <div class="scorer-info">
                        <div class="scorer-name">${escapeHtml(name)}</div>
                        <div class="scorer-team">
                            ${teamCrest ? `<img src="${teamCrest}" alt="${teamName}" class="scorer-team-crest">` : ''}
                            <span>${escapeHtml(teamName)}</span>
                        </div>
                    </div>
                    <div class="scorer-stats">
                        <div class="scorer-goals">
                            <span class="stat-value">${goals}</span>
                            <span class="stat-label">Голів</span>
                        </div>
                        ${assists > 0 ? `
                            <div class="scorer-assists">
                                <span class="stat-value">${assists}</span>
                                <span class="stat-label">Асистів</span>
                            </div>
                        ` : ''}
                        <div class="scorer-penalties">
                            <span class="stat-value">${penalties}</span>
                            <span class="stat-label">з пен.</span>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = `
            <h3>⚽ Бомбардири ${league}</h3>
            <div class="scorers-list">
                ${scorersHtml}
            </div>
        `;
    } catch (error) {
        console.error('Помилка завантаження бомбардирів:', error);
        container.innerHTML = `
            <h3>⚽ Бомбардири ${league}</h3>
            <div class="empty-content">
                <p>❌ Помилка завантаження бомбардирів: ${error.message}</p>
            </div>
        `;
    }
}
