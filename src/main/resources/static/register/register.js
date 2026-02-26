const form = document.getElementById('regForm');
const err = document.getElementById('err');
const ok = document.getElementById('ok');

function showError(msg) {
    ok.style.display = 'none';
    err.textContent = msg;
    err.style.display = 'block';
}

function showOk(msg) {
    err.style.display = 'none';
    ok.textContent = msg;
    ok.style.display = 'block';
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const firstName = document.getElementById('firstName').value.trim();
    const lastName = document.getElementById('lastName').value.trim();
    const middleName = document.getElementById('middleName').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const password2 = document.getElementById('password2').value;

    if (password !== password2) {
        showError('Пароли не совпадают');
        return;
    }
    if (password.length < 6) {
        showError('Пароль должен быть не короче 6 символов');
        return;
    }

    const payload = { email, password, firstName, lastName, middleName };

    try {
        const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showOk('Регистрация успешна. Сейчас перенаправим на вход...');
            setTimeout(() => window.location.href = '/login/index.html', 900);
            return;
        }

        const text = await res.text();
        if (res.status === 400 || res.status === 409) {
            showError(text || 'Пользователь с таким email уже существует');
        } else {
            showError(text || `Ошибка регистрации (HTTP ${res.status})`);
        }
    } catch (e) {
        showError('Сетевая ошибка. Проверь, что сервер запущен.');
    }
});
