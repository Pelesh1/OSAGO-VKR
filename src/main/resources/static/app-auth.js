async function loadMe() {
    const token = sessionStorage.getItem('auth');
    if (!token) return null;

    const res = await fetch('/api/me', {
        headers: { 'Authorization': 'Basic ' + token }
    });

    if (!res.ok) return null;
    return await res.json();
}

function logout() {
    sessionStorage.removeItem('auth');
    window.location.href = '/';
}

document.addEventListener('DOMContentLoaded', async () => {
    console.log('auth token:', sessionStorage.getItem('auth'));
    const loginLink = document.getElementById('loginLink');
    const userLabel = document.getElementById('userLabel');
    const logoutLink = document.getElementById('logoutLink');

    const me = await loadMe();
    console.log('me:', me);

    if (me) {
        if (loginLink) loginLink.style.display = 'none';
        if (userLabel) {
            userLabel.textContent = me.shortFio; // "Подрядов Д.Д."
            userLabel.style.display = 'inline';
        }
        if (logoutLink) {
            logoutLink.style.display = 'inline';
            logoutLink.addEventListener('click', (e) => {
                e.preventDefault();
                logout();
            });
        }
    } else {
        // не залогинен — показываем "Войти"
        if (loginLink) loginLink.style.display = 'inline';
        if (userLabel) userLabel.style.display = 'none';
        if (logoutLink) logoutLink.style.display = 'none';
    }
});
