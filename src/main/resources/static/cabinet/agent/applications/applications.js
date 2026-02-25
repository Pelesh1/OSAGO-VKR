const state = {
    page: 0,
    size: 12,
    totalPages: 0,
    totalElements: 0
};
const pageParams = new URLSearchParams(window.location.search);
const fixedClientId = pageParams.get("clientId");
const fixedPolicyId = pageParams.get("policyId");

const STATUS_LABELS = {
    NEW: "Новая",
    IN_REVIEW: "На рассмотрении",
    NEED_INFO: "Нужны данные",
    APPROVED: "Одобрена",
    PAYMENT_PENDING: "Ожидает оплату",
    PAID: "Оплачена",
    REJECTED: "Отклонена"
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

function fmtMoney(value) {
    if (value === null || value === undefined) return "—";
    return `${Number(value).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`;
}

function escapeHtml(value) {
    if (!value) return "";
    return String(value)
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

async function loadApplications(token) {
    const status = document.getElementById("statusFilter").value;
    const q = document.getElementById("qFilter").value.trim();
    const params = new URLSearchParams();
    params.set("page", String(state.page));
    params.set("size", String(state.size));
    if (status) params.set("status", status);
    if (q) params.set("q", q);
    if (fixedClientId) params.set("clientId", fixedClientId);
    if (fixedPolicyId) params.set("policyId", fixedPolicyId);

    const res = await fetch("/api/agent/applications?" + params.toString(), {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось загрузить заявки на полис");
    }
    return await res.json();
}

function renderRows(pageData) {
    const body = document.getElementById("appsBody");
    const items = pageData?.content ?? [];
    if (!items.length) {
        body.innerHTML = `<tr><td class="empty" colspan="7">Ничего не найдено</td></tr>`;
        return;
    }
    body.innerHTML = items.map((a) => {
        const appLabel = a.policyNumber ? `${a.policyNumber}` : `APP-${a.id}`;
        const auto = [a.brand, a.model].filter(Boolean).join(" ");
        return `
            <tr>
                <td>
                    <div><strong>${escapeHtml(appLabel)}</strong></div>
                    <div style="color:#64748b;">ID заявки: ${a.id}</div>
                </td>
                <td>
                    <div>${escapeHtml(a.clientName || "Клиент")}</div>
                    <div style="color:#64748b;">${escapeHtml(a.clientEmail || "—")}</div>
                </td>
                <td>
                    <div>${escapeHtml(auto || "—")}</div>
                    <div style="color:#64748b;">${escapeHtml(a.regNumber || a.vin || "—")}</div>
                </td>
                <td><span class="status status-${escapeHtml(a.status)}">${escapeHtml(statusLabel(a.status))}</span></td>
                <td>${fmtMoney(a.premiumAmount)}</td>
                <td>${fmtDateTime(a.updatedAt || a.createdAt)}</td>
                <td>
                    <button class="link-btn" type="button" data-open-id="${a.id}">Подробнее</button>
                </td>
            </tr>
        `;
    }).join("");
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
        const pageData = await loadApplications(token);
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
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/applications/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me) {
        sessionStorage.removeItem("auth");
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/applications/index.html");
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
    document.getElementById("appsBody").addEventListener("click", (e) => {
        const btn = e.target.closest("[data-open-id]");
        if (!btn) return;
        const id = Number(btn.dataset.openId);
        if (!id) return;
        window.location.href = "/cabinet/agent/applications/detail.html?id=" + encodeURIComponent(String(id));
    });

    await refresh(token);
});
