const STATUS_LABELS = {
    NEW: "Новая",
    IN_REVIEW: "На рассмотрении",
    NEED_INFO: "Нужны документы",
    APPROVED: "Одобрена",
    REJECTED: "Отклонена",
    CLOSED: "Закрыта"
};

const ACCIDENT_LABELS = {
    COLLISION: "Столкновение",
    PARKING_COLLISION: "Парковочное столкновение",
    GLASS_DAMAGE: "Повреждение стекла",
    SIDE_COLLISION: "Боковое столкновение",
    OTHER: "Прочее"
};

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
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

function fmtDateTime(v) {
    if (!v) return "—";
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleString("ru-RU");
}

function statusLabel(statusCode) {
    if (!statusCode) return "—";
    return STATUS_LABELS[statusCode] || statusCode;
}

function accidentLabel(code) {
    if (!code) return "Прочее";
    return ACCIDENT_LABELS[code] || code;
}

function resolveDecisionText(claim) {
    const manual = (claim.decisionComment || "").trim();
    if (manual) return manual;

    const amount = Number(claim.approvedAmount || 0);
    const amountText = amount > 0
        ? `${amount.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₽`
        : null;

    if (claim.status === "APPROVED") {
        return amountText
            ? `Страховой случай одобрен. Сумма выплаты: ${amountText}.`
            : "Страховой случай одобрен агентом.";
    }
    if (claim.status === "REJECTED") {
        return "Страховой случай отклонен агентом.";
    }
    if (claim.status === "NEED_INFO") {
        return "Требуются дополнительные документы или уточнения от клиента.";
    }
    if (claim.status === "IN_REVIEW") {
        return "Заявка находится на рассмотрении агента.";
    }
    if (claim.status === "CLOSED") {
        return "Обработка страхового случая завершена, заявка закрыта.";
    }
    return "Решение пока не принято.";
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

async function loadClaim(token, id) {
    const res = await fetch(`/api/agent/claims/${id}`, {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось загрузить карточку");
    }
    return await res.json();
}

async function takeInReview(token, id) {
    const res = await fetch(`/api/agent/claims/${id}/take`, {
        method: "POST",
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось взять в работу");
    }
    return await res.json();
}

async function requestNeedInfo(token, id, comment) {
    const res = await fetch(`/api/agent/claims/${id}/need-info`, {
        method: "POST",
        headers: {
            Authorization: "Basic " + token,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ comment })
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось запросить документы");
    }
    return await res.json();
}

async function approveClaim(token, id, approvedAmount, comment) {
    const res = await fetch(`/api/agent/claims/${id}/approve`, {
        method: "POST",
        headers: {
            Authorization: "Basic " + token,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ approvedAmount, comment })
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось одобрить заявку");
    }
    return await res.json();
}

async function rejectClaim(token, id, comment) {
    const res = await fetch(`/api/agent/claims/${id}/reject`, {
        method: "POST",
        headers: {
            Authorization: "Basic " + token,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ comment })
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось отклонить заявку");
    }
    return await res.json();
}

async function downloadAttachment(token, claimId, attachmentId, fallbackName) {
    const res = await fetch(`/api/agent/claims/${claimId}/attachments/${attachmentId}/download`, {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || "Не удалось скачать файл");
    }

    const blob = await res.blob();
    const contentDisposition = res.headers.get("content-disposition") || "";
    const match = /filename\*?=(?:UTF-8'')?["']?([^;"']+)["']?/i.exec(contentDisposition);
    const fileName = match ? decodeURIComponent(match[1]) : (fallbackName || "attachment");

    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function renderNotice(claim) {
    const box = document.getElementById("statusNotice");
    box.className = "notice";
    box.textContent = "";

    if (claim.status === "APPROVED" && Number(claim.approvedAmount || 0) > 0) {
        const amount = Number(claim.approvedAmount).toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        box.className = "notice ok";
        box.textContent = `✓ Страховой случай одобрен. Выплата в размере ${amount} ₽ будет произведена в течение 5 рабочих дней.`;
        return;
    }
    if (claim.status === "REJECTED") {
        box.className = "notice bad";
        box.textContent = `Страховой случай отклонен.${claim.decisionComment ? " Причина: " + claim.decisionComment : ""}`;
    }
}

function render(claim) {
    const claimNumber = claim.number || `CLM-${claim.id}`;
    document.getElementById("title").textContent = `Претензия #${claimNumber}`;

    const badge = document.getElementById("statusBadge");
    badge.textContent = statusLabel(claim.status);
    badge.className = `chip status-${claim.status || "NEW"}`;

    document.getElementById("clientName").textContent = claim.clientName || "—";
    document.getElementById("clientEmail").textContent = claim.clientEmail || "—";
    document.getElementById("contactPhone").textContent = claim.contactPhone || "—";
    document.getElementById("contactEmail").textContent = claim.contactEmail || "—";

    document.getElementById("policyNumber").textContent = claim.policyNumber || "—";
    document.getElementById("vehicleInfo").textContent = [claim.vehicleBrand, claim.vehicleModel, claim.vehicleRegNumber].filter(Boolean).join(", ") || "—";
    document.getElementById("createdAt").textContent = fmtDateTime(claim.createdAt);
    document.getElementById("updatedAt").textContent = fmtDateTime(claim.updatedAt);

    document.getElementById("accidentAt").textContent = fmtDateTime(claim.accidentAt);
    document.getElementById("accidentType").textContent = accidentLabel(claim.accidentType);
    document.getElementById("accidentPlace").textContent = claim.accidentPlace || "—";
    document.getElementById("claimNumber").textContent = claimNumber;
    document.getElementById("description").textContent = claim.description || "—";
    document.getElementById("decisionComment").textContent = resolveDecisionText(claim);

    const amount = Number(claim.approvedAmount || 0);
    document.getElementById("approvedAmount").textContent = amount > 0
        ? `${amount.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₽`
        : "—";

    const atts = claim.attachments || [];
    const attBox = document.getElementById("attachments");
    if (!atts.length) {
        attBox.innerHTML = `<div class="field-label">Документы не загружены</div>`;
    } else {
        attBox.innerHTML = atts.map((a) => `
            <div class="doc">
                <div class="doc-left">
                    <div class="doc-name">${escapeHtml(a.fileName || "Файл")}</div>
                    <div class="doc-meta">${escapeHtml(a.attachmentType || "FILE")}</div>
                </div>
                <button class="doc-btn" type="button" data-attachment-id="${a.id}" data-file-name="${escapeHtml(a.fileName || "attachment")}">Скачать</button>
            </div>
        `).join("");
    }

    renderNotice(claim);
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent(window.location.pathname + window.location.search);
        return;
    }

    const me = await loadMe(token);
    if (!me || me.status !== "AGENT") {
        sessionStorage.removeItem("auth");
        window.location.href = "/";
        return;
    }

    document.getElementById("logoutBtn").addEventListener("click", logout);

    const id = Number(new URLSearchParams(window.location.search).get("id"));
    if (!id) {
        setError("Не передан id претензии");
        return;
    }

    function setActionState(claim) {
        const canReview = claim.status === "NEW" || claim.status === "NEED_INFO";
        const canDecide = claim.status === "NEW" || claim.status === "IN_REVIEW" || claim.status === "NEED_INFO";
        const hideTake = claim.status === "APPROVED" || claim.status === "REJECTED" || claim.status === "CLOSED";

        const takeBtn = document.getElementById("takeBtn");
        takeBtn.disabled = !canReview;
        takeBtn.style.display = hideTake ? "none" : "block";

        document.getElementById("needInfoBtn").disabled = !canDecide;
        document.getElementById("approveBtn").disabled = !canDecide;
        document.getElementById("rejectBtn").disabled = !canDecide;

        const actionsCard = document.getElementById("actionsCard");
        actionsCard.style.display = (canReview || canDecide) ? "block" : "none";
    }

    async function reload() {
        setError("");
        try {
            const claim = await loadClaim(token, id);
            render(claim);
            setActionState(claim);
        } catch (e) {
            setError(e.message || "Ошибка загрузки");
        }
    }

    document.getElementById("takeBtn").addEventListener("click", async () => {
        setError("");
        try {
            await takeInReview(token, id);
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("needInfoBtn").addEventListener("click", async () => {
        const comment = document.getElementById("actionComment").value.trim();
        if (!comment) {
            setError("Для запроса документов укажи комментарий");
            return;
        }
        setError("");
        try {
            await requestNeedInfo(token, id, comment);
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("approveBtn").addEventListener("click", async () => {
        const amountRaw = document.getElementById("approveAmountInput").value.trim();
        const comment = document.getElementById("actionComment").value.trim();
        const amount = Number(amountRaw);
        if (!Number.isFinite(amount) || amount <= 0) {
            setError("Укажи корректную сумму выплаты больше 0");
            return;
        }
        setError("");
        try {
            await approveClaim(token, id, amount, comment || null);
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("rejectBtn").addEventListener("click", async () => {
        const comment = document.getElementById("actionComment").value.trim();
        if (!comment) {
            setError("Для отклонения укажи причину в комментарии");
            return;
        }
        setError("");
        try {
            await rejectClaim(token, id, comment);
            await reload();
        } catch (e) {
            setError(e.message || "Ошибка операции");
        }
    });

    document.getElementById("attachments").addEventListener("click", async (e) => {
        const btn = e.target.closest("button[data-attachment-id]");
        if (!btn) return;
        const attachmentId = Number(btn.dataset.attachmentId);
        if (!attachmentId) return;
        try {
            await downloadAttachment(token, id, attachmentId, btn.dataset.fileName || "attachment");
        } catch (err) {
            setError(err.message || "Не удалось скачать файл");
        }
    });

    await reload();
});
