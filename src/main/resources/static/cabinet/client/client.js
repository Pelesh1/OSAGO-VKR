async function loadMe() {
    const token = sessionStorage.getItem("auth");
    if (!token) return null;
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadSummary() {
    const token = sessionStorage.getItem("auth");
    const res = await fetch("/api/client/summary", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

async function loadNotifications() {
    const token = sessionStorage.getItem("auth");
    const res = await fetch("/api/client/notifications?limit=30", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return [];
    return await res.json();
}

async function loadMyApplications() {
    const token = sessionStorage.getItem("auth");
    const res = await fetch("/api/osago/applications/my", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return [];
    return await res.json();
}

async function markNotificationRead(id) {
    const token = sessionStorage.getItem("auth");
    await fetch(`/api/client/notifications/${id}/read`, {
        method: "POST",
        headers: { Authorization: "Basic " + token }
    });
}

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

function formatDateTime(dt) {
    if (!dt) return "";
    return new Date(dt).toLocaleString("ru-RU");
}

function iconByType(type) {
    if (type === "NEW_POLICY_REQUEST") return "📄";
    if (type && type.startsWith("CLAIM_")) return "⚠️";
    if (type === "NEW_MESSAGE") return "💬";
    return "ℹ️";
}

function styleByType(type) {
    if (type === "NEW_POLICY_REQUEST") return { bg: "#EFF6FF", border: "#BEDBFF", iconBg: "#DBEAFE" };
    if (type && type.startsWith("CLAIM_")) return { bg: "#FFF7ED", border: "#FFD6A8", iconBg: "#FFEDD4" };
    return { bg: "#F0FDF4", border: "#B9F8CF", iconBg: "#DCFCE7" };
}

function fixKnownMojibake(text) {
    if (!text) return text;
    let out = String(text);
    out = out.replaceAll("Р С›Р С—Р В»Р В°РЎвЂљР В° Р С—Р С•Р В»Р С‘РЎРѓР В° РЎС“РЎРѓР С—Р ВµРЎв‚¬Р Р…Р В°", "Оплата полиса успешна");
    out = out.replaceAll("Р СџР С•Р В»Р С‘РЎРѓ Р В°Р С”РЎвЂљР С‘Р Р†Р С‘РЎР‚Р С•Р Р†Р В°Р Р…. Р РЋРЎвЂљР В°РЎвЂљРЎС“РЎРѓ: ACTIVE.", "Полис активирован. Статус: ACTIVE.");
    out = out.replaceAll("Р Р€Р Р†Р ВµР Т‘Р С•Р СР В»Р ВµР Р…Р С‘Р Вµ", "Уведомление");
    return out;
}

function extractClaimNumber(text) {
    if (!text) return null;
    const m = /CLM-\d{4}-\d{6}/i.exec(text);
    return m ? m[0].toUpperCase() : null;
}

function escapeRegExp(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeClaimMessage(notification) {
    const title = fixKnownMojibake(notification.title || "");
    const rawMessage = fixKnownMojibake(notification.message || "");
    const full = `${title} ${rawMessage}`;
    const claimNumber = extractClaimNumber(full);
    const tail = rawMessage.trim();
    if (claimNumber) {
        const prefix = `По вашему страховому случаю ${claimNumber} есть новая информация.`;
        if (tail.startsWith(prefix)) {
            return tail.replace(new RegExp(`^(?:${escapeRegExp(prefix)}\\s*)+`), `${prefix} `).trim();
        }
        if (tail.startsWith("По вашему страховому случаю")) {
            return tail;
        }
        return `${prefix} ${tail}`.trim();
    }
    return tail || "По вашему страховому случаю есть новая информация.";
}

function compactNotifications(list) {
    const seenClaimNumbers = new Set();
    const out = [];
    for (const n of list || []) {
        const isClaim = (n.type || "").startsWith("CLAIM_");
        if (!isClaim) {
            out.push(n);
            continue;
        }
        const number = extractClaimNumber(`${n.title || ""} ${n.message || ""}`) || `claim-${n.id}`;
        if (seenClaimNumbers.has(number)) continue;
        seenClaimNumbers.add(number);
        out.push(n);
    }
    return out;
}

function parseNotificationAction(n) {
    const body = String(n?.body || "").trim();
    const pay = /^PAY_OSAGO:(\d+)$/i.exec(body);
    if (pay) return { type: "PAY_OSAGO", applicationId: Number(pay[1]) };
    const chat = /^CHAT:(\d+)$/i.exec(body);
    if (chat) return { type: "CHAT", chatId: Number(chat[1]) };
    return null;
}

function renderNotifications(rawList) {
    const list = compactNotifications(rawList);
    const visible = list.filter((n) => !n.isRead);
    const container = document.getElementById("notificationsList");
    const badge = document.getElementById("notificationsBadge");
    badge.textContent = String(visible.length);

    if (!visible.length) {
        container.innerHTML = `
            <div class="noti__item" style="background:#fff; border-color:#E5E7EB;">
                <div class="noti__icon" style="background:#F1F5F9;">🔔</div>
                <div class="noti__content">
                    <p class="noti__title">Новых уведомлений нет</p>
                    <p class="noti__desc">Здесь будут появляться события по полисам и заявлениям.</p>
                </div>
            </div>
        `;
        return;
    }

    container.innerHTML = visible.map((n) => {
        const style = styleByType(n.type);
        const isClaim = (n.type || "").startsWith("CLAIM_");
        const action = parseNotificationAction(n);
        const title = isClaim
            ? "Обновление по страховому случаю"
            : fixKnownMojibake(n.title || "Уведомление");
        const message = isClaim
            ? normalizeClaimMessage(n)
            : fixKnownMojibake(n.message || "");

        const payBtn = action && action.type === "PAY_OSAGO"
            ? `<button class="btn btn--dark noti-pay-btn" data-id="${n.id}" data-application-id="${action.applicationId}" style="height:30px;">К оплате</button>`
            : "";
        const chatBtn = action && action.type === "CHAT"
            ? `<button class="btn btn--dark noti-chat-btn" data-id="${n.id}" style="height:30px;">Открыть чат</button>`
            : "";

        return `
            <div class="noti__item" style="background:${style.bg}; border-color:${style.border};">
                <div class="noti__icon" style="background:${style.iconBg};">${iconByType(n.type)}</div>
                <div class="noti__content">
                    <p class="noti__title">${title}</p>
                    <p class="noti__desc">${message}</p>
                    <p class="noti__desc" style="margin-top:6px; font-size:12px;">${formatDateTime(n.createdAt)}</p>
                </div>
                <div class="noti__actions">
                    ${payBtn}
                    ${chatBtn}
                    <button class="xbtn mark-read-btn" data-id="${n.id}" title="Скрыть уведомление">✓</button>
                </div>
            </div>
        `;
    }).join("");
}

document.addEventListener("DOMContentLoaded", async () => {
    const logoutBtn = document.getElementById("logoutBtn");
    const userShort = document.getElementById("userShort");

    logoutBtn.addEventListener("click", (e) => {
        e.preventDefault();
        logout();
    });

    const me = await loadMe();
    if (!me) {
        window.location.href = "/login/index.html";
        return;
    }
    if (me.status !== "CLIENT") {
        window.location.href = "/";
        return;
    }
    userShort.textContent = me.shortFio;

    const summary = await loadSummary();
    const agentPhone = (summary && summary.agentPhone) ? String(summary.agentPhone) : "+7 (800) 555-35-35";
    if (summary) {
        document.getElementById("summaryPolicies").textContent = String(summary.policies ?? 0);
        document.getElementById("summaryClaimsTotal").textContent = String(summary.claimsTotal ?? 0);
        document.getElementById("summaryClaimsInProgress").textContent = String(summary.claimsInProgress ?? 0);
    }

    const callAgentBtn = document.getElementById("callAgentBtn");
    const consultationBtn = document.getElementById("consultationBtn");
    const supportPhoneHint = document.getElementById("supportPhoneHint");

    if (callAgentBtn && supportPhoneHint) {
        callAgentBtn.addEventListener("click", () => {
            supportPhoneHint.textContent = `Телефон уполномоченного агента: ${agentPhone}`;
            supportPhoneHint.style.display = "block";
        });
    }
    if (consultationBtn) {
        consultationBtn.addEventListener("click", () => {
            window.location.href = "/cabinet/client/chat/index.html";
        });
    }

    let notifications = await loadNotifications();
    renderNotifications(notifications);

    const notificationsList = document.getElementById("notificationsList");
    notificationsList.addEventListener("click", async (e) => {
        const chatBtn = e.target.closest(".noti-chat-btn");
        if (chatBtn) {
            const notificationId = Number(chatBtn.dataset.id);
            await markNotificationRead(notificationId);
            notifications = notifications.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n));
            renderNotifications(notifications);
            window.location.href = "/cabinet/client/chat/index.html";
            return;
        }

        const payBtn = e.target.closest(".noti-pay-btn");
        if (payBtn) {
            const notificationId = Number(payBtn.dataset.id);
            const applicationId = Number(payBtn.dataset.applicationId);
            if (!applicationId) return;
            const applications = await loadMyApplications();
            const app = (applications || []).find((x) => Number(x.id) === applicationId);
            if (!app) return;

            const paymentDraft = {
                applicationId: app.id,
                policyId: app.policyId || null,
                policyNumber: app.policyNumber || `APP-${app.id}`,
                amount: app.premiumAmount,
                vehicleSummary: "ОСАГО",
                regNumber: "—",
                termMonths: "—"
            };
            sessionStorage.setItem("osagoPaymentDraft", JSON.stringify(paymentDraft));
            await markNotificationRead(notificationId);
            notifications = notifications.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n));
            renderNotifications(notifications);
            window.location.href = "/insurance/osago/payment.html";
            return;
        }

        const btn = e.target.closest(".mark-read-btn");
        if (!btn) return;
        const id = Number(btn.dataset.id);
        if (!id) return;
        await markNotificationRead(id);
        notifications = notifications.map((n) => (n.id === id ? { ...n, isRead: true } : n));
        renderNotifications(notifications);
    });
});
