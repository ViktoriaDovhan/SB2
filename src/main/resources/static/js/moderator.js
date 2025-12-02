document.addEventListener('DOMContentLoaded', () => {
    if (!isAuthenticated()) {
        window.location.href = '/login.html';
        return;
    }

    const role = getRole();
    if (role !== 'MODERATOR') {
        if (role === 'USER') {
            window.location.href = '/user.html';
        } else if (role === 'EDITOR') {
            window.location.href = '/editor.html';
        }
        return;
    }

    updateUserInfo();
    loadNews();
    loadMatches();
    // Завантажуємо майбутні та минулі матчі
    if (typeof loadTeamsByLeague === 'function') {
        loadTeamsByLeague('UCL');
    } else {
        loadTeams();
    }
    loadForumTopics();
    loadModerationTopics();

    setupTabs();

    // Обробка чекбоксу показу рахунку - додаємо з затримкою, щоб елемент точно існував
    setTimeout(() => {
        const showScoresElement = document.getElementById('showScores');
        if (showScoresElement) {
            showScoresElement.addEventListener('change', () => {
                refreshScoreDisplay();
            });
        }
    }, 100);

    // Ініціалізація обробника чекбоксу майбутніх матчів
    if (typeof initUpcomingMatchesCheckbox === 'function') {
        initUpcomingMatchesCheckbox();
    }

    // Ініціалізація чекбоксів матчів
    initMatchesCheckboxes();

    // Ініціалізація опцій перегляду ліги
    if (typeof initLeagueOptions === 'function') {
        initLeagueOptions();
    }
});

function updateUserInfo() {
    const el = document.getElementById('userInfo');
    if (!el) return;
    const username = getUsername() || '';
    el.innerHTML = `
        <span class="user-name">🛡️ ${username}</span>
        <button onclick="logout()" class="btn-logout">Вийти</button>
    `;
}

function setupTabs() {
    const navLinks = document.querySelectorAll('.nav-link[data-tab]');
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const tabName = link.dataset.tab;
            showTab(tabName);
        });
    });

    setupDropdowns();
}

function setupDropdowns() {
    document.addEventListener('click', (e) => {
        const dropdowns = document.querySelectorAll('.dropdown');
        dropdowns.forEach(dropdown => {
            if (!dropdown.contains(e.target)) {
                dropdown.classList.remove('open');
            }
        });
    });

    const dropdownToggles = document.querySelectorAll('.dropdown-toggle');
    dropdownToggles.forEach(toggle => {
        toggle.addEventListener('click', (e) => {
            e.preventDefault();
            const dropdown = toggle.closest('.dropdown');

            document.querySelectorAll('.dropdown').forEach(d => {
                if (d !== dropdown) {
                    d.classList.remove('open');
                }
            });

            dropdown.classList.toggle('open');
        });
    });

    const dropdownItems = document.querySelectorAll('.dropdown-item');
    dropdownItems.forEach(item => {
        item.addEventListener('click', (e) => {
            const dropdown = item.closest('.dropdown');
            dropdown.classList.remove('open');

            if (item.dataset.tab) {
                e.preventDefault();
                const tabName = item.dataset.tab;
                showTab(tabName);
            }
        });
    });
}

function showTab(tabName) {
    document.querySelectorAll('.panel').forEach(panel => {
        panel.classList.remove('active');
    });

    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });

    const panel = document.getElementById(tabName);
    if (panel) {
        panel.classList.add('active');
    }

    const activeLink = document.querySelector(`.nav-link[data-tab="${tabName}"]`);
    if (activeLink) {
        activeLink.classList.add('active');
    }
}

async function loadNews() {
    try {
        const response = await fetch('/api/news');
        if (!response.ok) throw new Error('Помилка завантаження новин');

        const news = await response.json();

        if (typeof renderNewsList === 'function') {
            renderNewsList(news.slice(0, 3), 'home-news');
            renderNewsList(news, 'all-news');
        } else {
            displayNews(news.slice(0, 3), 'home-news', true);
            displayNews(news, 'all-news', true);
        }

        updateStatistics('news', news.length);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити новини', 'error');
    }
}

function displayNews(news, containerId, withInteractions = false) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (news.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає новин</div>';
        return;
    }

    container.innerHTML = news.map(item => `
        <article class="news-article">
            <div class="news-header">
                <h3 class="news-title">${escapeHtml(item.title)}</h3>
                <p class="news-content">${escapeHtml(item.content)}</p>
                <div class="news-meta">
                    <span class="news-badge">📅 ${formatDate(item.createdAt)}</span>
                    <span class="news-badge likes">❤️ ${item.likes || 0} вподобань</span>
                </div>
                ${withInteractions ? `
                    <div class="topic-actions">
                        <button class="btn" onclick="likeNews(${item.id})">❤️ Подобається</button>
                    </div>
                ` : ''}
            </div>
        </article>
    `).join('');
}

let allMatchesCache = [];
let currentFilteredMatches = [];
let matchesPage = 1;
const MATCHES_PER_PAGE = 20;

async function loadMatches() {
    const container = document.getElementById('matches-list');
    if (container) {
        container.innerHTML = '<div class="loading-spinner">Завантаження...</div>';
    }

    try {
        const [dbMatchesR, externalR] = await Promise.all([
            fetch('/api/matches?full=true'),
            fetch('/api/teams/matches/all')
        ]);

        let allMatches = [];

        if (dbMatchesR.ok) {
            const json = await dbMatchesR.json();
            if (Array.isArray(json)) {
                allMatches = [...json];
            }
        }

        if (externalR.ok) {
            const json = await externalR.json();
            if (json && Array.isArray(json.matches)) {
                const normalized = json.matches.map(m => normalizeExternalMatch(m));
                allMatches = [...allMatches, ...normalized];
            }
        }

        const uniqueMatches = Array.from(new Map(allMatches.map(m => [m.id, m])).values());
        uniqueMatches.sort((a, b) => new Date(b.kickoffAt) - new Date(a.kickoffAt));

        allMatchesCache = uniqueMatches;

        renderFilters(uniqueMatches);
        filterAndRenderMatches(true);

        updateStatistics('matches', uniqueMatches.length);

        document.getElementById('filter-league')?.addEventListener('change', () => filterAndRenderMatches(true));
        document.getElementById('filter-tour')?.addEventListener('change', () => filterAndRenderMatches(true));
        document.getElementById('filter-search')?.addEventListener('input', debounce(() => filterAndRenderMatches(true), 300));
        document.getElementById('load-more-matches')?.addEventListener('click', () => {
            matchesPage++;
            renderMatchesPage();
        });

        // Обробка зміни чекбоксу показу рахунку - вже додано вище

    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити матчі', 'error');
        if (container) container.innerHTML = '<div class="error-state">Помилка завантаження</div>';
    }
}

function renderFilters(matches) {
    const leagueSelect = document.getElementById('filter-league');
    const tourSelect = document.getElementById('filter-tour');

    if (!leagueSelect || !tourSelect) return;

    const leagues = [...new Set(matches.map(m => m.league))].filter(Boolean).sort();
    leagueSelect.innerHTML = '<option value="">Всі ліги</option>' +
        leagues.map(l => `<option value="${l}">${getLeagueName(l)}</option>`).join('');

    const tours = [...new Set(matches.map(m => m.matchday))].filter(Boolean).sort((a, b) => a - b);
    tourSelect.innerHTML = '<option value="">Всі тури</option>' +
        tours.map(t => `<option value="${t}">Тур ${t}</option>`).join('');
}

function getLeagueName(code) {
    const names = {
        'UCL': '⭐ Ліга Чемпіонів',
        'EPL': '🏴 АПЛ',
        'LaLiga': '🇪🇸 Ла Ліга',
        'Bundesliga': '🇩🇪 Бундесліга',
        'SerieA': '🇮🇹 Серія А',
        'Ligue1': '🇫🇷 Ліга 1',
        'UPL': '🇺🇦 УПЛ'
    };
    return names[code] || code;
}

function filterAndRenderMatches(resetPage = false) {
    if (resetPage) matchesPage = 1;

    const leagueFilter = document.getElementById('filter-league')?.value;
    const tourFilter = document.getElementById('filter-tour')?.value;
    const searchText = document.getElementById('filter-search')?.value.toLowerCase();
    const showScores = document.getElementById('showScores')?.checked ?? true;

    currentFilteredMatches = allMatchesCache.filter(m => {
        const matchLeague = m.league || '';
        const matchTour = m.matchday ? m.matchday.toString() : '';
        const home = (m.homeTeam || '').toLowerCase();
        const away = (m.awayTeam || '').toLowerCase();

        if (leagueFilter && matchLeague !== leagueFilter) return false;
        if (tourFilter && matchTour !== tourFilter) return false;
        if (searchText && !home.includes(searchText) && !away.includes(searchText)) return false;

        return true;
    });

    const container = document.getElementById('matches-list');
    if (resetPage && container) container.innerHTML = '';

    if (currentFilteredMatches.length === 0) {
        if (container) container.innerHTML = '<div class="empty-state">Матчів не знайдено</div>';
        const loadMoreBtn = document.getElementById('load-more-matches');
        if (loadMoreBtn) loadMoreBtn.style.display = 'none';
        return;
    }

    renderMatchesPage();
}

function refreshCurrentView() {
    // Просто перерендерюємо поточну сторінку з поточними фільтрами
    const container = document.getElementById('matches-list');
    if (container) {
        // Очищаємо тільки поточну сторінку, зберігаємо всі фільтри
        container.innerHTML = '';
        renderMatchesPage();
    }
}

function renderMatchesPage() {
    const container = document.getElementById('matches-list');
    const loadMoreBtn = document.getElementById('load-more-matches');
    const showScores = document.getElementById('showScores')?.checked ?? true;

    const start = (matchesPage - 1) * MATCHES_PER_PAGE;
    const end = start + MATCHES_PER_PAGE;
    const pageMatches = currentFilteredMatches.slice(start, end);

    if (pageMatches.length === 0) {
        if (loadMoreBtn) loadMoreBtn.style.display = 'none';
        return;
    }

    let lastDate = '';
    let html = '';

    pageMatches.forEach(match => {
        const matchDate = new Date(match.kickoffAt).toLocaleDateString('uk-UA', { weekday: 'long', day: 'numeric', month: 'long' });

        if (matchDate !== lastDate) {
            html += `<div class="match-date-header">${matchDate}</div>`;
            lastDate = matchDate;
        }

        html += createMatchCardHtml(match, showScores);
    });

    if (container) {
        if (matchesPage === 1) {
            container.innerHTML = html;
        } else {
            container.insertAdjacentHTML('beforeend', html);
        }
    }

    if (loadMoreBtn) {
        loadMoreBtn.style.display = end < currentFilteredMatches.length ? 'block' : 'none';
    }
}

function refreshScoreDisplay() {
    // Просто змінюємо відображення рахунку для поточних відображених матчів
    const showScores = document.getElementById('showScores')?.checked ?? true;
    const matchCards = document.querySelectorAll('.match-card');

    matchCards.forEach(card => {
        const scoreElement = card.querySelector('.match-score');
        if (scoreElement) {
            const matchId = card.querySelector('.info-badge')?.textContent?.replace('ID: ', '');
            if (matchId) {
                // Знаходимо матч в currentFilteredMatches
                const match = currentFilteredMatches.find(m => m.id.toString() === matchId);
                if (match) {
                    const homeScore = match.homeScore ?? '?';
                    const awayScore = match.awayScore ?? '?';
                    const scoreDisplay = showScores ? `${homeScore} - ${awayScore}` : '? - ?';
                    scoreElement.textContent = scoreDisplay;
                }
            }
        }
    });
}

function createMatchCardHtml(match, showScores) {
    const homeScore = match.homeScore ?? '?';
    const awayScore = match.awayScore ?? '?';
    const scoreDisplay = showScores ? `${homeScore} - ${awayScore}` : '? - ?';

    // Отримуємо емблеми команд
    const homeTeamEmblem = match.homeTeamEmblem || '';
    const awayTeamEmblem = match.awayTeamEmblem || '';
    const league = match.league || '';
    const leagueIcon = getLeagueIcon(league);

    // Створюємо HTML для іконок команд (як у вкладці Команди)
    const homeIconHtml = homeTeamEmblem 
        ? `<img src="${escapeHtml(homeTeamEmblem)}" alt="${escapeHtml(match.homeTeam || 'Команда 1')}" class="team-crest" onerror="this.outerHTML='${leagueIcon}'">`
        : `<span class="team-crest-fallback">${leagueIcon}</span>`;
    
    const awayIconHtml = awayTeamEmblem 
        ? `<img src="${escapeHtml(awayTeamEmblem)}" alt="${escapeHtml(match.awayTeam || 'Команда 2')}" class="team-crest" onerror="this.outerHTML='${leagueIcon}'">`
        : `<span class="team-crest-fallback">${leagueIcon}</span>`;

    return `
        <div class="match-card">
            <div class="match-content">
                <div class="team team-home">
                    ${homeIconHtml}
                    <span class="team-name">${escapeHtml(match.homeTeam || 'Команда 1')}</span>
                </div>
                <div class="match-score">${scoreDisplay}</div>
                <div class="team team-away">
                    <span class="team-name">${escapeHtml(match.awayTeam || 'Команда 2')}</span>
                    ${awayIconHtml}
                </div>
            </div>
            <div class="match-info">
                <span class="info-badge">🏆 ${getLeagueName(match.league)}</span>
                <span class="info-badge">📅 ${formatDate(match.kickoffAt)}</span>
                ${match.matchday ? `<span class="info-badge">Тур ${match.matchday}</span>` : ''}
            </div>
        </div>
    `;
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

async function loadMatchesByLeague(leagueCode) {
    currentLeague = leagueCode;
    const container = document.getElementById('matches-list');
    if (container) {
        container.innerHTML = '<div class="loading-spinner">Завантаження...</div>';
    }

    try {
        // Fetch all matches for the league (using the existing endpoint that fetches all season matches)
        // We might need to filter by league on the client side if the endpoint returns everything
        // But let's check if we can use /api/teams/matches/all and filter, or if there is a better way.
        // The previous code used /api/teams/matches/all which returned everything.
        // Let's use that and filter client-side for now as per plan.

        const [dbMatchesR, externalR] = await Promise.all([
            fetch('/api/matches?league=' + leagueCode + '&full=true'), // Request full list from DB
            fetch('/api/teams/matches/all') // This might be heavy, but it's what we have. 
            // Optimization: If we could pass league to external API endpoint, it would be better.
            // But for now, let's stick to the plan.
        ]);

        let allMatches = [];

        // Process DB matches
        if (dbMatchesR.ok) {
            const json = await dbMatchesR.json();
            if (Array.isArray(json)) {
                allMatches = [...json];
            }
        }

        // Process External Matches
        if (externalR.ok) {
            const json = await externalR.json();
            if (json && Array.isArray(json.matches)) {
                const normalized = json.matches
                    .filter(m => m.league === leagueCode) // Filter by league
                    .map(m => normalizeExternalMatch(m));
                allMatches = [...allMatches, ...normalized];
            }
        }

        // Deduplicate by ID
        const uniqueMatches = Array.from(new Map(allMatches.map(m => [m.id, m])).values());

        // Sort by date (newest first)
        uniqueMatches.sort((a, b) => new Date(b.kickoffAt) - new Date(a.kickoffAt));

        currentMatches = uniqueMatches;

        // Identify Matchdays
        const matchdays = [...new Set(uniqueMatches.map(m => getMatchdayFromDate(m.kickoffAt)))].sort((a, b) => a - b);

        // Find current matchday (closest to today)
        const currentMatchday = findCurrentMatchday(uniqueMatches);

        renderMatchdaySelector(matchdays, currentMatchday);
        filterMatchesByMatchday(currentMatchday);

        updateStatistics('matches', uniqueMatches.length);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити матчі', 'error');
        if (container) container.innerHTML = '<div class="error-state">Помилка завантаження</div>';
    }
}

function getMatchdayFromDate(dateString) {
    // This is a simplified logic. Ideally, the API should return the matchday.
    // If API doesn't return matchday, we might need to group by week or similar.
    // For now, let's assume we can group by "Tour" if available, or just use a placeholder.
    // Since the external API returns 'matchday' (we saw it in backend code), let's try to use it.
    // But `normalizeExternalMatch` didn't include it. Let's update `normalizeExternalMatch`.
    return 0; // Placeholder, will be fixed in normalizeExternalMatch
}

function findCurrentMatchday(matches) {
    const now = new Date();
    // Find the first match that is in the future
    const nextMatch = matches.slice().reverse().find(m => new Date(m.kickoffAt) > now);
    return nextMatch ? nextMatch.matchday : (matches[0]?.matchday || 1);
}

function renderMatchdaySelector(matchdays, activeMatchday) {
    const selector = document.getElementById('matchday-selector');
    if (!selector) return;

    // If we don't have real matchdays, we might need to generate them or handle differently.
    // Let's assume we have them.

    // Group matches by matchday to get the list of available matchdays
    const availableMatchdays = [...new Set(currentMatches.map(m => m.matchday))].filter(m => m != null).sort((a, b) => a - b);

    if (availableMatchdays.length === 0) {
        selector.innerHTML = '<span class="matchday-pill">Всі матчі</span>';
        return;
    }

    selector.innerHTML = availableMatchdays.map(day => `
        <div class="matchday-pill ${day === activeMatchday ? 'active' : ''}" 
             onclick="filterMatchesByMatchday(${day}); setActivePill(this)">
            Тур ${day}
        </div>
    `).join('');

    // Scroll to active
    setTimeout(() => {
        const active = selector.querySelector('.active');
        if (active) {
            active.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
        }
    }, 100);
}

function setActivePill(element) {
    document.querySelectorAll('.matchday-pill').forEach(p => p.classList.remove('active'));
    element.classList.add('active');
}

function filterMatchesByMatchday(matchday) {
    const container = document.getElementById('matches-list');
    if (!container) return;

    const filtered = currentMatches.filter(m => m.matchday === matchday);
    const showScores = document.getElementById('showScores')?.checked ?? true;

    if (filtered.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає матчів для цього туру</div>';
        return;
    }

    // Sort: Upcoming first (asc), then Past (desc) ? 
    // Usually within a tour, we just want chronological order.
    filtered.sort((a, b) => new Date(a.kickoffAt) - new Date(b.kickoffAt));

    displayMatches(filtered, 'matches-list', showScores, true);
}

function scrollMatchdays(direction) {
    const selector = document.getElementById('matchday-selector');
    if (selector) {
        const scrollAmount = 200;
        selector.scrollBy({
            left: direction === 'left' ? -scrollAmount : scrollAmount,
            behavior: 'smooth'
        });
    }
}

// Initialize Match League Tabs
document.querySelectorAll('.league-tab[data-match-league]').forEach(tab => {
    tab.addEventListener('click', (e) => {
        e.preventDefault();
        // Remove active class from all tabs
        document.querySelectorAll('.league-tab[data-match-league]').forEach(t => t.classList.remove('active'));
        // Add active class to clicked tab
        tab.classList.add('active');

        const league = tab.dataset.matchLeague;
        loadMatchesByLeague(league);
    });
});

function normalizeExternalMatch(m) {
    return {
        id: m.id,
        homeTeam: m.homeTeam?.name || m.homeTeam || 'Unknown',
        awayTeam: m.awayTeam?.name || m.awayTeam || 'Unknown',
        homeTeamEmblem: m.homeTeam?.crest || m.homeTeamEmblem || '',
        awayTeamEmblem: m.awayTeam?.crest || m.awayTeamEmblem || '',
        homeScore: m.score?.home ?? null,
        awayScore: m.score?.away ?? null,
        kickoffAt: m.kickoffAt,
        league: m.league,
        matchday: m.matchday, // Ensure this is passed
        isExternal: true
    };
}

function getLeagueIcon(league) {
    const icons = {
        'UCL': '⭐',
        'EPL': '🏴',
        'LaLiga': '🇪🇸',
        'Bundesliga': '🇩🇪',
        'SerieA': '🇮🇹',
        'Ligue1': '🇫🇷',
        'UPL': '🇺🇦'
    };
    return icons[league] || '⚽';
}

function displayMatches(matches, containerId, showScores, withNotifications = false) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (matches.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає матчів</div>';
        return;
    }

    container.innerHTML = matches.map(match => {
        const homeScore = match.homeScore ?? '?';
        const awayScore = match.awayScore ?? '?';
        const scoreDisplay = showScores ? `${homeScore} - ${awayScore}` : '? - ?';
        const isFuture = new Date(match.kickoffAt) > new Date();

        return `
            <div class="match-card">
                <div class="match-teams">
                    <span class="team-name team-home">${escapeHtml(match.homeTeam || 'Команда 1')}</span>
                    <span class="match-score">${scoreDisplay}</span>
                    <span class="team-name team-away">${escapeHtml(match.awayTeam || 'Команда 2')}</span>
                </div>
                <div class="match-info">
                    <span class="info-badge">📅 ${formatDate(match.kickoffAt)}</span>
                </div>
                ${withNotifications && isFuture ? `
                    <div class="topic-actions">
                        <button class="btn-action" onclick="subscribeToMatch(${match.id})">
                            🔔 Увімкнути сповіщення
                        </button>
                    </div>
                ` : ''}
            </div>
        `;
    }).join('');
}

async function loadTeams() {
    try {
        const actualResp = await fetch('/api/teams/actual');
        if (!actualResp.ok) throw new Error('Помилка завантаження актуальних команд');

        const leaguesMap = await actualResp.json(); // { UPL: [...], UCL: [...], ... }
        const actualTeams = Object.entries(leaguesMap)
            .flatMap(([league, teams]) => (teams || []).map(t => ({ ...t, league })));

        let userTeams = [];
        try {
            const userResp = await fetch('/api/teams');
            if (userResp.ok) {
                const arr = await userResp.json();
                if (Array.isArray(arr)) userTeams = arr;
            }
        } catch (_) { }

        const combined = [...actualTeams, ...userTeams];

        if (typeof renderTeamsList === 'function') {
            renderTeamsList(combined);
        } else {
            const container = document.getElementById('teams-list');
            if (container) {
                if (combined.length === 0) {
                    container.innerHTML = '<div class="empty-state">Немає команд</div>';
                } else {
                    container.innerHTML = combined.map(team => `
        <div class="team-card">
            <div class="team-icon">
                <div class="team-emblem">🏆</div>
            </div>
            <h3 class="team-name-display">${escapeHtml(team.name)}</h3>
            ${team.city ? `<p class="team-city">Місто: ${escapeHtml(team.city)}</p>` : ''}
            ${team.league ? `<p class="team-city">Ліга: ${escapeHtml(team.league)}</p>` : ''}
        </div>
    `).join('');
                }
            }
        }

        updateStatistics('teams', combined.length);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити команди', 'error');
    }
}

function displayTeams(teams, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (teams.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає команд</div>';
        return;
    }

    container.innerHTML = teams.map(team => `
        <div class="team-card">
            <div class="team-icon">
                <div class="team-emblem">🏆</div>
            </div>
            <h3 class="team-name-display">${escapeHtml(team.name)}</h3>
            <p class="team-city">Заснована: ${team.foundedYear || 'Невідомо'}</p>
            <p class="team-city">Стадіон: ${escapeHtml(team.stadium || 'Невідомо')}</p>
        </div>
    `).join('');
}

async function loadForumTopics() {
    try {
        const response = await fetchWithAuth('/api/forum/topics');
        if (!response.ok) throw new Error('Помилка завантаження форуму');

        const topics = await response.json();
        if (typeof renderForumTopics === 'function') {
            renderForumTopics(topics);
        } else {
            displayForumTopics(topics);
        }

        updateStatistics('topics', topics.length);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити форум', 'error');
    }
}

function displayForumTopics(topics) {
    const container = document.getElementById('forum-topics');
    if (!container) return;

    if (topics.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає тем на форумі</div>';
        return;
    }

    container.innerHTML = topics.map(topic => `
        <div class="topic-card">
            <h3 class="topic-title">${escapeHtml(topic.title)}</h3>
            <p>${escapeHtml(topic.description)}</p>
            <div class="topic-meta">
                <span class="topic-badge author">👤 ${escapeHtml(topic.author)}</span>
                <span class="topic-badge">📅 ${formatDate(topic.createdAt)}</span>
                <span class="topic-badge">💬 ${topic.postsCount || 0} відповідей</span>
            </div>
            <div class="topic-actions">
                <button class="btn" onclick="showTopicPosts(${topic.id}, '${escapeHtml(topic.title)}')">
                    Переглянути обговорення
                </button>
            </div>
        </div>
    `).join('');
}

function showCreateTopicForm() {
    document.getElementById('create-topic-form').style.display = 'block';
}

function hideCreateTopicForm() {
    document.getElementById('create-topic-form').style.display = 'none';
    document.getElementById('topic-title').value = '';
    document.getElementById('topic-description').value = '';
}

async function createForumTopic(event) {
    event.preventDefault();

    const title = document.getElementById('topic-title').value;
    const description = document.getElementById('topic-description').value;

    try {
        const response = await fetchWithAuth('/api/forum/topics', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ title, description })
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage('Тему створено успішно!', 'success');
        hideCreateTopicForm();
        loadForumTopics();
        loadModerationTopics();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося створити тему: ' + error.message, 'error');
    }
}

async function showTopicPosts(topicId, topicTitle) {
    try {
        const response = await fetchWithAuth(`/api/forum/topics/${topicId}/posts`);
        if (!response.ok) throw new Error('Помилка завантаження постів');

        const posts = await response.json();

        const modal = `
            <div id="topic-modal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 9999; display: flex; align-items: center; justify-content: center;">
                <div style="background: white; padding: 30px; border-radius: 10px; max-width: 800px; max-height: 80vh; overflow-y: auto; width: 90%;">
                    <h2>${escapeHtml(topicTitle)}</h2>
                    <div id="posts-container" style="margin: 20px 0;">
                        ${posts.length === 0 ? '<p>Немає коментарів</p>' : posts.map(post => `
                            <div class="topic-card" style="margin-bottom: 15px;">
                                <p>${escapeHtml(post.content)}</p>
                                <div class="topic-meta">
                                    <span class="topic-badge author">👤 ${escapeHtml(post.author)}</span>
                                    <span class="topic-badge">📅 ${formatDate(post.createdAt)}</span>
                                </div>
                                <div class="topic-actions">
                                    <button class="btn danger" onclick="deletePost(${post.id})">
                                        🗑️ Видалити пост (модератор)
                                    </button>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                    <form onsubmit="addPostToTopic(event, ${topicId})" style="margin-top: 20px;">
                        <div class="form-group">
                            <label>Ваш коментар</label>
                            <textarea id="post-content" rows="3" required></textarea>
                        </div>
                        <button type="submit" class="btn">Додати коментар</button>
                        <button type="button" class="btn danger" onclick="deleteTopic(${topicId})">🗑️ Видалити тему</button>
                        <button type="button" class="btn" onclick="closeTopicModal()">Закрити</button>
                    </form>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modal);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити пости', 'error');
    }
}

async function addPostToTopic(event, topicId) {
    event.preventDefault();

    const content = document.getElementById('post-content').value;

    try {
        const response = await fetchWithAuth(`/api/forum/topics/${topicId}/posts`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ content })
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage('Коментар додано!', 'success');
        closeTopicModal();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося додати коментар: ' + error.message, 'error');
    }
}

function closeTopicModal() {
    const modal = document.getElementById('topic-modal');
    if (modal) {
        modal.remove();
    }
}

async function likeNews(newsId) {
    try {
        const response = await fetchWithAuth(`/api/news/${newsId}/like`, {
            method: 'POST'
        });

        if (!response.ok) throw new Error('Помилка');

        showMessage('Вподобання додано!', 'success');
        loadNews();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося додати вподобання', 'error');
    }
}

async function subscribeToMatch(matchId) {
    try {
        const response = await fetchWithAuth(`/api/matches/${matchId}/subscribe`, {
            method: 'POST'
        });

        if (!response.ok) throw new Error('Помилка');

        showMessage('Сповіщення увімкнено! Ви отримаєте нагадування перед матчем.', 'success');
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося увімкнути сповіщення', 'error');
    }
}

async function loadModerationTopics() {
    try {
        const response = await fetchWithAuth('/api/forum/topics');
        if (!response.ok) throw new Error('Помилка завантаження форуму');

        const topics = await response.json();
        displayModerationTopics(topics);
    } catch (error) {
        console.error('Помилка:', error);
    }
}

function displayModerationTopics(topics) {
    const container = document.getElementById('moderation-topics');
    if (!container) return;

    if (topics.length === 0) {
        container.innerHTML = '<div class="empty-state">Немає тем на форумі</div>';
        return;
    }

    container.innerHTML = topics.map(topic => `
        <div class="topic-card">
            <h3 class="topic-title">${escapeHtml(topic.title)}</h3>
            <p>${escapeHtml(topic.description)}</p>
            <div class="topic-meta">
                <span class="topic-badge author">👤 ${escapeHtml(topic.author)}</span>
                <span class="topic-badge">📅 ${formatDate(topic.createdAt)}</span>
                <span class="topic-badge">💬 ${topic.postsCount || 0} відповідей</span>
            </div>
            <div class="topic-actions">
                <button class="btn" onclick="showTopicPosts(${topic.id}, '${escapeHtml(topic.title)}')">
                    Переглянути
                </button>
                <button class="btn danger" onclick="deleteTopic(${topic.id})">
                    🗑️ Видалити тему
                </button>
            </div>
        </div>
    `).join('');
}

async function deleteTopic(topicId) {
    if (!confirm('Ви впевнені що хочете видалити цю тему?')) {
        return;
    }

    try {
        const response = await fetchWithAuth(`/api/forum/topics/${topicId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage('Тему видалено!', 'success');
        closeTopicModal();
        loadForumTopics();
        loadModerationTopics();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося видалити тему: ' + error.message, 'error');
    }
}

async function deletePost(postId) {
    if (!confirm('Ви впевнені що хочете видалити цей пост?')) {
        return;
    }

    try {
        const response = await fetchWithAuth(`/api/forum/posts/${postId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage('Пост видалено!', 'success');
        closeTopicModal();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося видалити пост: ' + error.message, 'error');
    }
}

async function banUser() {
    const username = document.getElementById('ban-username').value.trim();
    if (!username) {
        showMessage('Введіть ім\'я користувача', 'error');
        return;
    }

    if (!confirm(`Ви впевнені що хочете заблокувати користувача ${username}?`)) {
        return;
    }

    try {
        const response = await fetchWithAuth(`/api/moderator/users/${username}/ban`, {
            method: 'POST'
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage(`Користувача ${username} заблоковано!`, 'success');
        document.getElementById('ban-username').value = '';
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося заблокувати користувача: ' + error.message, 'error');
    }
}

async function unbanUser() {
    const username = document.getElementById('ban-username').value.trim();
    if (!username) {
        showMessage('Введіть ім\'я користувача', 'error');
        return;
    }

    try {
        const response = await fetchWithAuth(`/api/moderator/users/${username}/unban`, {
            method: 'POST'
        });

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }

        showMessage(`Користувача ${username} розблоковано!`, 'success');
        document.getElementById('ban-username').value = '';
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося розблокувати користувача: ' + error.message, 'error');
    }
}

function formatDate(dateString) {
    if (!dateString) return 'Невідомо';
    const date = new Date(dateString);
    return date.toLocaleString('uk-UA', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showMessage(message, type = 'success') {
    const existing = document.querySelector('.alert');
    if (existing) {
        existing.remove();
    }

    const alert = document.createElement('div');
    alert.className = `alert alert-${type}`;
    alert.textContent = message;

    const main = document.querySelector('.site-main .wrap');
    if (main) {
        main.insertBefore(alert, main.firstChild);

        setTimeout(() => {
            alert.remove();
        }, 5000);
    }
}

function updateStatistics(type, count) {
    const statElement = document.getElementById(`stat-${type}`);
    if (statElement) {
        statElement.textContent = count;
    }
}

// Ініціалізація чекбоксів для матчів
function initMatchesCheckboxes() {
    // Чекбокс для майбутніх матчів
    const upcomingCheckbox = document.getElementById('show-upcoming-matches');
    if (upcomingCheckbox) {
        upcomingCheckbox.addEventListener('change', () => {
            const container = document.getElementById('upcoming-matches');
            if (container) {
                if (upcomingCheckbox.checked) {
                    loadUpcomingMatchesFromApi();
                } else {
                    container.innerHTML = '<div class="empty-state">📅 Наступний тур вимкнено</div>';
                }
            }
        });
    }

    // Чекбокс для минулих матчів
    const pastCheckbox = document.getElementById('show-past-matches');
    if (pastCheckbox) {
        pastCheckbox.addEventListener('change', () => {
            const container = document.getElementById('past-matches');
            if (container) {
                if (pastCheckbox.checked) {
                    loadPreviousMatchesFromApi();
                } else {
                    container.innerHTML = '<div class="empty-state">📅 Поточний тур вимкнено</div>';
                }
            }
        });
    }
}
