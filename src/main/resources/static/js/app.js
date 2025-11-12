async function apiFetch(method, url, data) {
    const opts = {method, headers: {}};
    if (method !== 'GET' && method !== 'HEAD') {
        opts.headers['Content-Type'] = 'application/json';
        opts.body = JSON.stringify(data ?? {});
    }
    const r = await fetch(url, opts);
    const text = await r.text();
    let json;
    try {
        json = text ? JSON.parse(text) : {status: r.status};
    } catch {
        json = {status: r.status, raw: text};
    }
    return {ok: r.ok, status: r.status, json};
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

function extractErrorMessage(p){return (p&&(p.detail||p.message||p.error||p.raw))||'Не вдалося виконати операцію';}

function setStatus(section, ok, status, payload){
    const el=document.querySelector(`#status-${section}`);
    if(el){
        const msg=ok?'':extractErrorMessage(payload);
        el.textContent=(ok?'✅ ':'❌ ')+status+(msg?` · ${msg}`:'');
        el.className='status '+(ok?'success':'error');
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
        const dateStr = kickoff.toLocaleDateString('uk-UA', {day: 'numeric', month: 'long', year: 'numeric'});
        const timeStr = kickoff.toLocaleTimeString('uk-UA', {hour: '2-digit', minute: '2-digit'});

        const homeScore = match.homeScore ?? '?';
        const awayScore = match.awayScore ?? '?';
        const scoreDisplay = showScores ? `${homeScore} : ${awayScore}` : '? : ?';

        return `
        <div class="match-card" onclick="viewMatchDetail(${match.id})">
            <div class="match-header">
                <span class="match-date">📅 ${dateStr} • ${timeStr}</span>
            </div>
            <div class="match-teams">
                <div class="team-name team-home">${escapeHtml(match.homeTeam)}</div>
                <div class="match-score">${scoreDisplay}</div>
                <div class="team-name team-away">${escapeHtml(match.awayTeam)}</div>
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

function escapeHtml(s){const d=document.createElement('div');d.textContent=s??'';return d.innerHTML;}

async function viewNewsDetail(id) {
    const {ok, status, json} = await apiFetch('GET', `/api/news/${id}`);
    if (ok) {
        alert(`Новина #${id}\n\n${json.title}\n\n${json.content}\n\nЛайків: ${json.likes || 0}`);
    }
}

async function viewMatchDetail(id) {
    const {ok, status, json} = await apiFetch('GET', `/api/matches/${id}`);
    if (ok) {
        const kickoff = new Date(json.kickoffAt);
        alert(`Матч #${id}\n\n${json.homeTeam} ${json.homeScore} : ${json.awayScore} ${json.awayTeam}\n\n${kickoff.toLocaleString('uk-UA')}`);
    }
}

async function viewTopicPosts(topicId) {
    const {ok, status, json} = await apiFetch('GET', `/api/forum/topics/${topicId}/posts`);
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
    const {ok, status, json} = await apiFetch('POST', `/api/news/${id}/like`);
    if (ok) {
        const activePanel = document.querySelector('.panel.active');
        if (activePanel) {
            await writeList(activePanel.id);
        }
    }
}

async function backendFetch(url,opt={})
{
    const r=await fetch(url,opt);
    const t=await r.text();
    let p=null;
    try {
        p=t?JSON.parse(t):null
    }
    catch{
        p={raw:t}
    }
    if(!r.ok){
        alert('❌ '+extractErrorMessage(p));
        throw new Error('HTTP '+r.status)
    }
    return p
}


window.viewTopicPosts=async function(topicId){
    currentTopicId=topicId;
    const posts=await backendFetch(`/api/forum/topics/${topicId}/posts`);
    const items=Array.isArray(posts)&&posts.length?posts.map(p=>`
    <div class="post-item"><div class="post-author">👤 ${escapeHtml(p.author)}</div><div class="post-text">${escapeHtml(p.text)}</div></div>`).join('')
        :'<div class="empty-state">У цій темі поки немає постів</div>';
    const box=document.createElement('div');
    box.innerHTML=`
    <div class="modal-backdrop" style="position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:9999;display:flex;align-items:center;justify-content:center;padding:20px" onclick="this.remove()">
      <div class="modal" style="background:#fff;padding:24px;border-radius:12px;max-width:600px;max-height:80vh;overflow:auto" onclick="event.stopPropagation()">
        <h3 style="margin:0 0 16px">📝 Пости теми #${topicId}</h3>
        <div class="posts">${items}</div>
        <button class="btn btn-primary" style="margin-top:16px;width:100%" onclick="this.closest('.modal-backdrop').remove()">Закрити</button>
      </div>
    </div>`;
    document.body.appendChild(box);
};


window.addPostUI=async function(author,text){
    if(!currentTopicId){alert('Спочатку відкрийте тему і натисніть «Переглянути пости».');return;}
    await backendFetch(`/api/forum/topics/${currentTopicId}/posts`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({author,text})});
    await reloadForumList();
    await viewTopicPosts(currentTopicId);
};


function ensureForumLoaded(){const a=document.querySelector('.panel.active'); if(a&&a.id==='forum') reloadForumList();}

window.createTopicUI=async function(title,author){
    await backendFetch('/api/forum/topics',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,author})});
    await reloadForumList();
};

async function reloadForumList(){
    const topics=await backendFetch('/api/forum/topics');
    const box=document.querySelector('#forum-list')||document.querySelector('#topics-list')||document.querySelector('#topic-list');
    if(!box)return;
    if(!Array.isArray(topics)||topics.length===0){
        box.innerHTML='<div class="empty-state"><h3>💬 Немає тем</h3><p>Створіть першу тему!</p></div>';
        const c=document.getElementById('stat-topics'); if(c) c.textContent='0';
        return;
    }
    box.innerHTML=topics.map(t=>`
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
    const c=document.getElementById('stat-topics'); if(c) c.textContent=String(topics.length);
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
        'UPL': '🇺🇦',
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

let currentLeague = 'UPL';

async function loadTeamsByLeague(league) {
    currentLeague = league;
    
    document.querySelectorAll('.league-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.league === league);
    });
    
    const r = await apiFetch('GET', '/api/teams/actual');
    if (r.ok && r.json) {
        const teamsData = r.json;
        const leagueTeams = teamsData[league] || [];
        
        const userTeamsR = await apiFetch('GET', '/api/teams');
        if (userTeamsR.ok && Array.isArray(userTeamsR.json)) {
            const userLeagueTeams = userTeamsR.json.filter(t => t.league === league);
            const combined = [...leagueTeams, ...userLeagueTeams];
            renderTeamsList(combined);
        } else {
            renderTeamsList(leagueTeams);
        }
        
        const teamsEl = document.getElementById('stat-teams');
        if (teamsEl) {
            const totalTeams = Object.values(teamsData).reduce((sum, teams) => sum + teams.length, 0);
            const userTeamsCount = userTeamsR.ok ? userTeamsR.json.length : 0;
            teamsEl.textContent = totalTeams + userTeamsCount;
        }
    }
    
    // Перезавантаження активних опцій для нової ліги
    reloadActiveLeagueOptions();
    
    // Перезавантаження матчів з фільтрацією по новій лізі
    if (typeof reloadMatchesForLeague === 'function') {
        reloadMatchesForLeague(league);
    }
}

async function updateDashboardStats() {
    const newsR = await apiFetch('GET', '/api/news');
    const matchesR = await apiFetch('GET', '/api/matches');
    const teamsR = await apiFetch('GET', '/api/teams/actual');
    const topicsR = await apiFetch('GET', '/api/forum/topics');
    
    const newsCount = Array.isArray(newsR.json) ? newsR.json.length : 0;
    const matchesCount = Array.isArray(matchesR.json) ? matchesR.json.length : 0;
    
    let teamsCount = 0;
    if (teamsR.ok && teamsR.json) {
        teamsCount = Object.values(teamsR.json).reduce((sum, teams) => sum + teams.length, 0);
    }
    
    const topicsCount = Array.isArray(topicsR.json) ? topicsR.json.length : 0;

    const newsEl = document.getElementById('stat-news');
    const matchesEl = document.getElementById('stat-matches');
    const teamsEl = document.getElementById('stat-teams');
    const topicsEl = document.getElementById('stat-topics');
    
    if (newsEl) newsEl.textContent = newsCount;
    if (matchesEl) matchesEl.textContent = matchesCount;
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
        const r = await apiFetch('GET', '/api/matches');
        if (r.ok && Array.isArray(r.json)) {
            renderMatchesList(r.json);
        }
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

window.toggleForms = function(section) {
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

    const {ok, status, json} = await apiFetch(method, url, data);
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
        alert(`❌ Помилка: `+ extractErrorMessage(json));
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
    
    const cardsHtml = matches.map(match => {
        const kickoffDate = new Date(match.kickoffAt);
        const now = new Date();
        const hoursUntil = Math.floor((kickoffDate - now) / (1000 * 60 * 60));
        
        const dateStr = kickoffDate.toLocaleDateString('uk-UA', {
            day: 'numeric',
            month: 'long',
            weekday: 'short'
        });
        
        const timeStr = kickoffDate.toLocaleTimeString('uk-UA', {
            hour: '2-digit',
            minute: '2-digit'
        });
        
        let countdownText;
        if (hoursUntil < 1) {
            countdownText = 'Менше години!';
        } else if (hoursUntil < 24) {
            countdownText = `Через ${hoursUntil} год`;
        } else {
            const days = Math.floor(hoursUntil / 24);
            countdownText = `Через ${days} ${days === 1 ? 'день' : 'дні'}`;
        }
        
        return `
            <div class="notification-match-card">
                <div class="match-teams">
                    ${match.homeTeam} 🆚 ${match.awayTeam}
                </div>
                <div class="match-time">
                    📅 ${dateStr} о ${timeStr}
                </div>
                <div class="match-time">
                    <span class="match-countdown">${countdownText}</span>
                </div>
            </div>
        `;
    }).join('');
    
    container.innerHTML = `
        <div class="notification-banner">
            <div class="notification-icon">⚽🔔</div>
            <div class="notification-content">
                <div class="notification-title">
                    🎯 Майбутні матчі в найближчі 2 дні
                </div>
                <div class="notification-matches">
                    ${cardsHtml}
                </div>
            </div>
        </div>
    `;
    
    container.style.display = 'block';
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
        'show-stats': 'league-stats'
    };

    Object.entries(checkboxes).forEach(([checkboxId, containerId]) => {
        const checkbox = document.getElementById(checkboxId);
        const container = document.getElementById(containerId);

        if (checkbox && container) {
            checkbox.addEventListener('change', function() {
                if (this.checked) {
                    container.style.display = 'block';
                    loadLeagueContent(containerId);
                } else {
                    container.style.display = 'none';
                }
            });

            // Початковий стан
            if (checkbox.checked) {
                container.style.display = 'block';
                loadLeagueContent(containerId);
            }
        }
    });
}

// Перезавантаження активних опцій
function reloadActiveLeagueOptions() {
    const checkboxes = [
        'show-past-matches',
        'show-upcoming-matches',
        'show-table',
        'show-stats'
    ];
    
    const containers = {
        'show-past-matches': 'past-matches',
        'show-upcoming-matches': 'upcoming-matches',
        'show-table': 'league-table',
        'show-stats': 'league-stats'
    };
    
    checkboxes.forEach(checkboxId => {
        const checkbox = document.getElementById(checkboxId);
        if (checkbox && checkbox.checked) {
            const containerId = containers[checkboxId];
            loadLeagueContent(containerId);
        }
    });
}

// Завантаження контенту для секції
function loadLeagueContent(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const league = currentLeague;

    switch(containerId) {
        case 'past-matches':
            renderPastMatches(container, league);
            break;
        case 'upcoming-matches':
            renderUpcomingMatches(container, league);
            break;
        case 'league-table':
            renderLeagueTable(container, league);
            break;
        case 'league-stats':
            renderLeagueStats(container, league);
            break;
    }
}

// Рендер таблиці турніру
async function renderLeagueTable(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
    
    try {
        // Спочатку отримуємо реальну турнірну таблицю з API
        const standingsResp = await apiFetch('GET', `/api/teams/standings/${league}`);
        let table = [];
        
        if (standingsResp.ok && standingsResp.json && standingsResp.json.standings) {
            // Є реальні дані з API
            table = standingsResp.json.standings.map(entry => ({
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
            // Немає реальних даних, генеруємо з локальних матчів
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
        
        container.innerHTML = `
            <h3>📊 Турнірна таблиця ${league}</h3>
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
                ${table.some(t => t.source === 'api') ? 
                    '<small style="color: var(--gray); display: block; margin-top: 12px; text-align: center;">📡 Дані з реального API + локальні матчі</small>' : 
                    '<small style="color: var(--gray); display: block; margin-top: 12px; text-align: center;">📍 Дані з локальних матчів</small>'
                }
            ` : `
                <div class="empty-content">
                    <p>Недостатньо даних для побудови таблиці</p>
                    <small>Додайте матчі для команд ліги ${league}</small>
                </div>
            `}
        `;
    } catch (error) {
        container.innerHTML = `
            <h3>📊 Турнірна таблиця ${league}</h3>
            <div class="empty-content">
                <p>❌ Помилка завантаження: ${error.message}</p>
            </div>
        `;
    }
}

// Генерація турнірної таблиці
function generateLeagueTable(teams, matches) {
    const stats = {};
    
    // Ініціалізація статистики
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

// Рендер минулих матчів
async function renderPastMatches(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
    
    try {
        const teamsResp = await apiFetch('GET', '/api/teams/actual');
        const matchesResp = await apiFetch('GET', '/api/matches');
        
        if (!teamsResp.ok || !matchesResp.ok) {
            throw new Error('Не вдалось завантажити дані');
        }
        
        const leagueTeams = teamsResp.json[league] || [];
        const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
        const now = new Date();
        
        // Фільтруємо минулі матчі ліги (дата < поточної дати + league співпадає)
        const pastMatches = allMatches.filter(m => {
            // Перевіряємо лігу
            if (m.league !== league) return false;
            
            if (!m.kickoffAt) return false;
            const matchDate = new Date(m.kickoffAt);
            if (matchDate >= now) return false; // Не минулий матч
            
            return true;
        }).sort((a, b) => new Date(b.kickoffAt) - new Date(a.kickoffAt));
        
        container.innerHTML = `
            <h3>⚽ Минулі матчі ${league}</h3>
            ${pastMatches.length > 0 ? `
                <div class="matches-list">
                    ${pastMatches.map(match => {
                        const score = match.homeScore !== undefined && match.awayScore !== undefined 
                            ? `${match.homeScore} : ${match.awayScore}` 
                            : '-';
                        return `
                            <div class="match-card past">
                                <div class="match-date">${formatMatchDate(match.kickoffAt)}</div>
                                <div class="match-teams">
                                    <div class="team home">${escapeHtml(match.homeTeam)}</div>
                                    <div class="match-score">${score}</div>
                                    <div class="team away">${escapeHtml(match.awayTeam)}</div>
                                </div>
                            </div>
                        `;
                    }).join('')}
                </div>
            ` : `
                <div class="empty-content">
                    <p>Немає зіграних матчів</p>
                    <small>Матчі з'являться після створення матчів з минулою датою для ліги ${league}</small>
                </div>
            `}
        `;
    } catch (error) {
        container.innerHTML = `
            <h3>⚽ Минулі матчі ${league}</h3>
            <div class="empty-content">
                <p>❌ Помилка завантаження: ${error.message}</p>
            </div>
        `;
    }
}

function formatMatchDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short', year: 'numeric' });
}

// Рендер майбутніх матчів
async function renderUpcomingMatches(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
    
    try {
        const teamsResp = await apiFetch('GET', '/api/teams/actual');
        const matchesResp = await apiFetch('GET', '/api/matches');
        
        if (!teamsResp.ok || !matchesResp.ok) {
            throw new Error('Не вдалось завантажити дані');
        }
        
        const leagueTeams = teamsResp.json[league] || [];
        const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
        const now = new Date();
        
        // Фільтруємо майбутні матчі (дата >= поточної дати + league співпадає)
        const upcomingMatches = allMatches.filter(m => {
            // Перевіряємо лігу
            if (m.league !== league) return false;
            
            if (!m.kickoffAt) return false;
            const matchDate = new Date(m.kickoffAt);
            if (matchDate < now) return false; // Минулий матч
            
            return true;
        }).sort((a, b) => new Date(a.kickoffAt) - new Date(b.kickoffAt));
        
        container.innerHTML = `
            <h3>📅 Майбутні матчі ${league}</h3>
            ${upcomingMatches.length > 0 ? `
                <div class="matches-list">
                    ${upcomingMatches.map(match => `
                        <div class="match-card upcoming">
                            <div class="match-date">${formatMatchDate(match.kickoffAt)}</div>
                            <div class="match-teams">
                                <div class="team home">${escapeHtml(match.homeTeam)}</div>
                                <div class="match-vs">VS</div>
                                <div class="team away">${escapeHtml(match.awayTeam)}</div>
                            </div>
                        </div>
                    `).join('')}
                </div>
            ` : `
                <div class="empty-content">
                    <p>Немає запланованих матчів</p>
                    <small>Додайте матчі для ліги ${league}</small>
                </div>
            `}
        `;
    } catch (error) {
        container.innerHTML = `
            <h3>📅 Майбутні матчі ${league}</h3>
            <div class="empty-content">
                <p>❌ Помилка завантаження: ${error.message}</p>
            </div>
        `;
    }
}

// Рендер статистики
async function renderLeagueStats(container, league) {
    container.innerHTML = '<div style="text-align: center; padding: 20px;">⏳ Завантаження...</div>';
    
    try {
        const teamsResp = await apiFetch('GET', '/api/teams/actual');
        const matchesResp = await apiFetch('GET', '/api/matches');
        
        if (!teamsResp.ok || !matchesResp.ok) {
            throw new Error('Не вдалось завантажити дані');
        }
        
        const leagueTeams = teamsResp.json[league] || [];
        const allMatches = Array.isArray(matchesResp.json) ? matchesResp.json : [];
        
        // Фільтруємо матчі ліги по league
        const leagueMatches = allMatches.filter(m => m.league === league);
        
        // Зіграні матчі - це ті, що мають рахунок (не null) АБО дата вже минула і є рахунок
        const playedMatches = leagueMatches.filter(m => {
            return m.homeScore !== null && m.homeScore !== undefined && 
                   m.awayScore !== null && m.awayScore !== undefined;
        });
        
        // Майбутні матчі - ті що без рахунку
        const upcomingMatches = leagueMatches.filter(m => {
            return m.homeScore === null || m.homeScore === undefined || 
                   m.awayScore === null || m.awayScore === undefined;
        });
        
        let totalGoals = 0;
        let homeWins = 0, awayWins = 0, draws = 0;
        
        playedMatches.forEach(m => {
            const home = m.homeScore;
            const away = m.awayScore;
            if (!isNaN(home) && !isNaN(away)) {
                totalGoals += home + away;
                if (home > away) homeWins++;
                else if (away > home) awayWins++;
                else draws++;
            }
        });
        
        const avgGoals = playedMatches.length > 0 ? (totalGoals / playedMatches.length).toFixed(2) : 0;
        
        const winRate = playedMatches.length > 0 ? ((homeWins/playedMatches.length)*100).toFixed(1) : 0;
        const drawRate = playedMatches.length > 0 ? ((draws/playedMatches.length)*100).toFixed(1) : 0;
        const lossRate = playedMatches.length > 0 ? ((awayWins/playedMatches.length)*100).toFixed(1) : 0;
        
        container.innerHTML = `
            <h3>📈 Статистика ліги ${league}</h3>
            
            <div class="stats-overview">
                <div class="stat-box stat-primary">
                    <div class="stat-icon">🏆</div>
                    <div class="stat-content">
                        <div class="stat-number">${leagueTeams.length}</div>
                        <div class="stat-text">Команд у лізі</div>
                    </div>
                </div>
                
                <div class="stat-box stat-success">
                    <div class="stat-icon">⚽</div>
                    <div class="stat-content">
                        <div class="stat-number">${playedMatches.length}</div>
                        <div class="stat-text">Зіграно матчів</div>
                    </div>
                </div>
                
                <div class="stat-box stat-warning">
                    <div class="stat-icon">📅</div>
                    <div class="stat-content">
                        <div class="stat-number">${upcomingMatches.length}</div>
                        <div class="stat-text">Заплановано</div>
                    </div>
                </div>
                
                <div class="stat-box stat-info">
                    <div class="stat-icon">🎯</div>
                    <div class="stat-content">
                        <div class="stat-number">${totalGoals}</div>
                        <div class="stat-text">Всього голів</div>
                    </div>
                </div>
                
                <div class="stat-box stat-danger">
                    <div class="stat-icon">📊</div>
                    <div class="stat-content">
                        <div class="stat-number">${avgGoals}</div>
                        <div class="stat-text">Голів за матч</div>
                    </div>
                </div>
            </div>
            
            ${playedMatches.length > 0 ? `
                <div class="results-chart">
                    <h4>📊 Розподіл результатів</h4>
                    <div class="chart-items">
                        <div class="chart-item">
                            <div class="chart-header">
                                <span class="chart-title">🏠 Перемоги господарів</span>
                                <span class="chart-value">${homeWins} матчів (${winRate}%)</span>
                            </div>
                            <div class="chart-bar-wrapper">
                                <div class="chart-bar-bg">
                                    <div class="chart-bar-fill bar-wins" style="width: ${winRate}%"></div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="chart-item">
                            <div class="chart-header">
                                <span class="chart-title">⚖️ Нічиї</span>
                                <span class="chart-value">${draws} матчів (${drawRate}%)</span>
                            </div>
                            <div class="chart-bar-wrapper">
                                <div class="chart-bar-bg">
                                    <div class="chart-bar-fill bar-draws" style="width: ${drawRate}%"></div>
                                </div>
                            </div>
                        </div>
                        
                        <div class="chart-item">
                            <div class="chart-header">
                                <span class="chart-title">✈️ Перемоги гостей</span>
                                <span class="chart-value">${awayWins} матчів (${lossRate}%)</span>
                            </div>
                            <div class="chart-bar-wrapper">
                                <div class="chart-bar-bg">
                                    <div class="chart-bar-fill bar-losses" style="width: ${lossRate}%"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            ` : ''}
        `;
    } catch (error) {
        container.innerHTML = `
            <h3>📈 Статистика ліги ${league}</h3>
            <div class="empty-content">
                <p>❌ Помилка завантаження: ${error.message}</p>
            </div>
        `;
    }
}


