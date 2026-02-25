function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

function showError(message) {
    const err = document.getElementById("err");
    err.textContent = message;
    err.style.display = "block";
}

function formatDate(iso) {
    if (!iso) return "—";
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return "—";
    return date.toLocaleDateString("ru-RU");
}

function statusMeta(status) {
    const map = {
        NEW: { label: "Новые", cls: "badge--sent" },
        IN_REVIEW: { label: "На рассмотрении", cls: "badge--review" },
        NEED_INFO: { label: "На рассмотрении", cls: "badge--review" },
        APPROVED: { label: "Одобрена", cls: "badge--approved" },
        CLOSED: { label: "Выплата произведена", cls: "badge--paid" },
        REJECTED: { label: "Отклонена", cls: "badge--rejected" }
    };
    return map[status] || { label: status || "Неизвестно", cls: "badge--default" };
}

function policyLabel(policyId) {
    if (!policyId) return "—";
    return `EEE ${policyId}`;
}

function setCounters(claims) {
    const counters = {
        totalCount: claims.length,
        sentCount: 0,
        reviewCount: 0,
        approvedCount: 0,
        paidCount: 0
    };

    for (const claim of claims) {
        if (claim.status === "NEW") counters.sentCount += 1;
        if (claim.status === "IN_REVIEW" || claim.status === "NEED_INFO") counters.reviewCount += 1;
        if (claim.status === "APPROVED") counters.approvedCount += 1;
        if (claim.status === "CLOSED") counters.paidCount += 1;
    }

    for (const [id, value] of Object.entries(counters)) {
        const el = document.getElementById(id);
        if (el) el.textContent = String(value);
    }
}

function renderRows(claims) {
    const tbody = document.getElementById("rows");
    tbody.innerHTML = "";

    if (!claims.length) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align:center; color:#6b7280; padding:24px;">Претензий пока нет</td>
            </tr>
        `;
        return;
    }

    for (const claim of claims) {
        const status = statusMeta(claim.status);
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${claim.number || "—"}</td>
            <td>${policyLabel(claim.policyId)}</td>
            <td>${formatDate(claim.accidentAt || claim.createdAt)}</td>
            <td class="desc-cell" title="${claim.description || ""}">${claim.description || "—"}</td>
            <td><span class="badge ${status.cls}">${status.label}</span></td>
            <td class="action-cell"><button class="details-btn" data-id="${claim.id}">👁 Подробнее</button></td>
        `;
        tbody.appendChild(tr);
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html";
        return;
    }

    document.getElementById("cabinetBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });

    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });

    document.getElementById("rows").addEventListener("click", (event) => {
        const button = event.target.closest(".details-btn");
        if (!button) return;
        const claimId = button.getAttribute("data-id");
        window.location.href = `/cabinet/client/claims/detail.html?id=${encodeURIComponent(claimId)}`;
    });

    try {
        const res = await fetch("/api/client/claims?size=100", {
            headers: { Authorization: `Basic ${token}` }
        });

        if (res.status === 401) {
            logout();
            return;
        }
        if (!res.ok) {
            const text = await res.text();
            showError(text || `Ошибка загрузки (HTTP ${res.status})`);
            return;
        }

        const payload = await res.json();
        const claims = Array.isArray(payload) ? payload : (payload.content || []);

        renderRows(claims);
        setCounters(claims);
    } catch {
        showError("Сервер недоступен");
    }
});
