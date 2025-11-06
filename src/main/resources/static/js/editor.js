document.addEventListener('DOMContentLoaded', () => {
    if (!isAuthenticated()) {
        window.location.href = '/login.html';
        return;
    }

    const role = getRole();
    if (role !== 'EDITOR') {
        if (role === 'USER') {
            window.location.href = '/user.html';
        } else if (role === 'MODERATOR') {
            window.location.href = '/moderator.html';
        }
        return;
    }

    updateUserInfo();
    loadNews();
    loadMatches();
    if (typeof loadTeamsByLeague === 'function') {
        loadTeamsByLeague('UPL');
    } else {
        loadTeams();
    }
    loadForumTopics();
    loadEditorTeamsList();

    setupTabs();

    document.getElementById('showScores').addEventListener('change', () => {
        loadMatches();
    });

    setupTeamAutocomplete().catch(error => {
        console.error('Помилка ініціалізації autocomplete:', error);
    });
});

function updateUserInfo() {
    const el = document.getElementById('userInfo');
    if (!el) return;
    const username = getUsername() || '';
    el.innerHTML = `
        <span class="user-name">✍️ ${username}</span>
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
            displayNews(news.slice(0, 3), 'home-news', false);
            displayNews(news, 'all-news', false);
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
                    <span class="news-badge id">ID: ${item.id}</span>
                </div>
            </div>
        </article>
    `).join('');
}

async function loadMatches() {
    try {
        const response = await fetch('/api/matches');
        if (!response.ok) throw new Error('Помилка завантаження матчів');
        
        const matches = await response.json();
        const showScores = document.getElementById('showScores')?.checked ?? true;

        const now = new Date();
        const upcomingMatches = matches
            .filter(m => new Date(m.kickoffAt) > now)
            .slice(0, 6);
        
        if (typeof renderMatchesList === 'function') {
            renderMatchesList(upcomingMatches, 'home-matches', showScores);
            renderMatchesList(matches, 'all-matches', showScores);
        } else {
            displayMatches(upcomingMatches, 'home-matches', showScores, false);
            displayMatches(matches, 'all-matches', showScores, false);
        }

        updateStatistics('matches', matches.length);
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося завантажити матчі', 'error');
    }
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
        
        return `
            <div class="match-card">
                <div class="match-teams">
                    <span class="team-name team-home">${escapeHtml(match.homeTeam || 'Команда 1')}</span>
                    <span class="match-score">${scoreDisplay}</span>
                    <span class="team-name team-away">${escapeHtml(match.awayTeam || 'Команда 2')}</span>
                </div>
                <div class="match-info">
                    <span class="info-badge">📅 ${formatDate(match.kickoffAt)}</span>
                    <span class="info-badge">ID: ${match.id}</span>
                </div>
            </div>
        `;
    }).join('');
}

async function loadTeams() {
    try {
        const actualResp = await fetch('/api/teams/actual');
        if (!actualResp.ok) throw new Error('Помилка завантаження актуальних команд');

        const leaguesMap = await actualResp.json();
        const actualTeams = Object.entries(leaguesMap)
            .flatMap(([league, teams]) => (teams || []).map(t => ({ ...t, league })));

        let userTeams = [];
        try {
            const userResp = await fetch('/api/teams');
            if (userResp.ok) {
                const arr = await userResp.json();
                if (Array.isArray(arr)) userTeams = arr;
            }
        } catch (_) {}

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
            ${team.league ? `<p class=\"team-city\">Ліга: ${escapeHtml(team.league)}</p>` : ''}
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

async function loadEditorTeamsList() {
    try {
        const response = await fetch('/api/teams');
        if (!response.ok) throw new Error('Помилка завантаження команд');
        
        const teams = await response.json();
        
        const container = document.getElementById('editor-teams-list');
        if (!container) return;
        
        container.innerHTML = `
            <div style="background: #f5f5f5; padding: 15px; border-radius: 5px; max-height: 300px; overflow-y: auto;">
                ${teams.map(team => `
                    <div style="padding: 8px; border-bottom: 1px solid #ddd;">
                        <strong>ID: ${team.id}</strong> - ${escapeHtml(team.name)}
                    </div>
                `).join('')}
            </div>
        `;
    } catch (error) {
        console.error('Помилка:', error);
    }
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
                            <div class="forum-topic" style="margin-bottom: 15px;">
                                <p>${escapeHtml(post.content)}</p>
                                <div class="topic-meta">
                                    <span>👤 ${escapeHtml(post.author)}</span>
                                    <span>📅 ${formatDate(post.createdAt)}</span>
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
                        <button type="button" class="btn danger" onclick="closeTopicModal()">Закрити</button>
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

async function createNews(event) {
    event.preventDefault();
    
    const title = document.getElementById('news-create-title').value;
    const content = document.getElementById('news-create-content').value;
    
    try {
        const response = await fetchWithAuth('/api/news', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ title, content })
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Новину створено успішно!', 'success');
        document.getElementById('news-create-title').value = '';
        document.getElementById('news-create-content').value = '';
        loadNews();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося створити новину: ' + error.message, 'error');
    }
}

async function updateNews(event) {
    event.preventDefault();
    
    const id = document.getElementById('news-update-id').value;
    const title = document.getElementById('news-update-title').value;
    const content = document.getElementById('news-update-content').value;
    
    try {
        const response = await fetchWithAuth(`/api/news/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ title, content })
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Новину оновлено успішно!', 'success');
        document.getElementById('news-update-id').value = '';
        document.getElementById('news-update-title').value = '';
        document.getElementById('news-update-content').value = '';
        loadNews();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося оновити новину: ' + error.message, 'error');
    }
}

async function deleteNews(event) {
    event.preventDefault();
    
    const id = document.getElementById('news-delete-id').value;
    
    if (!confirm(`Ви впевнені що хочете видалити новину #${id}?`)) {
        return;
    }
    
    try {
        const response = await fetchWithAuth(`/api/news/${id}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Новину видалено успішно!', 'success');
        document.getElementById('news-delete-id').value = '';
        loadNews();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося видалити новину: ' + error.message, 'error');
    }
}

async function createMatch(event) {
    event.preventDefault();

    const homeId = document.getElementById('match-create-home-id').value;
    const awayId = document.getElementById('match-create-away-id').value;
    const kickoffAt = document.getElementById('match-create-kickoff').value;

    console.log('Створення матчу:', { homeId, awayId, kickoffAt });

    if (!homeId) {
        showMessage('Будь ласка, виберіть домашню команду зі списку', 'error');
        return;
    }
    if (!awayId) {
        showMessage('Будь ласка, виберіть гостьову команду зі списку', 'error');
        return;
    }

    const homeTeam = window.teamsCache.find(team => team.id == homeId);
    const awayTeam = window.teamsCache.find(team => team.id == awayId);

    if (!homeTeam || !awayTeam) {
        showMessage('Помилка: команда не знайдена', 'error');
        return;
    }

    console.log('Створення матчу:', {
        homeTeam: homeTeam.name,
        awayTeam: awayTeam.name,
        kickoffAt: kickoffAt
    });

    try {
        const response = await fetchWithAuth('/api/matches', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                homeTeam: homeTeam.name,
                awayTeam: awayTeam.name,
                kickoffAt: kickoffAt
            })
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Матч створено успішно!', 'success');
        document.getElementById('match-create-home-id').value = '';
        document.getElementById('match-create-away-id').value = '';
        document.getElementById('match-create-kickoff').value = '';
        loadMatches();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося створити матч: ' + error.message, 'error');
    }
}

async function updateMatchScore(event) {
    event.preventDefault();
    
    const id = document.getElementById('match-update-id').value;
    const homeScore = document.getElementById('match-update-home-score').value;
    const awayScore = document.getElementById('match-update-away-score').value;
    
    try {
        const response = await fetchWithAuth(`/api/matches/${id}/score`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                homeScore: parseInt(homeScore),
                awayScore: parseInt(awayScore)
            })
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Рахунок оновлено успішно!', 'success');
        document.getElementById('match-update-id').value = '';
        document.getElementById('match-update-home-score').value = '';
        document.getElementById('match-update-away-score').value = '';
        loadMatches();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося оновити рахунок: ' + error.message, 'error');
    }
}

async function deleteMatch(event) {
    event.preventDefault();
    
    const id = document.getElementById('match-delete-id').value;
    
    if (!confirm(`Ви впевнені що хочете видалити матч #${id}?`)) {
        return;
    }
    
    try {
        const response = await fetchWithAuth(`/api/matches/${id}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            const error = await response.text();
            throw new Error(error);
        }
        
        showMessage('Матч видалено успішно!', 'success');
        document.getElementById('match-delete-id').value = '';
        loadMatches();
    } catch (error) {
        console.error('Помилка:', error);
        showMessage('Не вдалося видалити матч: ' + error.message, 'error');
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

async function setupTeamAutocomplete() {
    try {
        console.log('Ініціалізація autocomplete...');
        await loadTeamsForAutocomplete();
        console.log('Autocomplete ініціалізований успішно');

        setupLeagueFilter();

        const homeInput = document.getElementById('match-create-home-input');
        const homeDropdown = document.getElementById('match-create-home-dropdown');
        const homeHidden = document.getElementById('match-create-home-id');

        if (homeInput && homeDropdown) {
            console.log('Налаштування event listener для домашньої команди');
            homeInput.addEventListener('input', () => {
                console.log('Input event для домашньої команди');
                const filteredTeams = getFilteredTeams();
                showTeamAutocomplete(homeInput, homeDropdown, homeHidden, filteredTeams);
            });

            homeInput.addEventListener('focus', () => {
                if (homeInput.value.trim()) {
                    console.log('Focus event для домашньої команди');
                    const filteredTeams = getFilteredTeams();
                    showTeamAutocomplete(homeInput, homeDropdown, homeHidden, filteredTeams);
                }
            });
        }

        const awayInput = document.getElementById('match-create-away-input');
        const awayDropdown = document.getElementById('match-create-away-dropdown');
        const awayHidden = document.getElementById('match-create-away-id');

        if (awayInput && awayDropdown) {
            awayInput.addEventListener('input', () => {
                const filteredTeams = getFilteredTeams();
                showTeamAutocomplete(awayInput, awayDropdown, awayHidden, filteredTeams);
            });

            awayInput.addEventListener('focus', () => {
                if (awayInput.value.trim()) {
                    const filteredTeams = getFilteredTeams();
                    showTeamAutocomplete(awayInput, awayDropdown, awayHidden, filteredTeams);
                }
            });
        }
    } catch (error) {
        console.error('Помилка ініціалізації autocomplete:', error);
    }
}

function setupLeagueFilter() {
    const leagueSelect = document.getElementById('match-create-league');
    if (leagueSelect) {
        leagueSelect.addEventListener('change', () => {
            console.log('Змінено лігу на:', leagueSelect.value);
            clearTeamSelections();
        });
    }
}

function getFilteredTeams() {
    const leagueSelect = document.getElementById('match-create-league');
    const selectedLeague = leagueSelect ? leagueSelect.value : '';

    if (!selectedLeague || selectedLeague === '') {
        return window.teamsCache || [];
    }

    return (window.teamsCache || []).filter(team => team.league === selectedLeague);
}

function clearTeamSelections() {
    const homeInput = document.getElementById('match-create-home-input');
    const homeHidden = document.getElementById('match-create-home-id');
    const awayInput = document.getElementById('match-create-away-input');
    const awayHidden = document.getElementById('match-create-away-id');

    if (homeInput) homeInput.value = '';
    if (homeHidden) homeHidden.value = '';
    if (awayInput) awayInput.value = '';
    if (awayHidden) awayHidden.value = '';
}

function showTeamAutocomplete(input, dropdown, hiddenInput, teams) {
    const query = input.value.trim().toLowerCase();
    console.log('Autocomplete запит:', query, 'команд в кеші:', teams.length);

    if (!query) {
        dropdown.classList.remove('show');
        return;
    }

    const filtered = teams.filter(team =>
        team.name.toLowerCase().includes(query)
    );
    console.log('Відфільтровано команд:', filtered.length);

    if (filtered.length === 0) {
        dropdown.innerHTML = '<div class="autocomplete-empty">Команду не знайдено</div>';
        dropdown.classList.add('show');
        return;
    }

    dropdown.innerHTML = filtered.map(team => `
        <div class="autocomplete-item" data-id="${team.id}" data-name="${team.name}">
            ${team.name} <small style="color: #666;">(${team.league})</small>
        </div>
    `).join('');

    dropdown.classList.add('show');

    dropdown.querySelectorAll('.autocomplete-item').forEach(item => {
        item.addEventListener('click', () => {
            console.log('Вибрано команду:', item.dataset.name, 'ID:', item.dataset.id);
            input.value = item.dataset.name;
            hiddenInput.value = item.dataset.id;
            console.log('Встановлено hidden поле:', hiddenInput.id, '=', hiddenInput.value);
            dropdown.classList.remove('show');
        });
    });
}

async function loadTeamsForAutocomplete() {
    try {
        console.log('Завантаження команд для autocomplete...');
        const response = await fetch('/api/teams/actual');
        if (!response.ok) throw new Error('Не вдалося завантажити команди');

        const leaguesData = await response.json();
        console.log('Отримано ліг:', Object.keys(leaguesData).length);

        window.teamsCache = [];
        for (const league in leaguesData) {
            const teams = leaguesData[league];
            console.log(`Ліга ${league}: ${teams.length} команд`);
            teams.forEach(team => {
                window.teamsCache.push({
                    id: team.id,
                    name: team.name,
                    league: team.league
                });
            });
        }

        console.log('Загалом збережено в кеш:', window.teamsCache.length);

    } catch (error) {
        console.error('Помилка завантаження команд для autocomplete:', error);
    }
}

