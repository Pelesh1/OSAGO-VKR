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

function fmtDateTime(v) {
    if (!v) return "—";
    return new Date(v).toLocaleString("ru-RU");
}

function fmtDate(v) {
    if (!v) return "—";
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return v;
    return d.toLocaleDateString("ru-RU");
}

function fmtMoney(v) {
    if (v === null || v === undefined) return "—";
    return `${Number(v).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`;
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

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadApplication(token, id) {
    const res = await fetch(`/api/agent/applications/${id}`, {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось загрузить карточку заявки");
    }
    return await res.json();
}

async function callAction(token, id, action, body = null) {
    const res = await fetch(`/api/agent/applications/${id}/${action}`, {
        method: "POST",
        headers: {
            Authorization: "Basic " + token,
            ...(body ? { "Content-Type": "application/json" } : {})
        },
        ...(body ? { body: JSON.stringify(body) } : {})
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось выполнить действие");
    }
    return await res.json();
}

function render(app) {
    document.getElementById("title").textContent = app.policyNumber
        ? `Заявка по полису ${app.policyNumber}`
        : `Заявка #${app.id}`;
    document.getElementById("subtitle").textContent = `ID заявки: ${app.id}`;

    const badge = document.getElementById("statusBadge");
    badge.textContent = statusLabel(app.status);
    badge.className = `status status-${app.status || ""}`;

    document.getElementById("clientName").textContent = app.clientName || "—";
    document.getElementById("clientEmail").textContent = app.clientEmail || "—";
    document.getElementById("insuredBirthDate").textContent = fmtDate(app.insuredBirthDate);
    document.getElementById("passport").textContent = [app.passportSeries, app.passportNumber].filter(Boolean).join(" ") || "—";
    document.getElementById("registrationAddress").textContent = app.registrationAddress || "—";

    document.getElementById("driverLicenseNumber").textContent = app.driverLicenseNumber || "—";
    document.getElementById("licenseIssuedDate").textContent = fmtDate(app.licenseIssuedDate);

    document.getElementById("vehicleName").textContent = [app.brand, app.model].filter(Boolean).join(" ") || "—";
    document.getElementById("regNumber").textContent = app.regNumber || "—";
    document.getElementById("vin").textContent = app.vin || "—";
    document.getElementById("vehicleCategory").textContent = app.vehicleCategoryName || "—";
    document.getElementById("region").textContent = app.regionName || "—";
    document.getElementById("powerHp").textContent = app.powerHp ? `${app.powerHp} л.с.` : "—";

    document.getElementById("policyNumber").textContent = app.policyNumber || "—";
    document.getElementById("policyType").textContent = app.policyType || "—";
    document.getElementById("policyStatus").textContent = app.policyStatus || "—";
    document.getElementById("policyPeriod").textContent = app.startDate ? `${fmtDate(app.startDate)} — ${fmtDate(app.endDate)}` : "—";
    document.getElementById("termMonths").textContent = app.termMonths ? `${app.termMonths} мес.` : "—";
    document.getElementById("premiumAmount").textContent = fmtMoney(app.premiumAmount);

    document.getElementById("applicationComment").textContent = app.comment || "—";
    document.getElementById("createdAt").textContent = fmtDateTime(app.createdAt);
    document.getElementById("updatedAt").textContent = fmtDateTime(app.updatedAt);
}

function setActionState(app) {
    const canTake = app.status === "NEW" || app.status === "NEED_INFO";
    const canDecide = app.status === "NEW" || app.status === "IN_REVIEW" || app.status === "NEED_INFO";
    document.getElementById("takeBtn").disabled = !canTake;
    document.getElementById("needInfoBtn").disabled = !canDecide;
    document.getElementById("approveBtn").disabled = !canDecide;
    document.getElementById("rejectBtn").disabled = !canDecide;
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent(window.location.pathname + window.location.search);
        return;
    }

    const me = await loadMe(token);
    if (!me) {
        sessionStorage.removeItem("auth");
        window.location.href = "/login/index.html?next=" + encodeURIComponent(window.location.pathname + window.location.search);
        return;
    }
    if (me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }

    document.getElementById("logoutBtn").addEventListener("click", logout);
    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/agent/applications/index.html";
    });

    const id = Number(new URLSearchParams(window.location.search).get("id"));
    if (!id) {
        setError("Не передан id заявки");
        return;
    }

    async function reload() {
        setError("");
        try {
            const app = await loadApplication(token, id);
            render(app);
            setActionState(app);
        } catch (e) {
            setError(e.message || "Ошибка загрузки");
        }
    }

    document.getElementById("takeBtn").addEventListener("click", async () => {
        setError("");
        try {
            await callAction(token, id, "take");
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("needInfoBtn").addEventListener("click", async () => {
        const comment = document.getElementById("actionComment").value.trim();
        if (!comment) {
            setError("Для запроса данных нужен комментарий");
            return;
        }
        setError("");
        try {
            await callAction(token, id, "need-info", { comment });
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("approveBtn").addEventListener("click", async () => {
        const comment = document.getElementById("actionComment").value.trim();
        setError("");
        try {
            await callAction(token, id, "approve", { comment: comment || null });
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("rejectBtn").addEventListener("click", async () => {
        const comment = document.getElementById("actionComment").value.trim();
        if (!comment) {
            setError("Для отклонения укажите причину");
            return;
        }
        setError("");
        try {
            await callAction(token, id, "reject", { comment });
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    await reload();
});
