function isAuthenticated() {
    const token = localStorage.getItem('jwtToken');
    return token !== null;
}

function getToken() {
    return localStorage.getItem('jwtToken');
}

function getUsername() {
    return localStorage.getItem('username');
}

function getRole() {
    return localStorage.getItem('role');
}

function logout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    window.location.href = '/index.html';
}

function checkPageAccess(requiredRole) {
    if (!isAuthenticated()) {
        window.location.href = '/login.html';
        return false;
    }

    const userRole = getRole();

    if (requiredRole && userRole !== requiredRole) {
        switch(userRole) {
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
        return false;
    }

    return true;
}

async function authFetch(url, options = {}) {
    const token = getToken();
    
    if (!options.headers) {
        options.headers = {};
    }
    
    if (token) {
        options.headers['Authorization'] = `Bearer ${token}`;
    }
    
    const response = await fetch(url, options);

    if (response.status === 401) {
        logout();
        return null;
    }
    
    return response;
}

async function fetchWithAuth(url, options = {}) {
    return authFetch(url, options);
}

function updateUserInfo() {
    const userInfoElement = document.getElementById('userInfo');
    if (!userInfoElement) return;

    if (isAuthenticated()) {
        const username = getUsername();
        const role = getRole();
        const roleText = {
            'USER': 'Користувач',
            'MODERATOR': 'Модератор',
            'EDITOR': 'Редактор'
        }[role] || role;

        userInfoElement.innerHTML = `
            <span>👤 ${username} (${roleText})</span>
            <button onclick="logout()" class="btn-logout">Вийти</button>
        `;
    } else {
        userInfoElement.innerHTML = `
            <a href="/login.html" class="btn-login-link">Увійти</a>
            <a href="/register.html" class="btn-register-link">Реєстрація</a>
        `;
    }
}

function showForRole(elementId, allowedRoles) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const userRole = getRole();
    
    if (!isAuthenticated() || !allowedRoles.includes(userRole)) {
        element.style.display = 'none';
    } else {
        element.style.display = '';
    }
}

function showForAuthenticated(elementId) {
    const element = document.getElementById(elementId);
    if (!element) return;

    if (!isAuthenticated()) {
        element.style.display = 'none';
    } else {
        element.style.display = '';
    }
}

function showForGuest(elementId) {
    const element = document.getElementById(elementId);
    if (!element) return;

    if (isAuthenticated()) {
        element.style.display = 'none';
    } else {
        element.style.display = '';
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        isAuthenticated,
        getToken,
        getUsername,
        getRole,
        logout,
        checkPageAccess,
        authFetch,
        updateUserInfo,
        showForRole,
        showForAuthenticated,
        showForGuest
    };
}

