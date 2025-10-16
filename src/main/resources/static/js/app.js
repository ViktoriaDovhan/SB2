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

function renderMatchesList(matchesList, containerId = 'matches-list') {
    const container = document.querySelector(`#${containerId}`);
    if (!container) return;
    
    if (!matchesList || matchesList.length === 0) {
        container.innerHTML = '<div class="empty-state"><h3>⚽ Немає матчів</h3><p>Створіть перший матч!</p></div>';
        return;
    }
    
    container.innerHTML = matchesList.map(match => {
        const kickoff = new Date(match.kickoffAt);
        const dateStr = kickoff.toLocaleDateString('uk-UA', {day: 'numeric', month: 'long', year: 'numeric'});
        const timeStr = kickoff.toLocaleTimeString('uk-UA', {hour: '2-digit', minute: '2-digit'});
        
        return `
        <div class="match-card" onclick="viewMatchDetail(${match.id})">
            <div class="match-header">
                <span class="match-date">📅 ${dateStr} • ${timeStr}</span>
            </div>
            <div class="match-teams">
                <div class="team-name team-home">${escapeHtml(match.homeTeam)}</div>
                <div class="match-score">${match.homeScore || 0} : ${match.awayScore || 0}</div>
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
    
    if (section === 'teams' && method === 'POST') {
        const leagueSelect = document.getElementById('league-select');
        const customLeagueInput = document.getElementById('custom-league-input');
        
        if (leagueSelect && leagueSelect.value === 'custom' && customLeagueInput) {
            data.league = customLeagueInput.value;
        }
    }

    const {ok, status, json} = await apiFetch(method, url, data);
    setStatus(section, ok, status, json);

    if (ok && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        if (section === 'teams' && method === 'POST') {
            alert(`✅ Команду "${data.name}" успішно додано до ліги ${data.league}!`);
            toggleForms('teams');
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

// ==================== СПОВІЩЕННЯ ПРО МАЙБУТНІ МАТЧІ ====================

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

// Завантажувати сповіщення при першому завантаженні
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadUpcomingMatchesNotifications);
} else {
    loadUpcomingMatchesNotifications();
}

// Оновлювати сповіщення кожні 5 хвилин
setInterval(loadUpcomingMatchesNotifications, 5 * 60 * 1000);

