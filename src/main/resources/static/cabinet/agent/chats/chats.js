let pollTimer = null;
let meId = null;
let activeChatId = null;
let activeClientId = null;
let activeTopicRefId = null;
let activeTopicType = null;

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

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadChats(token) {
    const res = await fetch("/api/agent/chats", { headers: { Authorization: "Basic " + token } });
    const txt = await res.text();
    let data = [];
    try { data = txt ? JSON.parse(txt) : []; } catch {}
    if (!res.ok) throw new Error((data && data.message) || txt || "Не удалось загрузить список чатов");
    return data;
}

async function loadChatDetails(token, chatId) {
    const res = await fetch(`/api/agent/chats/${chatId}`, { headers: { Authorization: "Basic " + token } });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось загрузить чат");
    return data;
}

async function sendMessage(token, chatId, message) {
    const res = await fetch(`/api/agent/chats/${chatId}/messages`, {
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

function renderChatList(items) {
    const box = document.getElementById("chatList");
    if (!items || !items.length) {
        box.innerHTML = `<div style="color:#64748b;">У вас пока нет чатов с клиентами.</div>`;
        return;
    }
    box.innerHTML = items.map((c) => `
        <div class="chat-item ${Number(c.chatId) === Number(activeChatId) ? "active" : ""}" data-chat-id="${c.chatId}" data-client-id="${c.clientId}">
            <div class="chat-name">${(c.clientName || "Клиент").replaceAll("<", "&lt;").replaceAll(">", "&gt;")}</div>
            <div class="chat-preview">${(c.lastMessage || c.topicLabel || "Нет сообщений").replaceAll("<", "&lt;").replaceAll(">", "&gt;")}</div>
            <div class="chat-time">${formatTime(c.lastMessageAt)} ${c.unreadCount > 0 ? `• Непрочитано: ${c.unreadCount}` : ""}</div>
        </div>
    `).join("");
}

function renderMessages(messages) {
    const box = document.getElementById("messages");
    if (!messages || !messages.length) {
        box.innerHTML = `<div style="color:#64748b;">Сообщений пока нет.</div>`;
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

async function refresh(token) {
    const chats = await loadChats(token);
    if (!activeChatId && chats.length) {
        activeChatId = chats[0].chatId;
        activeClientId = chats[0].clientId;
    }

    const selected = chats.find((c) => Number(c.chatId) === Number(activeChatId));
    if (selected) activeClientId = selected.clientId;

    renderChatList(chats);

    if (!activeChatId) {
        document.getElementById("chatTitle").textContent = "Выберите чат";
        document.getElementById("topicLine").textContent = "Тема обращения: —";
        renderMessages([]);
        return;
    }

    const details = await loadChatDetails(token, activeChatId);
    document.getElementById("chatTitle").textContent = details.header?.clientName || "Чат";
    document.getElementById("topicLine").textContent = `Тема обращения: ${details.topic?.label || "не выбрана"}`;
    const phone = details.header?.clientPhone ? details.header.clientPhone : "—";
    document.getElementById("clientPhone").textContent = `Телефон клиента: ${phone}`;

    activeTopicType = details.topic?.topicType || null;
    activeTopicRefId = details.topic?.topicRefId || null;

    renderMessages(details.messages || []);
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/chats/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me || me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }
    meId = me.id;

    try {
        await refresh(token);
    } catch (e) {
        showError(e.message);
    }

    document.getElementById("goPolicies").addEventListener("click", () => {
        if (!activeClientId) return;
        let url = `/cabinet/agent/policies/index.html?clientId=${encodeURIComponent(String(activeClientId))}`;
        if (activeTopicType === "POLICY" && activeTopicRefId) {
            url += `&policyId=${encodeURIComponent(String(activeTopicRefId))}`;
        }
        window.location.href = url;
    });

    document.getElementById("goClaims").addEventListener("click", () => {
        if (!activeClientId) return;
        let url = `/cabinet/agent/claims/index.html?clientId=${encodeURIComponent(String(activeClientId))}`;
        if (activeTopicType === "CLAIM" && activeTopicRefId) {
            url += `&claimId=${encodeURIComponent(String(activeTopicRefId))}`;
        }
        window.location.href = url;
    });

    document.getElementById("chatList").addEventListener("click", async (e) => {
        const item = e.target.closest("[data-chat-id]");
        if (!item) return;
        activeChatId = Number(item.dataset.chatId);
        activeClientId = Number(item.dataset.clientId);
        hideError();
        try {
            await refresh(token);
        } catch (err) {
            showError(err.message);
        }
    });

    document.getElementById("sendBtn").addEventListener("click", async () => {
        hideError();
        const input = document.getElementById("messageInput");
        const text = (input.value || "").trim();
        if (!text || !activeChatId) return;
        try {
            await sendMessage(token, activeChatId, text);
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
            await refresh(token);
        } catch {
        }
    }, 5000);
});

window.addEventListener("beforeunload", () => {
    if (pollTimer) clearInterval(pollTimer);
});
