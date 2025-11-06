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
    if (typeof loadTeamsByLeague === 'function') {
        loadTeamsByLeague('UPL');
    } else {
        loadTeams();
    }
    loadForumTopics();
    loadModerationTopics();

    setupTabs();

    document.getElementById('showScores').addEventListener('change', () => {
        loadMatches();
    });
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
            displayMatches(upcomingMatches, 'home-matches', showScores, true);
            displayMatches(matches, 'all-matches', showScores, true);
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

