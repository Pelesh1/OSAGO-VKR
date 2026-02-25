const form = document.getElementById("resetForm");
const err = document.getElementById("err");
const ok = document.getElementById("ok");

function showError(msg) {
    ok.style.display = "none";
    err.textContent = msg;
    err.style.display = "block";
}

function showSuccess(msg) {
    err.style.display = "none";
    ok.textContent = msg;
    ok.style.display = "block";
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const email = document.getElementById("email").value.trim();
    const newPassword = document.getElementById("newPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;

    if (!email || !newPassword || !confirmPassword) {
        showError("Заполните все поля");
        return;
    }
    if (newPassword.length < 6) {
        showError("Пароль должен быть не короче 6 символов");
        return;
    }
    if (newPassword !== confirmPassword) {
        showError("Пароли не совпадают");
        return;
    }

    try {
        const res = await fetch("/api/auth/reset-password", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, newPassword, confirmPassword })
        });

        if (res.ok) {
            showSuccess("Пароль успешно изменен. Теперь можно войти.");
            setTimeout(() => {
                window.location.href = "/login/index.html";
            }, 1200);
            return;
        }

        const text = await res.text();
        showError(text || `Ошибка: ${res.status}`);
    } catch {
        showError("Сервер недоступен");
    }
});
