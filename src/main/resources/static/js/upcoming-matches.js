// Client-side helpers for rendering league-specific matches from the API cache

let allUpcomingMatches = [];
let allPreviousMatches = [];
let lastUpcomingLeague = null;
let lastPreviousLeague = null;

function resolveLeagueCode(league) {
    if (league && typeof league === 'string' && league.trim().length > 0) {
        return league.trim();
    }
    if (typeof currentLeague !== 'undefined' && currentLeague) {
        return currentLeague;
    }
    return null;
}

async function loadUpcomingMatchesFromApi(league = null) {
    return loadMatchesFromApi('upcoming', '/api/teams/matches/upcoming', 'upcoming-matches', resolveLeagueCode(league));
}

async function loadPreviousMatchesFromApi(league = null) {
    return loadMatchesFromApi('previous', '/api/teams/matches/previous', 'past-matches', resolveLeagueCode(league));
}

async function loadMatchesFromApi(type, endpoint, containerId, league = null) {
    try {
        const response = await fetch(endpoint);
        if (!response.ok) throw new Error(`Failed to load ${type} matches`);

        const data = await response.json();
        const allMatches = data.matches || [];
        const targetLeague = resolveLeagueCode(league);
        const filteredMatches = targetLeague ? allMatches.filter(m => m.league === targetLeague) : allMatches;

        if (type === 'upcoming') {
            allUpcomingMatches = filteredMatches;
            lastUpcomingLeague = targetLeague;
        } else {
            allPreviousMatches = filteredMatches;
            lastPreviousLeague = targetLeague;
        }

        if (isMatchesSectionActive(containerId)) {
            displayMatchesByType(filteredMatches, containerId, type);
        }

        return filteredMatches;
    } catch (error) {
        console.error(`Error loading ${type} matches:`, error);
        const container = document.getElementById(containerId);
        if (container && isMatchesSectionActive(containerId)) {
            container.innerHTML = `<div class="empty-state">Unable to load ${type === 'upcoming' ? 'upcoming' : 'previous'} matches</div>`;
        }
        return [];
    }
}

function displayMatchesByType(matches, containerId, type) {
    const container = document.getElementById(containerId);
    if (!container || !isMatchesSectionActive(containerId)) return;

    if (!matches.length) {
        const emptyMessage = type === 'upcoming'
            ? 'No upcoming matches for the selected league'
            : 'No previous matches for the selected league';
        container.innerHTML = `<div class="empty-state">${emptyMessage}</div>`;
        return;
    }

    const matchesHtml = matches.map(match => {
        const homeTeam = match.homeTeam || {};
        const awayTeam = match.awayTeam || {};
        const kickoffAt = match.kickoffAt ? new Date(match.kickoffAt) : null;
        const leagueBadge = getLeagueEmoji(match.league);
        const score = match.score || {};
        const cardClass = type === 'upcoming' ? 'upcoming-match' : 'past-match';
        const scoreMarkup = (type === 'previous' && score.home !== undefined && score.away !== undefined)
            ? `<span class="match-score">${score.home} - ${score.away}</span>`
            : '<span class="match-score match-vs">VS</span>';

        return `
            <div class="match-card ${cardClass}">
                <div class="match-league-badge">${leagueBadge} ${match.league || ''}</div>
                <div class="match-teams">
                    ${homeTeam.crest ? `<img src="${homeTeam.crest}" alt="${homeTeam.name}" class="team-crest">` : ''}
                    <span class="team-name team-home">${escapeHtml(homeTeam.name || 'Home Team')}</span>
                    ${scoreMarkup}
                    <span class="team-name team-away">${escapeHtml(awayTeam.name || 'Away Team')}</span>
                    ${awayTeam.crest ? `<img src="${awayTeam.crest}" alt="${awayTeam.name}" class="team-crest">` : ''}
                </div>
                <div class="match-info">
                    <span class="info-badge">Kick-off: ${kickoffAt ? formatDate(kickoffAt) : 'TBD'}</span>
                    ${match.matchday ? `<span class="info-badge">Matchday ${match.matchday}</span>` : ''}
                </div>
            </div>
        `;
    }).join('');

    container.innerHTML = matchesHtml;
}

function isMatchesSectionActive(containerId) {
    if (containerId === 'upcoming-matches') {
        const checkbox = document.getElementById('show-upcoming-matches');
        return checkbox ? checkbox.checked : true;
    }
    if (containerId === 'past-matches') {
        const checkbox = document.getElementById('show-past-matches');
        return checkbox ? checkbox.checked : true;
    }
    return true;
}

function getLeagueEmoji(league) {
    const emojis = {
        'UCL': '⭐',
        'UCL': '⭐',
        'EPL': '🏴',
        'LaLiga': '🇪🇸',
        'Bundesliga': '🇩🇪',
        'SerieA': '🇮🇹',
        'Ligue1': '🇫🇷'
    };
    return emojis[league] || '⚽';
}

function reloadMatchesForLeague(league) {
    const targetLeague = resolveLeagueCode(league);
    const upcomingCheckbox = document.getElementById('show-upcoming-matches');
    if (upcomingCheckbox && upcomingCheckbox.checked) {
        if (lastUpcomingLeague === targetLeague && allUpcomingMatches.length > 0) {
            displayMatchesByType(allUpcomingMatches, 'upcoming-matches', 'upcoming');
        } else {
            loadUpcomingMatchesFromApi(targetLeague);
        }
    }

    const pastCheckbox = document.getElementById('show-past-matches');
    if (pastCheckbox && pastCheckbox.checked) {
        if (lastPreviousLeague === targetLeague && allPreviousMatches.length > 0) {
            displayMatchesByType(allPreviousMatches, 'past-matches', 'previous');
        } else {
            loadPreviousMatchesFromApi(targetLeague);
        }
    }
}

function initUpcomingMatchesCheckbox() {
    console.log('Initializing league match toggles...');
    const upcomingCheckbox = document.getElementById('show-upcoming-matches');
    const pastCheckbox = document.getElementById('show-past-matches');

    if (upcomingCheckbox) {
        const container = document.getElementById('upcoming-matches');
        upcomingCheckbox.addEventListener('change', () => {
            if (!container) return;
            if (upcomingCheckbox.checked) {
                container.style.display = '';
                const league = resolveLeagueCode();
                if (lastUpcomingLeague === league && allUpcomingMatches.length > 0) {
                    displayMatchesByType(allUpcomingMatches, 'upcoming-matches', 'upcoming');
                } else {
                    loadUpcomingMatchesFromApi(league);
                }
            } else {
                container.style.display = 'none';
                container.innerHTML = '';
            }
        });
    }

    if (pastCheckbox) {
        const container = document.getElementById('past-matches');
        pastCheckbox.addEventListener('change', () => {
            if (!container) return;
            if (pastCheckbox.checked) {
                container.style.display = '';
                const league = resolveLeagueCode();
                if (lastPreviousLeague === league && allPreviousMatches.length > 0) {
                    displayMatchesByType(allPreviousMatches, 'past-matches', 'previous');
                } else {
                    loadPreviousMatchesFromApi(league);
                }
            } else {
                container.style.display = 'none';
                container.innerHTML = '';
            }
        });
    }
}

function formatDate(date) {
    return date.toLocaleString('uk-UA', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit'
    });
}
