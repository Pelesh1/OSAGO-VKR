let state = {
    page: 0,
    size: 20,
    totalPages: 0
};

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

function showError(message) {
    const box = document.getElementById("errorBox");
    box.textContent = message || "Ошибка";
    box.style.display = "block";
}

function hideError() {
    const box = document.getElementById("errorBox");
    box.textContent = "";
    box.style.display = "none";
}

function formatDateTime(v) {
    if (!v) return "—";
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleString("ru-RU");
}

function formatDate(v) {
    if (!v) return "—";
    const d = new Date(v + "T00:00:00");
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleDateString("ru-RU");
}

function formatMoney(v) {
    const n = Number(v || 0);
    return `${n.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₽`;
}

function statusLabel(s) {
    const code = String(s || "").toUpperCase();
    if (code === "DRAFT") return "Черновик";
    if (code === "PENDING_PAY") return "Ожидает оплату";
    if (code === "ACTIVE") return "Активен";
    if (code === "CANCELLED") return "Отменен";
    if (code === "EXPIRED") return "Истек";
    return code || "—";
}

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

function parseParams() {
    const p = new URLSearchParams(window.location.search);
    return {
        clientId: p.get("clientId"),
        policyId: p.get("policyId")
    };
}

async function loadPolicies(token) {
    const status = document.getElementById("statusFilter").value;
    const q = document.getElementById("qFilter").value.trim();
    const params = new URLSearchParams();
    params.set("page", String(state.page));
    params.set("size", String(state.size));
    if (status) params.set("status", status);
    if (q) params.set("q", q);

    const ext = parseParams();
    if (ext.clientId) params.set("clientId", ext.clientId);
    if (ext.policyId) params.set("policyId", ext.policyId);

    const res = await fetch(`/api/agent/policies?${params.toString()}`, {
        headers: { Authorization: "Basic " + token }
    });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось загрузить полисы");
    return data;
}

function renderRows(data) {
    const body = document.getElementById("policiesBody");
    const items = data.content || [];
    if (!items.length) {
        body.innerHTML = `<tr><td class="empty" colspan="7">Полисы не найдены</td></tr>`;
        return;
    }
    body.innerHTML = items.map((item) => {
        const number = item.number || `ID ${item.id}`;
        const policyType = item.policyType || "OSAGO";
        const client = item.clientName || item.clientEmail || `Клиент #${item.userId}`;
        const vehicle = [item.brand, item.model].filter(Boolean).join(" ").trim() || "—";
        const period = `${formatDate(item.startDate)} — ${formatDate(item.endDate)}`;
        const statusCode = String(item.status || "").toUpperCase();
        const statusClass = `status-${statusCode}`;
        const actionHref = `/cabinet/agent/applications/index.html?policyId=${encodeURIComponent(String(item.id))}`;
        return `
            <tr>
                <td>
                    <div><strong>${number}</strong></div>
                    <div style="color:#64748b;font-size:12px;">${policyType}</div>
                </td>
                <td>
                    <div>${client}</div>
                    <div style="color:#64748b;font-size:12px;">${item.clientEmail || "—"}</div>
                </td>
                <td>
                    <div>${vehicle}</div>
                    <div style="color:#64748b;font-size:12px;">${item.regNumber || "—"}${item.vin ? ` • ${item.vin}` : ""}</div>
                </td>
                <td><span class="status ${statusClass}">${statusLabel(item.status)}</span></td>
                <td>
                    <div>${period}</div>
                    <div style="color:#64748b;font-size:12px;">Создан: ${formatDateTime(item.createdAt)}</div>
                </td>
                <td>${formatMoney(item.premiumAmount)}</td>
                <td>
                    <a class="link-btn" href="${actionHref}" style="display:inline-flex;align-items:center;text-decoration:none;">Заявки</a>
                </td>
            </tr>
        `;
    }).join("");
}

function renderPager(data) {
    state.totalPages = Number(data.totalPages || 0);
    const current = Number(data.page || 0);
    const total = Number(data.totalPages || 0);
    const totalElements = Number(data.totalElements || 0);
    document.getElementById("pagerInfo").textContent = `Страница ${current + 1} из ${Math.max(total, 1)} • Записей: ${totalElements}`;

    document.getElementById("prevBtn").disabled = current <= 0;
    document.getElementById("nextBtn").disabled = current + 1 >= total;
}

async function refresh(token) {
    hideError();
    const data = await loadPolicies(token);
    renderRows(data);
    renderPager(data);
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/policies/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me || me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }

    document.getElementById("agentName").textContent = me.shortFio || me.email;
    document.getElementById("logoutBtn").addEventListener("click", logout);
    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/agent/index.html";
    });

    const ext = parseParams();
    if (ext.policyId) {
        document.getElementById("qFilter").value = ext.policyId;
    }

    document.getElementById("applyBtn").addEventListener("click", async () => {
        state.page = 0;
        try {
            await refresh(token);
        } catch (e) {
            showError(e.message);
        }
    });

    document.getElementById("resetBtn").addEventListener("click", async () => {
        document.getElementById("statusFilter").value = "";
        document.getElementById("qFilter").value = "";
        state.page = 0;
        try {
            await refresh(token);
        } catch (e) {
            showError(e.message);
        }
    });

    document.getElementById("prevBtn").addEventListener("click", async () => {
        if (state.page <= 0) return;
        state.page -= 1;
        try {
            await refresh(token);
        } catch (e) {
            showError(e.message);
        }
    });

    document.getElementById("nextBtn").addEventListener("click", async () => {
        if (state.page + 1 >= state.totalPages) return;
        state.page += 1;
        try {
            await refresh(token);
        } catch (e) {
            showError(e.message);
        }
    });

    try {
        await refresh(token);
    } catch (e) {
        showError(e.message);
    }
});
