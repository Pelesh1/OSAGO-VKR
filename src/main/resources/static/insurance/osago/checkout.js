function showError(message) {
    const box = document.getElementById("errorBox");
    box.textContent = message;
    box.style.display = "block";
}

function hideError() {
    const box = document.getElementById("errorBox");
    box.style.display = "none";
    box.textContent = "";
}

function showSuccess(message) {
    const box = document.getElementById("successBox");
    box.textContent = message;
    box.style.display = "block";
}

function formatMoney(amount) {
    if (amount === null || amount === undefined) return "—";
    return `${Number(amount).toLocaleString("ru-RU", { minimumFractionDigits: 0, maximumFractionDigits: 2 })} ₽`;
}

function initStartDate() {
    const input = document.getElementById("startDate");
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, "0");
    const dd = String(now.getDate()).padStart(2, "0");
    input.value = `${yyyy}-${mm}-${dd}`;
}

function setValueIfEmpty(id, value) {
    const el = document.getElementById(id);
    if (!el || value === null || value === undefined) return;
    if (!String(el.value || "").trim()) {
        el.value = String(value);
    }
}

function splitPassport(raw) {
    const digits = String(raw || "").replace(/\D+/g, "");
    if (!digits) return { series: "", number: "" };
    return {
        series: digits.slice(0, 4),
        number: digits.slice(4, 10)
    };
}

function normalizeStatusLabel(status) {
    const s = String(status || "").toUpperCase();
    if (s === "NEW") return "на проверке агента";
    if (s === "IN_REVIEW") return "в работе";
    if (s === "NEED_INFO") return "нужны уточнения";
    if (s === "APPROVED") return "можно оплачивать";
    if (s === "REJECTED") return "отклонена";
    if (s === "PAYMENT_PENDING") return "ожидает оплату";
    if (s === "PAID") return "оплачена";
    return s || "NEW";
}

function digitsOnly(value) {
    return String(value || "").replace(/\D+/g, "");
}

function isLikelyVin(value) {
    const v = String(value || "").trim().toUpperCase();
    return /^[A-HJ-NPR-Z0-9]{11,17}$/.test(v);
}

function isLikelyRegNumber(value) {
    const s = String(value || "").trim().toUpperCase().replace(/\s+/g, "");
    return /^[АВЕКМНОРСТУХABEKMHOPCTYX][0-9]{3}[АВЕКМНОРСТУХABEKMHOPCTYX]{2}[0-9]{2,3}$/.test(s);
}

function parseDateInput(value) {
    if (!value) return null;
    const d = new Date(value + "T00:00:00");
    return Number.isNaN(d.getTime()) ? null : d;
}

function isAdult(value, years) {
    const d = parseDateInput(value);
    if (!d) return false;
    const now = new Date();
    const adultDate = new Date(d);
    adultDate.setFullYear(adultDate.getFullYear() + years);
    return adultDate <= now;
}

function validateCheckout(payload) {
    if (!payload.vehicle.brand || payload.vehicle.brand.length < 2) return "Проверьте марку автомобиля";
    if (payload.vehicle.model && payload.vehicle.model.length < 1) return "Проверьте модель автомобиля";

    const hasVin = Boolean(payload.vehicle.vin);
    const hasReg = Boolean(payload.vehicle.regNumber);
    if (!hasVin && !hasReg) return "Укажите VIN или госномер";
    if (hasVin && !isLikelyVin(payload.vehicle.vin)) return "VIN введен в неверном формате";
    if (hasReg && !isLikelyRegNumber(payload.vehicle.regNumber)) return "Госномер введен в неверном формате";

    if (!payload.insuredPerson.birthDate) return "Укажите дату рождения страхователя";
    if (!isAdult(payload.insuredPerson.birthDate, 18)) return "Страхователь должен быть не младше 18 лет";

    const passSeries = digitsOnly(payload.insuredPerson.passportSeries);
    const passNumber = digitsOnly(payload.insuredPerson.passportNumber);
    if (passSeries.length !== 4) return "Серия паспорта должна содержать 4 цифры";
    if (passNumber.length !== 6) return "Номер паспорта должен содержать 6 цифр";

    if (!payload.insuredPerson.passportIssueDate) return "Укажите дату выдачи паспорта";
    if (parseDateInput(payload.insuredPerson.passportIssueDate) > new Date()) return "Дата выдачи паспорта не может быть в будущем";
    if (!payload.insuredPerson.passportIssuer || payload.insuredPerson.passportIssuer.length < 5) return "Укажите орган, выдавший паспорт";
    if (!payload.insuredPerson.registrationAddress || payload.insuredPerson.registrationAddress.length < 6) return "Укажите полный адрес регистрации";

    const dl = digitsOnly(payload.driverInfo.driverLicenseNumber);
    if (dl.length < 6) return "Проверьте номер водительского удостоверения";
    if (!payload.driverInfo.licenseIssuedDate) return "Укажите дату выдачи водительского удостоверения";
    if (parseDateInput(payload.driverInfo.licenseIssuedDate) > new Date()) return "Дата выдачи удостоверения не может быть в будущем";

    if (!payload.startDate) return "Укажите дату начала действия полиса";
    if (parseDateInput(payload.startDate) < parseDateInput(new Date().toISOString().slice(0, 10))) return "Дата начала полиса не может быть в прошлом";

    if (!payload.consentAccuracy || !payload.consentPersonalData) return "Подтвердите оба согласия";

    return null;
}

document.addEventListener("DOMContentLoaded", () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        const next = encodeURIComponent("/insurance/osago/checkout.html");
        window.location.href = `/login/index.html?next=${next}`;
        return;
    }

    const rawDraft = sessionStorage.getItem("osagoCalcDraft");
    if (!rawDraft) {
        showError("Сначала выполните расчет ОСАГО");
        setTimeout(() => {
            window.location.href = "/insurance/osago/";
        }, 900);
        return;
    }

    let draft;
    try {
        draft = JSON.parse(rawDraft);
    } catch {
        showError("Повреждены данные расчета, выполните расчет повторно");
        return;
    }

    document.getElementById("sumValue").textContent = formatMoney(draft.resultAmount);
    document.getElementById("calcInfo").innerHTML = `
        <div><b>Расчет #${draft.calcRequestId}</b></div>
        <div class="small">Категория ТС: ${draft.form?.vehicleCategoryId ?? "—"} · Регион: ${draft.form?.regionId ?? "—"} · Мощность: ${draft.form?.powerHp ?? "—"} л.с.</div>
    `;

    const vehicle = draft.form?.vehicle || {};
    setValueIfEmpty("brand", vehicle.brand);
    setValueIfEmpty("model", vehicle.model);
    setValueIfEmpty("vin", vehicle.vin);
    setValueIfEmpty("regNumber", vehicle.regNumber);

    const insured = draft.form?.insured || {};
    setValueIfEmpty("birthDate", insured.birthDate);
    setValueIfEmpty("passportIssueDate", insured.passportIssueDate);
    const passport = splitPassport(insured.passportRaw);
    setValueIfEmpty("passportSeries", passport.series);
    setValueIfEmpty("passportNumber", passport.number);
    const fullAddress = [insured.registrationAddress, insured.apartment].filter(Boolean).join(", кв. ");
    setValueIfEmpty("registrationAddress", fullAddress || insured.registrationAddress);

    const driver = draft.form?.driver || {};
    setValueIfEmpty("driverLicenseNumber", driver.licenseNumber);
    setValueIfEmpty("licenseIssuedDate", driver.licenseIssuedDate);

    const consents = draft.form?.consents || {};
    if (consents.accuracy) document.getElementById("consentAccuracy").checked = true;
    if (consents.personalData) document.getElementById("consentPersonalData").checked = true;

    initStartDate();

    const payBtn = document.getElementById("payBtn");
    if (payBtn) {
        payBtn.textContent = "Отправить агенту";
    }

    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/insurance/osago/";
    });

    const form = document.getElementById("checkoutForm");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        hideError();

        const payload = {
            calcRequestId: draft.calcRequestId,
            vehicle: {
                brand: document.getElementById("brand").value.trim(),
                model: document.getElementById("model").value.trim() || null,
                vin: document.getElementById("vin").value.trim().toUpperCase() || null,
                regNumber: document.getElementById("regNumber").value.trim().toUpperCase() || null
            },
            insuredPerson: {
                birthDate: document.getElementById("birthDate").value || null,
                passportSeries: document.getElementById("passportSeries").value.trim(),
                passportNumber: document.getElementById("passportNumber").value.trim(),
                passportIssueDate: document.getElementById("passportIssueDate").value || null,
                passportIssuer: document.getElementById("passportIssuer").value.trim() || null,
                registrationAddress: document.getElementById("registrationAddress").value.trim() || null
            },
            driverInfo: {
                driverLicenseNumber: document.getElementById("driverLicenseNumber").value.trim(),
                licenseIssuedDate: document.getElementById("licenseIssuedDate").value || null
            },
            startDate: document.getElementById("startDate").value || null,
            consentAccuracy: document.getElementById("consentAccuracy").checked,
            consentPersonalData: document.getElementById("consentPersonalData").checked
        };

        const validationError = validateCheckout(payload);
        if (validationError) {
            showError(validationError);
            return;
        }

        payBtn.disabled = true;
        payBtn.textContent = "Отправка...";

        try {
            const createRes = await fetch("/api/osago/applications", {
                method: "POST",
                headers: {
                    Authorization: `Basic ${token}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            if (createRes.status === 401) {
                sessionStorage.removeItem("auth");
                window.location.href = "/login/index.html";
                return;
            }

            const createData = await createRes.json().catch(() => ({}));
            if (!createRes.ok) {
                throw new Error(createData.message || `Ошибка оформления (HTTP ${createRes.status})`);
            }

            sessionStorage.setItem("osagoCheckoutPayload", JSON.stringify(payload));
            sessionStorage.removeItem("osagoPaymentDraft");
            sessionStorage.setItem("osagoSubmittedBanner", createData.policyNumber || `#${createData.applicationId}`);
            showSuccess(`Заявка ${createData.policyNumber} отправлена агенту. Текущий статус: ${normalizeStatusLabel(createData.status)}.`);
            setTimeout(() => {
                window.location.href = "/cabinet/client/policies/index.html#applications";
            }, 700);
        } catch (e) {
            showError(e.message || "Не удалось отправить заявку агенту");
        } finally {
            payBtn.disabled = false;
            payBtn.textContent = "Отправить агенту";
        }
    });
});
