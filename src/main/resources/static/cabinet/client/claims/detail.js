let authToken = null;
let currentClaimId = null;
let currentStatus = null;

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

function showError(message) {
    const err = document.getElementById("err");
    err.textContent = message;
    err.style.display = "block";
}

function showSuccess(message) {
    const ok = document.getElementById("ok");
    ok.textContent = message;
    ok.style.display = "block";
}

function hideMessages() {
    const err = document.getElementById("err");
    const ok = document.getElementById("ok");
    err.style.display = "none";
    err.textContent = "";
    ok.style.display = "none";
    ok.textContent = "";
}

function fmtDateTime(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleString("ru-RU", { hour12: false });
}

function fmtDate(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleDateString("ru-RU");
}

function normalizeCardNumber(raw) {
    return (raw || "").replace(/\D+/g, "");
}

function isValidCardByLuhn(cardDigits) {
    if (!/^\d{16,19}$/.test(cardDigits)) return false;
    let sum = 0;
    let shouldDouble = false;
    for (let i = cardDigits.length - 1; i >= 0; i--) {
        let digit = Number(cardDigits[i]);
        if (shouldDouble) {
            digit *= 2;
            if (digit > 9) digit -= 9;
        }
        sum += digit;
        shouldDouble = !shouldDouble;
    }
    return sum % 10 === 0;
}

function statusMeta(status) {
    const map = {
        NEW: { label: "Отправлено", cls: "badge--sent" },
        IN_REVIEW: { label: "На рассмотрении", cls: "badge--review" },
        NEED_INFO: { label: "Нужны документы", cls: "badge--review" },
        APPROVED: { label: "Одобрено", cls: "badge--approved" },
        CLOSED: { label: "Выплата произведена", cls: "badge--paid" },
        REJECTED: { label: "Отклонено", cls: "badge--rejected" }
    };
    return map[status] || { label: status || "Неизвестно", cls: "badge--default" };
}

function calcStages(status) {
    const indexByStatus = {
        NEW: 1,
        IN_REVIEW: 2,
        NEED_INFO: 2,
        APPROVED: 4,
        REJECTED: 4,
        CLOSED: 4
    };
    const active = indexByStatus[status] || 1;
    return [
        { id: 1, title: "Заявка подана", hint: "Заявка зарегистрирована в системе" },
        { id: 2, title: "Проверка документов", hint: "Идет анализ предоставленных материалов" },
        { id: 3, title: "Экспертиза", hint: "Проводится оценка ущерба" },
        { id: 4, title: "Принятие решения", hint: status === "REJECTED" ? "Заявка отклонена" : "Рассмотрение завершено" }
    ].map((stage) => ({
        ...stage,
        state: stage.id < active ? "done" : (stage.id === active ? "active" : "todo")
    }));
}

function renderTimeline(status) {
    const root = document.getElementById("timeline");
    root.innerHTML = "";
    for (const stage of calcStages(status)) {
        const li = document.createElement("li");
        li.innerHTML = `
            <span class="dot ${stage.state === "done" ? "done" : (stage.state === "active" ? "active" : "")}"></span>
            <div>
                <div style="font-weight:600;">${stage.title}</div>
                <div class="muted">${stage.hint}</div>
            </div>
        `;
        root.appendChild(li);
    }
}

function renderHistory(claim) {
    const history = [];
    if (claim.createdAt) history.push({ date: claim.createdAt, text: "Заявка создана в системе" });
    if (claim.updatedAt && claim.updatedAt !== claim.createdAt) history.push({ date: claim.updatedAt, text: "По заявке появились изменения" });
    if (claim.decidedAt) history.push({ date: claim.decidedAt, text: claim.status === "REJECTED" ? "Принято решение об отказе" : "Принято решение по выплате" });
    if (claim.paidAt) history.push({ date: claim.paidAt, text: "Выплата отмечена как произведенная" });

    const root = document.getElementById("history");
    root.innerHTML = "";
    if (!history.length) {
        root.innerHTML = `<div class="muted">Пока нет событий</div>`;
        return;
    }

    history.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    for (const item of history) {
        const row = document.createElement("div");
        row.className = "history-item";
        row.innerHTML = `
            <div class="muted">${fmtDateTime(item.date)}</div>
            <div>${item.text}</div>
        `;
        root.appendChild(row);
    }
}

function buildNotice(claim) {
    const num = claim.number || ("#" + claim.id);
    if (claim.status === "NEED_INFO") {
        const txt = claim.decisionComment ? `Причина: ${claim.decisionComment}` : "Агент запросил дополнительные данные.";
        return `По страховому случаю ${num} нужны дополнительные документы. ${txt}`;
    }
    if (claim.status === "IN_REVIEW") {
        return `Страховой случай ${num} находится на рассмотрении агента.`;
    }
    if (claim.status === "APPROVED") {
        const amount = claim.approvedAmount ? ` Сумма выплаты: ${claim.approvedAmount} ₽.` : "";
        return `Страховой случай ${num} одобрен.${amount}`;
    }
    if (claim.status === "REJECTED") {
        const reason = claim.decisionComment ? ` Причина: ${claim.decisionComment}` : "";
        return `Страховой случай ${num} отклонен.${reason}`;
    }
    if (claim.status === "CLOSED") {
        return `Страховой случай ${num} завершен.`;
    }
    return `Страховой случай ${num} зарегистрирован и ожидает обработки.`;
}

async function downloadAttachment(attachmentId, fallbackName) {
    if (!currentClaimId || !authToken) return;
    const res = await fetch(
        `/api/client/claims/${encodeURIComponent(currentClaimId)}/attachments/${encodeURIComponent(attachmentId)}/download`,
        { headers: { Authorization: `Basic ${authToken}` } }
    );
    if (res.status === 401) {
        logout();
        return;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка скачивания (HTTP ${res.status})`);
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

function renderAttachments(attachments) {
    const root = document.getElementById("attachments");
    root.innerHTML = "";
    if (!attachments || !attachments.length) {
        root.innerHTML = `<div class="muted">Документы не загружены</div>`;
        return;
    }
    for (const file of attachments) {
        const el = document.createElement("div");
        el.className = "attachment";
        el.innerHTML = `
            <div>
                <div>${file.fileName || "Без названия"}</div>
                <div class="attachment__meta">${file.attachmentType || "ACCIDENT_DOC"} • ${fmtDate(file.createdAt)}</div>
            </div>
            <button class="download-link" data-attachment-id="${file.id}" data-file-name="${file.fileName || ""}" type="button">Скачать</button>
        `;
        root.appendChild(el);
    }
}

function initials(name) {
    if (!name) return "—";
    return name.split(" ").filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join("");
}

function fillClaim(claim) {
    currentClaimId = claim.id;
    currentStatus = claim.status;
    const status = statusMeta(claim.status);

    document.getElementById("claimLabel").textContent = `Заявка ${claim.number || `#${claim.id}`}`;
    const statusEl = document.getElementById("statusBadge");
    statusEl.className = `badge ${status.cls}`;
    statusEl.textContent = status.label;

    const policy = claim.policyNumber || (claim.policyId ? `EEE ${claim.policyId}` : "—");
    document.getElementById("policyLabel").textContent = `Полис: ${policy}`;
    document.getElementById("accidentAt").textContent = fmtDateTime(claim.accidentAt);
    document.getElementById("accidentPlace").textContent = claim.accidentPlace || "—";
    document.getElementById("description").textContent = claim.description || "Описание не указано";
    document.getElementById("claimNotice").textContent = buildNotice(claim);

    const hasAgent = Boolean(claim.agentName || claim.agentEmail || claim.agentPhone);
    const agentName = hasAgent ? claim.agentName : "Агент не назначен";
    document.getElementById("agentName").textContent = agentName || "Агент не назначен";
    if (hasAgent) {
        const contactParts = [];
        if (claim.agentPhone) contactParts.push(`Телефон: ${claim.agentPhone}`);
        if (claim.agentEmail) contactParts.push(`Email: ${claim.agentEmail}`);
        document.getElementById("agentContact").textContent = contactParts.join(" · ") || "Контакты агента уточняются";
    } else {
        document.getElementById("agentContact").textContent = "Контакты появятся после назначения агента";
    }
    document.getElementById("agentInitials").textContent = initials(agentName);

    renderAttachments(claim.attachments || []);
    renderTimeline(claim.status);
    renderHistory(claim);

    const contactBtn = document.getElementById("contactAgentBtn");
    contactBtn.disabled = !claim.agentEmail;
    contactBtn.onclick = () => {
        if (!claim.agentEmail) return;
        window.location.href = `mailto:${claim.agentEmail}`;
    };

    const sendNoteBtn = document.getElementById("sendNoteBtn");
    sendNoteBtn.disabled = claim.status === "CLOSED" || claim.status === "REJECTED";

    const payoutSection = document.getElementById("payoutSection");
    if (payoutSection) {
        payoutSection.style.display = claim.status === "APPROVED" ? "block" : "none";
    }
}

async function loadClaimById(claimId) {
    const res = await fetch(`/api/client/claims/${encodeURIComponent(claimId)}`, {
        headers: { Authorization: `Basic ${authToken}` }
    });
    if (res.status === 401) {
        logout();
        return null;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка загрузки (HTTP ${res.status})`);
    }
    return await res.json();
}

async function sendClientNote(claimId, note) {
    const res = await fetch(`/api/client/claims/${encodeURIComponent(claimId)}/note`, {
        method: "POST",
        headers: {
            Authorization: `Basic ${authToken}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ note })
    });
    if (res.status === 401) {
        logout();
        return null;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка отправки (HTTP ${res.status})`);
    }
    return await res.json();
}

async function uploadClaimFile(claimId, file) {
    const form = new FormData();
    form.append("file", file);
    const type = (file.type || "").toLowerCase().startsWith("image/") ? "DAMAGE_PHOTO" : "ACCIDENT_DOC";
    form.append("attachmentType", type);
    const res = await fetch(`/api/client/claims/${encodeURIComponent(claimId)}/attachments`, {
        method: "POST",
        headers: { Authorization: `Basic ${authToken}` },
        body: form
    });
    if (res.status === 401) {
        logout();
        return null;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка загрузки файла (HTTP ${res.status})`);
    }
    return await res.json();
}

async function requestPayout(claimId, bankName, cardNumber) {
    const res = await fetch(`/api/client/claims/${encodeURIComponent(claimId)}/payout-request`, {
        method: "POST",
        headers: {
            Authorization: `Basic ${authToken}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ bankName, cardNumber })
    });
    if (res.status === 401) {
        logout();
        return null;
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка запроса выплаты (HTTP ${res.status})`);
    }
    return await res.json();
}

document.addEventListener("DOMContentLoaded", async () => {
    authToken = sessionStorage.getItem("auth");
    if (!authToken) {
        window.location.href = "/login/index.html";
        return;
    }

    document.getElementById("cabinetBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });
    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });

    document.getElementById("attachments").addEventListener("click", async (event) => {
        const button = event.target.closest("button[data-attachment-id]");
        if (!button) return;
        try {
            await downloadAttachment(button.getAttribute("data-attachment-id"), button.getAttribute("data-file-name"));
        } catch (err) {
            showError(err.message || "Не удалось скачать файл");
        }
    });

    const params = new URLSearchParams(window.location.search);
    const claimId = params.get("id");
    if (!claimId) {
        showError("Не указан идентификатор заявления");
        return;
    }

    document.getElementById("sendNoteBtn").addEventListener("click", async () => {
        hideMessages();
        if (currentStatus === "CLOSED" || currentStatus === "REJECTED") {
            showError("По этой заявке нельзя добавить новую информацию");
            return;
        }
        const note = document.getElementById("clientNote").value.trim();
        const input = document.getElementById("clientFiles");
        const files = Array.from(input.files || []);
        if (!note && !files.length) {
            showError("Добавьте комментарий или выберите файлы");
            return;
        }
        try {
            if (note) await sendClientNote(claimId, note);
            if (files.length) {
                for (const file of files) {
                    await uploadClaimFile(claimId, file);
                }
            }
            document.getElementById("clientNote").value = "";
            input.value = "";
            showSuccess("Информация отправлена агенту");
            const refreshed = await loadClaimById(claimId);
            if (refreshed) fillClaim(refreshed);
        } catch (e) {
            showError(e.message || "Не удалось отправить информацию");
        }
    });

    document.getElementById("sendFilesBtn").addEventListener("click", async () => {
        hideMessages();
        if (currentStatus === "CLOSED" || currentStatus === "REJECTED") {
            showError("По этой заявке нельзя добавить новые файлы");
            return;
        }
        const input = document.getElementById("clientFiles");
        const files = Array.from(input.files || []);
        if (!files.length) {
            showError("Выберите хотя бы один файл");
            return;
        }
        try {
            for (const file of files) {
                await uploadClaimFile(claimId, file);
            }
            input.value = "";
            showSuccess("Файлы отправлены агенту");
            const refreshed = await loadClaimById(claimId);
            if (refreshed) fillClaim(refreshed);
        } catch (e) {
            showError(e.message || "Не удалось загрузить файлы");
        }
    });

    document.getElementById("requestPayoutBtn").addEventListener("click", async () => {
        hideMessages();
        if (currentStatus !== "APPROVED") {
            showError("Запрос выплаты доступен только для одобренной заявки");
            return;
        }
        const bankName = document.getElementById("payoutBank").value.trim();
        const cardNumberRaw = document.getElementById("payoutCard").value.trim();
        const cardNumber = normalizeCardNumber(cardNumberRaw);
        if (!bankName) {
            showError("Укажите банк");
            return;
        }
        if (!cardNumber) {
            showError("Укажите номер карты");
            return;
        }
        if (!/^\d{16,19}$/.test(cardNumber)) {
            showError("Номер карты должен содержать от 16 до 19 цифр");
            return;
        }
        if (!isValidCardByLuhn(cardNumber)) {
            showError("Некорректный номер карты");
            return;
        }
        try {
            await requestPayout(claimId, bankName, cardNumber);
            showSuccess("Запрос выплаты отправлен");
            const refreshed = await loadClaimById(claimId);
            if (refreshed) fillClaim(refreshed);
        } catch (e) {
            showError(e.message || "Не удалось отправить запрос выплаты");
        }
    });

    try {
        const claim = await loadClaimById(claimId);
        if (!claim) return;
        fillClaim(claim);
    } catch (e) {
        showError("Сервер недоступен");
    }
});
