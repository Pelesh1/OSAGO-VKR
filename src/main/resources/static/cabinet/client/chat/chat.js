let pollTimer = null;
let meId = null;
let currentChatId = null;
let currentData = null;

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

function formatTime(value) {
    if (!value) return "";
    return new Date(value).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" });
}

function statusLabel(status) {
    if (!status) return "—";
    const s = String(status).toUpperCase();
    if (s === "ACTIVE") return "Активен";
    if (s === "PENDING_PAY") return "Ожидает оплату";
    if (s === "DRAFT") return "Черновик";
    if (s === "CANCELLED") return "Отменен";
    if (s === "NEW") return "Новая";
    if (s === "IN_REVIEW") return "На рассмотрении";
    if (s === "NEED_INFO") return "Нужны данные";
    if (s === "APPROVED") return "Одобрена";
    if (s === "PAYMENT_PENDING") return "Ожидает оплату";
    if (s === "PAID") return "Оплачена";
    if (s === "REJECTED") return "Отклонена";
    return status;
}

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadChat(token) {
    const res = await fetch("/api/client/chat", { headers: { Authorization: "Basic " + token } });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось загрузить чат");
    return data;
}

async function setTopic(token, payload) {
    const res = await fetch("/api/client/chat/topic", {
        method: "POST",
        headers: { Authorization: "Basic " + token, "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось сохранить тему");
    return data;
}

async function sendMessage(token, message) {
    const res = await fetch("/api/client/chat/messages", {
        method: "POST",
        headers: { Authorization: "Basic " + token, "Content-Type": "application/json" },
        body: JSON.stringify({ message })
    });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось отправить сообщение");
    return data;
}

function renderMessages(messages) {
    const box = document.getElementById("messages");
    if (!messages || !messages.length) {
        box.innerHTML = `<div style="color:#64748b;">Сообщений пока нет. Напишите первыми.</div>`;
        return;
    }
    box.innerHTML = messages.map((m) => {
        const mine = Number(m.senderId) === Number(meId);
        const readMark = mine
            ? `<div class="msg-time">${m.readByPeer ? "✓✓ Прочитано" : "✓ Отправлено"} • ${formatTime(m.createdAt)}</div>`
            : `<div class="msg-time">${formatTime(m.createdAt)}</div>`;
        return `
            <div class="msg ${mine ? "msg--me" : "msg--other"}">
                <div>${(m.message || "").replaceAll("<", "&lt;").replaceAll(">", "&gt;")}</div>
                ${readMark}
            </div>
        `;
    }).join("");
    box.scrollTop = box.scrollHeight;
}

function renderTopicSelectors(data) {
    const typeEl = document.getElementById("topicType");
    const refEl = document.getElementById("topicRefSelect");
    const otherNote = document.getElementById("otherTopicNote");

    const selectedType = typeEl.value;
    const policies = data?.clientPolicies || [];
    const claims = data?.activeClaims || [];

    if (selectedType === "POLICY") {
        otherNote.style.display = "none";
        refEl.style.display = "block";
        if (!policies.length) {
            refEl.innerHTML = `<option value="">Нет доступных полисов</option>`;
            refEl.disabled = true;
            return;
        }
        refEl.disabled = false;
        refEl.innerHTML = policies.map((p) => `<option value="${p.id}">${p.number} • ${statusLabel(p.status)}</option>`).join("");
        if (data.topic?.topicType === "POLICY" && data.topic?.topicRefId) {
            refEl.value = String(data.topic.topicRefId);
        }
        return;
    }

    if (selectedType === "CLAIM") {
        otherNote.style.display = "none";
        refEl.style.display = "block";
        if (!claims.length) {
            refEl.innerHTML = `<option value="">Нет активных страховых случаев</option>`;
            refEl.disabled = true;
            return;
        }
        refEl.disabled = false;
        refEl.innerHTML = claims.map((c) => `<option value="${c.id}">${c.number} • ${statusLabel(c.status)}</option>`).join("");
        if (data.topic?.topicType === "CLAIM" && data.topic?.topicRefId) {
            refEl.value = String(data.topic.topicRefId);
        }
        return;
    }

    refEl.style.display = "none";
    refEl.disabled = true;
    otherNote.style.display = "block";
}

async function refresh(token) {
    const data = await loadChat(token);
    currentData = data;
    currentChatId = data.chatId;

    document.getElementById("agentName").textContent = data.agent?.name || "Ваш страховой агент";
    document.getElementById("agentPhone").textContent = data.agent?.phone || "—";
    document.getElementById("currentTopic").textContent = data.topic?.label || "Не выбрана";

    const typeEl = document.getElementById("topicType");
    if (data.topic?.topicType) {
        typeEl.value = data.topic.topicType;
    }
    renderTopicSelectors(data);

    const unreadBadge = document.getElementById("unreadBadge");
    if ((data.unreadFromAgent || 0) > 0) {
        unreadBadge.textContent = String(data.unreadFromAgent);
        unreadBadge.style.display = "inline-block";
    } else {
        unreadBadge.style.display = "none";
    }

    renderMessages(data.messages || []);
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/client/chat/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me || me.status !== "CLIENT") {
        window.location.href = "/";
        return;
    }
    meId = me.id;

    const topicTypeEl = document.getElementById("topicType");
    topicTypeEl.addEventListener("change", () => renderTopicSelectors(currentData));

    document.getElementById("saveTopicBtn").addEventListener("click", async () => {
        hideError();
        try {
            const type = topicTypeEl.value;
            if (type === "POLICY") {
                const policyId = Number(document.getElementById("topicRefSelect").value);
                if (!policyId) throw new Error("Выберите полис");
                await setTopic(token, { topicType: "POLICY", policyId });
            } else if (type === "CLAIM") {
                const claimId = Number(document.getElementById("topicRefSelect").value);
                if (!claimId) throw new Error("Выберите страховой случай");
                await setTopic(token, { topicType: "CLAIM", claimId });
            } else {
                const note = (document.getElementById("otherTopicNote").value || "").trim();
                await setTopic(token, { topicType: "OTHER", note });
            }
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

    document.getElementById("sendBtn").addEventListener("click", async () => {
        hideError();
        const input = document.getElementById("messageInput");
        const text = (input.value || "").trim();
        if (!text) return;
        try {
            await sendMessage(token, text);
            input.value = "";
            await refresh(token);
        } catch (e) {
            showError(e.message);
        }
    });

    document.getElementById("messageInput").addEventListener("keydown", async (e) => {
        if (e.key !== "Enter") return;
        e.preventDefault();
        document.getElementById("sendBtn").click();
    });

    pollTimer = setInterval(async () => {
        try {
            if (currentChatId) await refresh(token);
        } catch {
        }
    }, 5000);
});

window.addEventListener("beforeunload", () => {
    if (pollTimer) clearInterval(pollTimer);
});
