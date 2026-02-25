const state = {
    page: 0,
    size: 12,
    totalPages: 0,
    totalElements: 0
};
const pageParams = new URLSearchParams(window.location.search);
const fixedClientId = pageParams.get("clientId");
const fixedClaimId = pageParams.get("claimId");

const STATUS_LABELS = {
    NEW: "Новая",
    IN_REVIEW: "На рассмотрении",
    NEED_INFO: "Нужны документы",
    APPROVED: "Одобрена",
    REJECTED: "Отклонена",
    CLOSED: "Закрыта"
};

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

function fmtDateTime(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("ru-RU");
}

function escapeHtml(value) {
    if (!value) return "";
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function statusLabel(statusCode) {
    if (!statusCode) return "—";
    return STATUS_LABELS[statusCode] || statusCode;
}

function setError(text) {
    const box = document.getElementById("errorBox");
    if (!text) {
        box.style.display = "none";
        box.textContent = "";
        return;
    }
    box.style.display = "block";
    box.textContent = text;
}

async function loadClaims(token) {
    const status = document.getElementById("statusFilter").value;
    const q = document.getElementById("qFilter").value.trim();
    const params = new URLSearchParams();
    params.set("page", String(state.page));
    params.set("size", String(state.size));
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    if (fixedClientId) params.set("clientId", fixedClientId);
    if (fixedClaimId) params.set("claimId", fixedClaimId);

    const res = await fetch("/api/agent/claims?" + params.toString(), {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось загрузить претензии");
    }
    return await res.json();
}

function renderRows(pageData) {
    const body = document.getElementById("claimsBody");
    const items = pageData?.content ?? [];
    if (!items.length) {
        body.innerHTML = `<tr><td class="empty" colspan="7">Ничего не найдено</td></tr>`;
        return;
    }
    body.innerHTML = items.map((c) => `
        <tr>
            <td>
                <div><strong>${escapeHtml(c.number || ("CLAIM-" + c.id))}</strong></div>
                <div style="color:#64748b;">ID: ${c.id}</div>
            </td>
            <td>${escapeHtml(c.clientName || "Клиент")}</td>
            <td>${escapeHtml(c.policyNumber || "—")}</td>
            <td>${fmtDateTime(c.accidentAt)}</td>
            <td><span class="status status-${escapeHtml(c.status)}">${escapeHtml(statusLabel(c.status))}</span></td>
            <td>${fmtDateTime(c.createdAt)}</td>
            <td>
                <button class="link-btn" type="button" data-open-id="${c.id}">Подробнее</button>
            </td>
        </tr>
    `).join("");
}

function renderPager() {
    const info = document.getElementById("pagerInfo");
    const prev = document.getElementById("prevBtn");
    const next = document.getElementById("nextBtn");
    const humanPage = state.totalPages === 0 ? 0 : state.page + 1;
    info.textContent = `Страница ${humanPage} из ${state.totalPages} • Всего: ${state.totalElements}`;
    prev.disabled = state.page <= 0;
    next.disabled = state.page + 1 >= state.totalPages;
}

async function refresh(token) {
    setError("");
    try {
        const pageData = await loadClaims(token);
        state.totalPages = pageData.totalPages ?? 0;
        state.totalElements = pageData.totalElements ?? 0;
        renderRows(pageData);
        renderPager();
    } catch (e) {
        renderRows({ content: [] });
        renderPager();
        setError(e.message || "Ошибка загрузки");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/claims/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me) {
        sessionStorage.removeItem("auth");
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/claims/index.html");
        return;
    }
    if (me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }

    document.getElementById("agentName").textContent = me.shortFio || me.email;
    document.getElementById("logoutBtn").addEventListener("click", logout);
    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/agent/index.html";
    });

    document.getElementById("applyBtn").addEventListener("click", async () => {
        state.page = 0;
        await refresh(token);
    });
    document.getElementById("resetBtn").addEventListener("click", async () => {
        document.getElementById("statusFilter").value = "";
        document.getElementById("qFilter").value = "";
        state.page = 0;
        await refresh(token);
    });
    document.getElementById("qFilter").addEventListener("keydown", async (e) => {
        if (e.key !== "Enter") return;
        state.page = 0;
        await refresh(token);
    });

    document.getElementById("prevBtn").addEventListener("click", async () => {
        if (state.page <= 0) return;
        state.page -= 1;
        await refresh(token);
    });
    document.getElementById("nextBtn").addEventListener("click", async () => {
        if (state.page + 1 >= state.totalPages) return;
        state.page += 1;
        await refresh(token);
    });
    document.getElementById("claimsBody").addEventListener("click", (e) => {
        const btn = e.target.closest("[data-open-id]");
        if (!btn) return;
        const id = Number(btn.dataset.openId);
        if (!id) return;
        window.location.href = "/cabinet/agent/claims/detail.html?id=" + encodeURIComponent(String(id));
    });

    await refresh(token);
});
