document.addEventListener('DOMContentLoaded', () => {
    if (isAuthenticated()) {
        const role = getRole();
        switch (role) {
            case 'MODERATOR':
                window.location.href = '/moderator.html';
                break;
            case 'EDITOR':
                window.location.href = '/editor.html';
                break;
            case 'USER':
            default:
                window.location.href = '/user.html';
                break;
        }
        return;
    }

    updateUserInfo();

    // Load initial data
    loadNews();
    loadMatches();
    loadForumTopics();

    // Load upcoming matches notifications
    if (typeof loadUpcomingMatchesNotifications === 'function') {
        loadUpcomingMatchesNotifications();
    }

    // Initialize teams by league functionality
    if (typeof loadTeamsByLeague === 'function') {
        loadTeamsByLeague('UCL');
    }

    // Initialize score toggle for matches - додаємо з затримкою, щоб елемент точно існував
    setTimeout(() => {
        const showScoresElement = document.getElementById('showScores');
        if (showScoresElement) {
            showScoresElement.addEventListener('change', () => {
                loadMatches();
            });
        }
    }, 100);

    setupTabs();
});

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

    const targetPanel = document.getElementById(tabName);
    if (targetPanel) {
        targetPanel.classList.add('active');
    }

    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    const activeLink = document.querySelector(`.nav-link[data-tab="${tabName}"]`);
    if (activeLink) {
        activeLink.classList.add('active');
    }
}

async function loadNews() {
    try {
        const response = await fetch('/api/news');
        if (response.ok) {
            const news = await response.json();
            const newsContainer = document.getElementById('all-news');
            if (newsContainer && Array.isArray(news)) {
                if (news.length > 0) {
                    if (typeof renderNewsList === 'function') {
                        renderNewsList(news, 'all-news');
                    }
                } else {
                    newsContainer.innerHTML = '<p class="empty-state">Новин поки немає</p>';
                }
            }
        }
    } catch (error) {
        console.error('Помилка завантаження новин:', error);
    }
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

    } catch (error) {
        console.error('Помилка:', error);
        if (container) container.innerHTML = '<div class="error-state">Помилка завантаження</div>';
    }
}

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
        matchday: m.matchday,
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
                <span class="info-badge">📅 ${formatDateTime(match.kickoffAt)}</span>
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

function updateStatistics(type, count) {
    const statElement = document.getElementById(`stat-${type}`);
    if (statElement) {
        statElement.textContent = count;
    }
}

async function loadForumTopics() {
    try {
        const response = await fetch('/api/forum/topics');
        if (response.ok) {
            const topics = await response.json();
            if (typeof renderForumTopics === 'function') {
                renderForumTopics(topics);
            }
        }
    } catch (error) {
        console.error('Помилка завантаження тем форуму:', error);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatDateTime(dateTimeString) {
    if (!dateTimeString) return '-';
    const date = new Date(dateTimeString);
    return date.toLocaleString('uk-UA', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}
