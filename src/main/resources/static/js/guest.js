document.addEventListener('DOMContentLoaded', () => {
    if (isAuthenticated()) {
        const role = getRole();
        switch(role) {
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
    loadNews();
    loadMatches();
    loadTeams();
    loadForumTopics();

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
        const news = await response.json();
        
        displayNewsList(news);
    } catch (error) {
        console.error('Помилка завантаження новин:', error);
    }
}

function displayNewsList(newsList) {
    const container = document.getElementById('news-list');
    if (!container) return;
    
    if (newsList.length === 0) {
        container.innerHTML = '<p class="empty-state">Новин поки немає</p>';
        return;
    }
    
    container.innerHTML = newsList.map(news => `
        <div class="news-card">
            <h3>${escapeHtml(news.title)}</h3>
            <p>${escapeHtml(news.content)}</p>
            <div class="news-meta">
                <span>❤️ ${news.likes || 0} вподобайок</span>
                <span>ID: ${news.id}</span>
            </div>
        </div>
    `).join('');
}

async function loadMatches() {
    try {
        const response = await fetch('/api/matches');
        const matches = await response.json();
        
        displayMatchesList(matches);
    } catch (error) {
        console.error('Помилка завантаження матчів:', error);
    }
}

function displayMatchesList(matchesList) {
    const container = document.getElementById('matches-list');
    if (!container) return;
    
    if (matchesList.length === 0) {
        container.innerHTML = '<p class="empty-state">Матчів поки немає</p>';
        return;
    }
    
    container.innerHTML = matchesList.map(match => `
        <div class="match-card">
            <div class="match-teams">
                <span class="team">${escapeHtml(match.homeTeam)}</span>
                <span class="vs">vs</span>
                <span class="team">${escapeHtml(match.awayTeam)}</span>
            </div>
            <div class="match-score">
                <span class="score">${match.homeScore !== null ? match.homeScore : '-'} : ${match.awayScore !== null ? match.awayScore : '-'}</span>
            </div>
            <div class="match-time">
                📅 ${formatDateTime(match.kickoffAt)}
            </div>
        </div>
    `).join('');
}

async function loadTeams() {
    try {
        const response = await fetch('/api/teams');
        const teams = await response.json();
        
        displayTeamsList(teams);
    } catch (error) {
        console.error('Помилка завантаження команд:', error);
    }
}

function displayTeamsList(teamsList) {
    const container = document.getElementById('teams-list');
    if (!container) return;
    
    if (teamsList.length === 0) {
        container.innerHTML = '<p class="empty-state">Команд поки немає</p>';
        return;
    }
    
    container.innerHTML = teamsList.map(team => `
        <div class="team-card">
            <div class="team-emblem">${team.colors || '⚽'}</div>
            <h3>${escapeHtml(team.name)}</h3>
            <p class="team-info">
                ${team.league ? `🏆 ${escapeHtml(team.league)}` : ''}
                ${team.city ? `📍 ${escapeHtml(team.city)}` : ''}
            </p>
        </div>
    `).join('');
}

async function loadForumTopics() {
    try {
        const response = await fetch('/api/forum/topics');
        const topics = await response.json();
        
        displayForumTopics(topics);
    } catch (error) {
        console.error('Помилка завантаження форуму:', error);
    }
}

function displayForumTopics(topics) {
    const container = document.getElementById('forum-list');
    if (!container) return;
    
    if (topics.length === 0) {
        container.innerHTML = '<p class="empty-state">Тем на форумі поки немає</p>';
        return;
    }
    
    container.innerHTML = topics.map(topic => `
        <div class="forum-topic">
            <h3>${escapeHtml(topic.title)}</h3>
            <div class="topic-meta">
                <span>👤 ${escapeHtml(topic.author)}</span>
                <span>💬 ${topic.posts ? topic.posts.length : 0} коментарів</span>
            </div>
        </div>
    `).join('');
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

