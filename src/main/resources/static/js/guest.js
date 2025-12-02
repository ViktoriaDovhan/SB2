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

    // Initialize score toggle for matches
    const showScoresCheckbox = document.getElementById('showScores');
    if (showScoresCheckbox) {
        showScoresCheckbox.addEventListener('change', () => {
            loadMatches();
        });
    }

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

async function loadMatches() {
    try {
        const dbMatchesP = fetch('/api/matches').then(r => r.json());
        const externalMatchesP = fetch('/api/teams/matches/all').then(r => r.json());

        const [dbMatches, externalData] = await Promise.all([dbMatchesP, externalMatchesP]);

        let allMatches = [];

        // Process DB matches
        if (Array.isArray(dbMatches)) {
            allMatches = [...dbMatches];
        }

        // Process external matches
        if (externalData && Array.isArray(externalData.matches)) {
            const normalized = externalData.matches.map(m => ({
                id: m.id,
                homeTeam: m.homeTeam || 'Unknown',
                awayTeam: m.awayTeam || 'Unknown',
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

        // Check if scores should be shown
        const showScoresCheckbox = document.getElementById('showScores');
        const showScores = showScoresCheckbox ? showScoresCheckbox.checked : true;

        // Render matches
        if (typeof renderMatchesList === 'function') {
            renderMatchesList(uniqueMatches, 'all-matches', showScores);
        }

    } catch (error) {
        console.error('Помилка завантаження матчів:', error);
        const container = document.getElementById('all-matches');
        if (container) {
            container.innerHTML = '<p class="empty-state">Помилка завантаження матчів</p>';
        }
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
