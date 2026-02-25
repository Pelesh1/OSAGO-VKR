function showError(message) {
    const box = document.getElementById("errorBox");
    box.textContent = message;
    box.style.display = "block";
}

function hideError() {
    const box = document.getElementById("errorBox");
    box.textContent = "";
    box.style.display = "none";
}

function showSuccess(message) {
    const box = document.getElementById("successBox");
    box.textContent = message;
    box.style.display = "block";
}

function formatMoney(amount) {
    if (amount === null || amount === undefined) return "—";
    return `${Number(amount).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`;
}

function normalizeCardNumber(value) {
    return String(value || "").replace(/\D/g, "").slice(0, 19);
}

function luhnCheck(number) {
    let sum = 0;
    let shouldDouble = false;
    for (let i = number.length - 1; i >= 0; i--) {
        let digit = Number(number[i]);
        if (shouldDouble) {
            digit *= 2;
            if (digit > 9) digit -= 9;
        }
        sum += digit;
        shouldDouble = !shouldDouble;
    }
    return sum % 10 === 0;
}

function isValidExpiry(value) {
    if (!/^\d{2}\/\d{2}$/.test(value)) return false;
    const [mm, yy] = value.split("/").map((x) => Number(x));
    if (mm < 1 || mm > 12) return false;

    const now = new Date();
    const fullYear = 2000 + yy;
    const exp = new Date(fullYear, mm, 0, 23, 59, 59, 999);
    return exp >= now;
}

function isValidCardHolder(value) {
    return /^[A-Za-zА-Яа-яЁё\s\-\.]{3,}$/.test(String(value || "").trim());
}

async function loadPolicySummary(token, policyId) {
    if (!policyId) return null;
    try {
        const res = await fetch(`/api/client/policies/${policyId}`, {
            headers: { Authorization: `Basic ${token}` }
        });
        if (!res.ok) return null;
        return await res.json();
    } catch {
        return null;
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        const next = encodeURIComponent("/insurance/osago/payment.html");
        window.location.href = `/login/index.html?next=${next}`;
        return;
    }

    const raw = sessionStorage.getItem("osagoPaymentDraft");
    if (!raw) {
        showError("Нет данных для оплаты. Сначала оформите заявку на полис.");
        setTimeout(() => (window.location.href = "/insurance/osago/"), 1000);
        return;
    }

    let draft;
    try {
        draft = JSON.parse(raw);
    } catch {
        showError("Поврежден черновик оплаты.");
        return;
    }

    const policy = await loadPolicySummary(token, draft.policyId);
    const vehicleName = policy
        ? [policy.brand, policy.model].filter(Boolean).join(" ")
        : null;
    const termLabel = policy && policy.termMonths
        ? `${policy.termMonths} месяцев`
        : (draft.termMonths && draft.termMonths !== "—" ? `${draft.termMonths} месяцев` : "—");

    document.getElementById("summaryVehicle").textContent = vehicleName || draft.vehicleSummary || "—";
    document.getElementById("summaryReg").textContent = (policy && policy.regNumber) || draft.regNumber || "—";
    document.getElementById("summaryTerm").textContent = termLabel;
    document.getElementById("summaryPolicy").textContent = (policy && policy.number) || draft.policyNumber || "—";
    document.getElementById("summaryAmount").textContent = formatMoney(draft.amount);

    const cardNumber = document.getElementById("cardNumber");
    const cardExp = document.getElementById("cardExp");
    const cardCvc = document.getElementById("cardCvc");
    const cardHolder = document.getElementById("cardHolder");
    const payBtn = document.getElementById("payBtn");

    cardNumber.addEventListener("input", () => {
        const cleaned = normalizeCardNumber(cardNumber.value);
        cardNumber.value = cleaned.replace(/(.{4})/g, "$1 ").trim();
    });

    cardExp.addEventListener("input", () => {
        let v = cardExp.value.replace(/\D/g, "").slice(0, 4);
        if (v.length >= 3) v = v.slice(0, 2) + "/" + v.slice(2);
        cardExp.value = v;
    });

    cardCvc.addEventListener("input", () => {
        cardCvc.value = cardCvc.value.replace(/\D/g, "").slice(0, 4);
    });

    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/insurance/osago/checkout.html";
    });

    payBtn.addEventListener("click", async () => {
        hideError();

        const number = normalizeCardNumber(cardNumber.value);
        const exp = cardExp.value.trim();
        const cvc = cardCvc.value.replace(/\D/g, "");
        const holder = cardHolder.value.trim();

        if (number.length < 16 || number.length > 19) return showError("Проверьте номер карты");
        if (!isValidExpiry(exp)) return showError("Проверьте срок действия карты");
        if (cvc.length < 3 || cvc.length > 4) return showError("Проверьте CVC/CVV");
        if (!isValidCardHolder(holder)) return showError("Проверьте имя владельца карты");

        payBtn.disabled = true;
        payBtn.textContent = "Оплата...";

        try {
            const payRes = await fetch(`/api/osago/applications/${draft.applicationId}/pay`, {
                method: "POST",
                headers: {
                    Authorization: `Basic ${token}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    provider: "card-demo",
                    returnUrl: "/cabinet/client/policies/index.html"
                })
            });
            const payData = await payRes.json().catch(() => ({}));
            if (!payRes.ok) {
                throw new Error(payData.message || `Ошибка создания платежа (HTTP ${payRes.status})`);
            }

            const confirmRes = await fetch(`/api/osago/applications/${draft.applicationId}/pay/confirm`, {
                method: "POST",
                headers: {
                    Authorization: `Basic ${token}`
                }
            });
            const confirmData = await confirmRes.json().catch(() => ({}));
            if (!confirmRes.ok) {
                throw new Error(confirmData.message || `Ошибка подтверждения оплаты (HTTP ${confirmRes.status})`);
            }

            showSuccess(`Оплата успешна. Полис ${draft.policyNumber} активирован.`);
            sessionStorage.removeItem("osagoPaymentDraft");
            sessionStorage.removeItem("osagoCalcDraft");
            sessionStorage.removeItem("osagoCheckoutPayload");
            setTimeout(() => {
                window.location.href = "/cabinet/client/policies/index.html";
            }, 900);
        } catch (e) {
            showError(e.message || "Не удалось провести оплату");
        } finally {
            payBtn.disabled = false;
            payBtn.textContent = "Оплатить";
        }
    });
});
