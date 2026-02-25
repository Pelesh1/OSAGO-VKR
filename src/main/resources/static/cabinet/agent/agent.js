function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadSummary(token) {
    const res = await fetch("/api/agent/summary", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadNotifications(token) {
    const res = await fetch("/api/agent/notifications?limit=20", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return [];
    return await res.json();
}

async function markRead(token, id) {
    await fetch(`/api/agent/notifications/${id}/read`, {
        method: "POST",
        headers: { Authorization: "Basic " + token }
    });
}

function formatDateTime(dt) {
    if (!dt) return "";
    return new Date(dt).toLocaleString("ru-RU");
}

function countClientUpdates(notifications) {
    return (notifications || []).filter((n) => {
        const type = (n.type || "").toUpperCase();
        return !n.isRead && (type === "CLAIM_CLIENT_NOTE" || type === "CLAIM_CLIENT_FILE");
    }).length;
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me) {
        sessionStorage.removeItem("auth");
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/index.html");
        return;
    }
    if (me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }

    document.getElementById("agentName").textContent = me.shortFio || me.email;
    document.getElementById("logoutBtn").addEventListener("click", logout);

    const cardButtons = document.querySelectorAll(".card__btn");
    if (cardButtons.length > 0) {
        cardButtons[0].addEventListener("click", () => {
            window.location.href = "/cabinet/agent/applications/index.html";
        });
    }
    if (cardButtons.length > 1) {
        cardButtons[1].addEventListener("click", () => {
            window.location.href = "/cabinet/agent/claims/index.html";
        });
    }
    if (cardButtons.length > 2) {
        cardButtons[2].addEventListener("click", () => {
            window.location.href = "/cabinet/agent/policies/index.html";
        });
    }
    if (cardButtons.length > 3) {
        cardButtons[3].addEventListener("click", () => {
            window.location.href = "/insurance/osago/index.html";
        });
    }
    if (cardButtons.length > 4) {
        cardButtons[4].addEventListener("click", () => {
            window.location.href = "/cabinet/agent/chats/index.html";
        });
    }
    if (cardButtons.length > 5) {
        cardButtons[5].addEventListener("click", () => {
            window.location.href = "/cabinet/agent/reports/index.html";
        });
    }

    const summary = await loadSummary(token);
    if (summary) {
        document.getElementById("activePolicies").textContent = String(summary.activePolicies ?? 0);
        document.getElementById("pendingApplications").textContent = String(summary.pendingApplications ?? 0);
        document.getElementById("claimsInReview").textContent = String(summary.claimsInReview ?? 0);
        document.getElementById("clientsTotal").textContent = String(summary.clientsTotal ?? 0);
        document.getElementById("completedToday").textContent = String(summary.completedToday ?? 0);
        document.getElementById("unreadNotifications").textContent = String(summary.unreadNotifications ?? 0);

        const applicationsDot = document.getElementById("applicationsDot");
        if (applicationsDot) {
            const value = Number(summary.pendingApplications ?? 0);
            applicationsDot.textContent = String(value);
            applicationsDot.style.display = value > 0 ? "grid" : "none";
        }

        const claimsDot = document.getElementById("claimsDot");
        if (claimsDot) {
            const value = Number(summary.claimsInReview ?? 0);
            claimsDot.textContent = String(value);
            claimsDot.style.display = value > 0 ? "grid" : "none";
        }
    }

    let notifications = await loadNotifications(token);
    const list = document.getElementById("notificationsList");
    const clientUpdatesDot = document.getElementById("clientUpdatesDot");

    function renderNotifications() {
        const updatesCount = countClientUpdates(notifications);
        if (clientUpdatesDot) {
            clientUpdatesDot.textContent = String(updatesCount);
            clientUpdatesDot.style.display = updatesCount > 0 ? "grid" : "none";
        }

        if (!notifications.length) {
            list.innerHTML = `<div class="muted">Нет уведомлений</div>`;
            return;
        }
        list.innerHTML = notifications.map((n) => `
            <div class="noti-item" style="${n.isRead ? "opacity:.75;" : ""}">
                <p class="noti-title">${n.title || "Уведомление"}</p>
                <p class="noti-msg">${n.message || ""}</p>
                <p class="noti-time">${formatDateTime(n.createdAt)}</p>
                ${n.isRead ? "" : `<button class="noti-btn" data-id="${n.id}">Прочитано</button>`}
            </div>
        `).join("");
    }

    renderNotifications();
    list.addEventListener("click", async (e) => {
        const btn = e.target.closest(".noti-btn");
        if (!btn) return;
        const id = Number(btn.dataset.id);
        if (!id) return;
        await markRead(token, id);
        notifications = notifications.map((n) => (n.id === id ? { ...n, isRead: true } : n));
        renderNotifications();
    });
});
