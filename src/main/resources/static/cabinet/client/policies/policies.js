function showError(message) {
    const box = document.getElementById("errorBox");
    box.textContent = message;
    box.style.display = "block";
}

function formatMoney(amount) {
    if (amount === null || amount === undefined) return "—";
    return `${Number(amount).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`;
}

function formatDate(date) {
    if (!date) return "—";
    return new Date(date).toLocaleDateString("ru-RU");
}

function formatDateTime(date) {
    if (!date) return "—";
    return new Date(date).toLocaleString("ru-RU");
}

function policyStatusPill(status) {
    if (status === "ACTIVE") return '<span class="pill pill-active">Активен</span>';
    if (status === "DRAFT") return '<span class="pill pill-draft">Черновик</span>';
    if (status === "PENDING_PAY") return '<span class="pill pill-pending">Ожидает оплату</span>';
    if (status === "CANCELLED") return '<span class="pill pill-red">Отменен</span>';
    if (status === "EXPIRED") return '<span class="pill pill-orange">Истек</span>';
    return `<span class="pill pill-violet">${status || "—"}</span>`;
}

function appStatusPill(status) {
    if (status === "NEW") return '<span class="pill pill-blue">Новая</span>';
    if (status === "IN_REVIEW") return '<span class="pill pill-violet">На рассмотрении</span>';
    if (status === "NEED_INFO") return '<span class="pill pill-orange">Нужны данные</span>';
    if (status === "APPROVED") return '<span class="pill pill-active">Одобрена</span>';
    if (status === "PAYMENT_PENDING") return '<span class="pill pill-pending">Ожидает оплату</span>';
    if (status === "PAID") return '<span class="pill pill-active">Оплачена</span>';
    if (status === "REJECTED") return '<span class="pill pill-red">Отклонена</span>';
    return `<span class="pill pill-violet">${status || "—"}</span>`;
}

function canPay(status) {
    return status === "APPROVED" || status === "PAYMENT_PENDING";
}

function canDeleteDraft(status) {
    return status === "NEW"
        || status === "IN_REVIEW"
        || status === "NEED_INFO"
        || status === "APPROVED"
        || status === "PAYMENT_PENDING";
}

function buildPaymentDraft(application) {
    return {
        applicationId: application.id,
        policyId: application.policyId || null,
        policyNumber: application.policyNumber || `APP-${application.id}`,
        amount: application.premiumAmount,
        vehicleSummary: "ОСАГО",
        regNumber: "—",
        termMonths: "—"
    };
}

function renderApplicationAction(a) {
    if (canPay(a.status)) {
        return `<button class="btn btn-blue" type="button" data-pay-id="${a.id}">Оплатить</button>`;
    }
    if (a.status === "PAID") {
        if (a.policyId) {
            return `<a class="btn" href="/cabinet/client/policies/detail.html?id=${a.policyId}">Открыть полис</a>`;
        }
        return `<span class="muted">Полис активирован</span>`;
    }
    if (a.status === "REJECTED") {
        return `<span class="muted">Заявка отклонена</span>`;
    }
    if (canDeleteDraft(a.status)) {
        return `<button class="btn" type="button" data-delete-id="${a.id}">Удалить черновик</button>`;
    }
    if (a.status === "NEED_INFO") {
        return `<span class="muted">Нужны уточнения от клиента</span>`;
    }
    return `<span class="muted">Ожидайте решения агента</span>`;
}

async function deleteDraftApplication(token, id) {
    const res = await fetch(`/api/osago/applications/${id}/delete-draft`, {
        method: "POST",
        headers: { Authorization: `Basic ${token}` }
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
        throw new Error(data.message || `Не удалось удалить черновик (HTTP ${res.status})`);
    }
}

async function loadPolicies(token) {
    const res = await fetch("/api/client/policies", {
        headers: { Authorization: `Basic ${token}` }
    });
    if (res.status === 401) return { unauthorized: true };
    const data = await res.json().catch(() => []);
    if (!res.ok) {
        throw new Error(`Ошибка загрузки полисов (HTTP ${res.status})`);
    }
    return { data };
}

async function loadApplications(token) {
    const res = await fetch("/api/osago/applications/my", {
        headers: { Authorization: `Basic ${token}` }
    });
    if (res.status === 401) return { unauthorized: true };
    const data = await res.json().catch(() => []);
    if (!res.ok) {
        throw new Error(`Ошибка загрузки заявок (HTTP ${res.status})`);
    }
    return { data };
}

function renderPolicies(data) {
    if (!Array.isArray(data) || data.length === 0) {
        document.getElementById("policiesEmpty").style.display = "block";
        document.getElementById("policiesWrap").style.display = "none";
        return;
    }

    const tbody = document.getElementById("policiesBody");
    tbody.innerHTML = data.map((p) => `
        <tr>
            <td>${p.number || `POL-${p.id}`}</td>
            <td>${policyStatusPill(p.status)}</td>
            <td>${[p.brand, p.model].filter(Boolean).join(" ") || "—"}</td>
            <td>${p.regNumber || "—"}</td>
            <td>${formatDate(p.startDate)} - ${formatDate(p.endDate)}</td>
            <td>${formatMoney(p.premiumAmount)}</td>
            <td><a class="btn" href="/cabinet/client/policies/detail.html?id=${p.id}">Подробнее</a></td>
        </tr>
    `).join("");

    document.getElementById("policiesEmpty").style.display = "none";
    document.getElementById("policiesWrap").style.display = "block";
}

function renderApplications(data) {
    if (!Array.isArray(data) || data.length === 0) {
        document.getElementById("applicationsEmpty").style.display = "block";
        document.getElementById("applicationsWrap").style.display = "none";
        return;
    }

    const tbody = document.getElementById("applicationsBody");
    tbody.innerHTML = data.map((a) => `
        <tr>
            <td>${a.id}</td>
            <td>${a.policyNumber || "—"}</td>
            <td>${appStatusPill(a.status)}</td>
            <td>${formatMoney(a.premiumAmount)}</td>
            <td>${formatDateTime(a.createdAt)}</td>
            <td>${renderApplicationAction(a)}</td>
        </tr>
    `).join("");

    document.getElementById("applicationsEmpty").style.display = "none";
    document.getElementById("applicationsWrap").style.display = "block";

    tbody.querySelectorAll("[data-pay-id]").forEach((btn) => {
        btn.addEventListener("click", () => {
            const id = Number(btn.dataset.payId);
            const app = data.find((x) => Number(x.id) === id);
            if (!app) return;
            sessionStorage.setItem("osagoPaymentDraft", JSON.stringify(buildPaymentDraft(app)));
            window.location.href = "/insurance/osago/payment.html";
        });
    });

    tbody.querySelectorAll("[data-delete-id]").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const id = Number(btn.dataset.deleteId);
            if (!id) return;
            if (!window.confirm("Удалить черновик заявки и связанный полис?")) return;
            try {
                await deleteDraftApplication(sessionStorage.getItem("auth"), id);
                window.location.reload();
            } catch (e) {
                showError(e.message || "Не удалось удалить черновик");
            }
        });
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    const next = encodeURIComponent("/cabinet/client/policies/index.html");
    if (!token) {
        window.location.href = `/login/index.html?next=${next}`;
        return;
    }

    try {
        const submittedBanner = sessionStorage.getItem("osagoSubmittedBanner");
        if (submittedBanner) {
            const banner = document.getElementById("submittedBanner");
            if (banner) {
                banner.textContent = `Заявка ${submittedBanner} отправлена. Ожидайте одобрения агента.`;
                banner.style.display = "block";
            }
            sessionStorage.removeItem("osagoSubmittedBanner");
        }

        const [policiesResult, applicationsResult] = await Promise.all([
            loadPolicies(token),
            loadApplications(token)
        ]);

        if (policiesResult.unauthorized || applicationsResult.unauthorized) {
            sessionStorage.removeItem("auth");
            window.location.href = `/login/index.html?next=${next}`;
            return;
        }

        renderApplications(applicationsResult.data || []);
        renderPolicies(policiesResult.data || []);

        if (window.location.hash === "#applications") {
            const section = document.getElementById("applicationsWrap") || document.getElementById("applicationsEmpty");
            section?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    } catch (e) {
        showError(e.message || "Не удалось загрузить данные");
    }
});
