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

function statusMarkup(status) {
    if (status === "ACTIVE") return '<span class="status status-active">Активен</span>';
    if (status === "DRAFT") return '<span class="status status-draft">Черновик</span>';
    if (status === "PENDING_PAY") return '<span class="status status-pending">Ожидает оплату</span>';
    return `<span class="status status-other">${status || "—"}</span>`;
}

function queryId() {
    const id = new URLSearchParams(window.location.search).get("id");
    const n = Number(id);
    return Number.isFinite(n) && n > 0 ? n : null;
}

function daysBetween(start, end) {
    if (!start || !end) return null;
    const s = new Date(start);
    const e = new Date(end);
    const ms = e.setHours(0, 0, 0, 0) - s.setHours(0, 0, 0, 0);
    return Math.ceil(ms / (1000 * 60 * 60 * 24));
}

function daysLeft(endDate) {
    if (!endDate) return null;
    const today = new Date();
    const end = new Date(endDate);
    const ms = end.setHours(0, 0, 0, 0) - today.setHours(0, 0, 0, 0);
    return Math.ceil(ms / (1000 * 60 * 60 * 24));
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    const id = queryId();
    if (!id) {
        showError("Некорректный идентификатор полиса");
        return;
    }
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent(`/cabinet/client/policies/detail.html?id=${id}`);
        return;
    }

    try {
        const res = await fetch(`/api/client/policies/${id}`, {
            headers: { Authorization: `Basic ${token}` }
        });
        if (res.status === 401) {
            sessionStorage.removeItem("auth");
            window.location.href = "/login/index.html?next=" + encodeURIComponent(`/cabinet/client/policies/detail.html?id=${id}`);
            return;
        }
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.message || `Ошибка загрузки деталей полиса (HTTP ${res.status})`);
        }

        const amount = formatMoney(data.premiumAmount);
        const validDays = daysBetween(data.startDate, data.endDate);
        const leftDays = daysLeft(data.endDate);

        document.getElementById("number").textContent = data.number || `POL-${data.id}`;
        document.getElementById("status").innerHTML = statusMarkup(data.status);
        document.getElementById("type").textContent = data.type || "—";
        document.getElementById("amount").textContent = amount;
        document.getElementById("amountTop").textContent = amount;
        document.getElementById("period").textContent = `${formatDate(data.startDate)} - ${formatDate(data.endDate)}`;
        document.getElementById("created").textContent = formatDate(data.createdAt);
        document.getElementById("vehicle").textContent = [data.brand, data.model].filter(Boolean).join(" ") || "—";
        document.getElementById("regNumber").textContent = data.regNumber || "—";
        document.getElementById("vin").textContent = data.vin || "—";
        document.getElementById("category").textContent = data.vehicleCategoryName || "—";
        document.getElementById("region").textContent = data.regionName || "—";
        document.getElementById("power").textContent = data.powerHp ? `${data.powerHp} л.с.` : "—";
        document.getElementById("drivers").textContent = data.unlimitedDrivers ? "Без ограничений" : "Ограниченный список";
        document.getElementById("term").textContent = data.termMonths ? `${data.termMonths} мес.` : "—";
        document.getElementById("durationDays").textContent = validDays !== null ? `${validDays} дн.` : "—";

        if (leftDays === null) {
            document.getElementById("daysLeft").textContent = "—";
        } else if (leftDays < 0) {
            document.getElementById("daysLeft").textContent = "Истек";
        } else {
            document.getElementById("daysLeft").textContent = `${leftDays} дн.`;
        }

        document.getElementById("downloadBtn").addEventListener("click", async () => {
            try {
                const pdfRes = await fetch(`/api/client/policies/${id}/pdf`, {
                    headers: { Authorization: `Basic ${token}` }
                });
                if (!pdfRes.ok) {
                    throw new Error(`Ошибка выгрузки PDF (HTTP ${pdfRes.status})`);
                }
                const blob = await pdfRes.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = `${data.number || `POL-${data.id}`}.pdf`;
                document.body.appendChild(a);
                a.click();
                a.remove();
                URL.revokeObjectURL(url);
            } catch (e) {
                showError(e.message || "Не удалось скачать PDF");
            }
        });

        document.getElementById("detailCard").style.display = "block";
    } catch (e) {
        showError(e.message || "Не удалось загрузить детали полиса");
    }
});
