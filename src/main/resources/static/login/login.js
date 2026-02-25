const form = document.getElementById("loginForm");
const err = document.getElementById("err");

function showError(msg) {
    err.textContent = msg;
    err.style.display = "block";
}

function getSafeNextUrl() {
    const params = new URLSearchParams(window.location.search);
    const next = params.get("next");
    if (!next) return null;
    if (!next.startsWith("/")) return null;
    if (next.startsWith("//")) return null;
    return next;
}

function defaultCabinetByStatus(status) {
    if (status === "AGENT") return "/cabinet/agent/index.html";
    if (status === "CLIENT") return "/cabinet/client/index.html";
    if (status === "ADMIN") return "/";
    return "/";
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const token = btoa(email + ":" + password);

    try {
        const res = await fetch("/api/me", {
            method: "GET",
            headers: {
                Authorization: "Basic " + token
            }
        });

        if (res.ok) {
            const me = await res.json();
            sessionStorage.setItem("auth", token);
            const next = getSafeNextUrl();
            window.location.href = next || defaultCabinetByStatus(me.status);
            return;
        }

        if (res.status === 401) {
            showError("Неверный email или пароль");
        } else {
            showError("Ошибка входа: " + res.status);
        }
    } catch {
        showError("Сервер недоступен");
    }
});
