// Функції для роботи з майбутніми та минулими матчами з API

// Глобальні змінні для зберігання всіх матчів
let allUpcomingMatches = [];
let allPreviousMatches = [];

async function loadUpcomingMatchesFromApi(league = null) {
    return loadMatchesFromApi('upcoming', '/api/teams/matches/upcoming', 'upcoming-matches', league);
}

async function loadPreviousMatchesFromApi(league = null) {
    return loadMatchesFromApi('previous', '/api/teams/matches/previous', 'past-matches', league);
}

async function loadMatchesFromApi(type, endpoint, containerId, league = null) {
    try {
        const response = await fetch(endpoint);
        if (!response.ok) throw new Error(`Помилка завантаження ${type === 'upcoming' ? 'майбутніх' : 'минулих'} матчів`);

        const data = await response.json();
        const allMatches = data.matches || [];

        // Зберігаємо всі матчі в глобальні змінні
        if (type === 'upcoming') {
            allUpcomingMatches = allMatches;
        } else {
            allPreviousMatches = allMatches;
        }

        // Фільтруємо матчі по лізі (якщо вказана)
        const filteredMatches = league ? allMatches.filter(m => m.league === league) : allMatches;

        // Відображаємо в контейнері
        displayMatchesByType(filteredMatches, containerId, type);

        return filteredMatches;
    } catch (error) {
        console.error(`❌ Помилка завантаження ${type === 'upcoming' ? 'майбутніх' : 'минулих'} матчів:`, error);
        const container = document.getElementById(containerId);
        if (container) {
            container.innerHTML = `<div class="empty-state">⚠️ Не вдалося завантажити ${type === 'upcoming' ? 'майбутні' : 'минулі'} матчі</div>`;
        }
        return [];
    }
}

function displayMatchesByType(matches, containerId, type) {
    const container = document.getElementById(containerId);
    if (!container) return;

    // Забезпечуємо grid layout
    if (!container.style.display || container.style.display === 'none') {
        container.style.display = 'grid';
    }

    if (matches.length === 0) {
        const emptyMessage = type === 'upcoming'
            ? '📅 Немає запланованих матчів поточного туру'
            : '📅 Немає завершених матчів попереднього туру';
        container.innerHTML = `<div class="empty-state">${emptyMessage}</div>`;
        return;
    }

    const matchesHtml = matches.map(match => {
        const homeTeam = match.homeTeam || {};
        const awayTeam = match.awayTeam || {};
        const kickoffAt = match.kickoffAt ? new Date(match.kickoffAt) : null;
        const leagueEmoji = getLeagueEmoji(match.league);
        const score = match.score || {};

        const cardClass = type === 'upcoming' ? 'upcoming-match' : 'past-match';

        return `
            <div class="match-card ${cardClass}">
                <div class="match-league-badge">${leagueEmoji} ${match.league || ''}</div>
                <div class="match-teams">
                    ${homeTeam.crest ? `<img src="${homeTeam.crest}" alt="${homeTeam.name}" class="team-crest">` : ''}
                    <span class="team-name team-home">${escapeHtml(homeTeam.name || 'Команда 1')}</span>
                    ${type === 'previous' && score.home !== undefined && score.away !== undefined
                        ? `<span class="match-score">${score.home} - ${score.away}</span>`
                        : `<span class="match-score match-vs">VS</span>`
                    }
                    <span class="team-name team-away">${escapeHtml(awayTeam.name || 'Команда 2')}</span>
                    ${awayTeam.crest ? `<img src="${awayTeam.crest}" alt="${awayTeam.name}" class="team-crest">` : ''}
                </div>
                <div class="match-info">
                    <span class="info-badge">📅 ${kickoffAt ? formatDate(kickoffAt) : 'TBD'}</span>
                    ${match.matchday ? `<span class="info-badge">🎯 Тур ${match.matchday}</span>` : ''}
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML += matchesHtml;

    console.log(`✅ HTML згенеровано і встановлено, довжина: ${container.innerHTML.length}`);
}

function getLeagueEmoji(league) {
    const emojis = {
        'UPL': '🇺🇦',
        'UCL': '⭐',
        'EPL': '🏴',
        'LaLiga': '🇪🇸',
        'Bundesliga': '🇩🇪',
        'SerieA': '🇮🇹',
        'Ligue1': '🇫🇷'
    };
    return emojis[league] || '⚽';
}

// Функція для оновлення матчів при зміні ліги
function reloadMatchesForLeague(league) {
    // Оновлюємо майбутні матчі (якщо чекбокс активний і контейнер видимий)
    const upcomingCheckbox = document.getElementById('show-upcoming-matches');
    const upcomingContainer = document.getElementById('upcoming-matches');
    if (upcomingCheckbox && upcomingCheckbox.checked && upcomingContainer && upcomingContainer.style.display !== 'none' && allUpcomingMatches.length > 0) {
        const filteredUpcoming = allUpcomingMatches.filter(m => m.league === league);
        displayMatchesByType(filteredUpcoming, 'upcoming-matches', 'upcoming');
    }
    
    // Оновлюємо минулі матчі (якщо чекбокс активний і контейнер видимий)
    const pastCheckbox = document.getElementById('show-past-matches');
    const pastContainer = document.getElementById('past-matches');
    if (pastCheckbox && pastCheckbox.checked && pastContainer && pastContainer.style.display !== 'none' && allPreviousMatches.length > 0) {
        const filteredPast = allPreviousMatches.filter(m => m.league === league);
        displayMatchesByType(filteredPast, 'past-matches', 'previous');
    }
}

// Ініціалізація обробників чекбоксів матчів
function initUpcomingMatchesCheckbox() {
    console.log('🔧 Ініціалізація обробників чекбоксів матчів...');
    
    const showUpcomingCheckbox = document.getElementById('show-upcoming-matches');
    if (showUpcomingCheckbox) {
        console.log('✅ Чекбокс show-upcoming-matches знайдено');
        showUpcomingCheckbox.addEventListener('change', (e) => {
            const container = document.getElementById('upcoming-matches');
            if (container) {
                container.style.display = e.target.checked ? 'grid' : 'none';
                if (e.target.checked) {
                    // Якщо є currentLeague, фільтруємо по ній
                    const league = typeof currentLeague !== 'undefined' ? currentLeague : null;
                    loadUpcomingMatchesFromApi(league);
                }
            }
        });
    } else {
        console.warn('⚠️ Чекбокс show-upcoming-matches НЕ знайдено!');
    }
    
    const showPastCheckbox = document.getElementById('show-past-matches');
    if (showPastCheckbox) {
        console.log('✅ Чекбокс show-past-matches знайдено');
        showPastCheckbox.addEventListener('change', (e) => {
            const container = document.getElementById('past-matches');
            if (container) {
                container.style.display = e.target.checked ? 'grid' : 'none';
                if (e.target.checked) {
                    // Якщо є currentLeague, фільтруємо по ній
                    const league = typeof currentLeague !== 'undefined' ? currentLeague : null;
                    loadPreviousMatchesFromApi(league);
                }
            }
        });
    } else {
        console.warn('⚠️ Чекбокс show-past-matches НЕ знайдено!');
    }
    
    console.log('✅ Ініціалізація чекбоксів завершена');
}

