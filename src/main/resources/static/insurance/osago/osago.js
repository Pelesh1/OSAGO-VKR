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

function formatMoney(amount) {
    if (amount === null || amount === undefined) return "—";
    return `${Number(amount).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} ₽`;
}

function fillSelect(select, items, valueKey, textFn) {
    select.innerHTML = "";
    (items || []).forEach((item) => {
        const opt = document.createElement("option");
        opt.value = String(item[valueKey]);
        opt.textContent = textFn(item);
        select.appendChild(opt);
    });
}

function toIsoDateYearsAgo(years) {
    const d = new Date();
    d.setFullYear(d.getFullYear() - years);
    return d.toISOString().slice(0, 10);
}

function getTrimmedValue(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : "";
}

function digitsOnly(value) {
    return String(value || "").replace(/\D+/g, "");
}

function isValidEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || "").trim());
}

function isValidRuPhone(value) {
    const d = digitsOnly(value);
    return d.length === 11 && (d.startsWith("7") || d.startsWith("8"));
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

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");

    const stepMarkers = Array.from(document.querySelectorAll("[data-step-marker]"));
    const stepSections = Array.from(document.querySelectorAll("[data-step]"));
    const backBtn = document.getElementById("backBtn");
    const nextBtn = document.getElementById("nextBtn");
    const resultAmountEl = document.getElementById("resultAmount");
    const resultInfoEl = document.getElementById("resultInfo");
    const agreeWithPrice = document.getElementById("agreeWithPrice");

    const vehicleCategoryId = document.getElementById("vehicleCategoryId");
    const regionId = document.getElementById("regionId");
    const powerHp = document.getElementById("powerHp");
    const termMonths = document.getElementById("termMonths");
    const unlimitedDrivers = document.getElementById("unlimitedDrivers");
    const driverBirthDate = document.getElementById("driverBirthDate");
    const licenseIssuedDate = document.getElementById("licenseIssuedDate");
    const driverFields = document.getElementById("driverFields");

    let currentStep = 1;
    let calcDraft = null;

    function syncDriverVisibility() {
        const hidden = Boolean(unlimitedDrivers.checked);
        driverFields.style.display = hidden ? "none" : "grid";
        driverBirthDate.required = !hidden;
        licenseIssuedDate.required = !hidden;
    }

    function goToStep(step) {
        currentStep = Math.max(1, Math.min(4, step));
        stepSections.forEach((el) => {
            el.classList.toggle("active", Number(el.dataset.step) === currentStep);
        });
        stepMarkers.forEach((el) => {
            const markerStep = Number(el.dataset.stepMarker);
            el.classList.toggle("active", markerStep === currentStep);
            el.classList.toggle("done", markerStep < currentStep);
        });

        backBtn.disabled = false;
        if (currentStep === 4) {
            nextBtn.textContent = "Перейти к оформлению";
        } else if (currentStep === 3) {
            nextBtn.textContent = "Узнать точные цены";
        } else {
            nextBtn.textContent = "Продолжить";
        }
        backBtn.textContent = currentStep === 1 ? "В кабинет" : "Назад";
    }

    function validateStep1() {
        const brand = getTrimmedValue("brand");
        const model = getTrimmedValue("model");
        const vin = getTrimmedValue("vin");
        const regNumber = getTrimmedValue("regNumber");
        const year = Number(document.getElementById("year").value);
        const hp = Number(powerHp.value);

        if (!vehicleCategoryId.value) return "Выберите категорию ТС";
        if (!regionId.value) return "Выберите регион регистрации";
        if (!brand) return "Укажите марку автомобиля";
        if (brand.length < 2) return "Марка должна быть не короче 2 символов";
        if (!model) return "Укажите модель автомобиля";
        if (!year || year < 1970 || year > new Date().getFullYear() + 1) return "Проверьте год выпуска";
        if (!hp || hp <= 0 || hp > 2000) return "Проверьте мощность двигателя";
        if (!vin && !regNumber) return "Укажите VIN или госномер";
        if (vin && !isLikelyVin(vin)) return "VIN введен в неверном формате";
        if (regNumber && !isLikelyRegNumber(regNumber)) return "Госномер введен в неверном формате";
        return null;
    }

    function validateStep2() {
        if (!termMonths.value) return "Выберите срок полиса";

        if (!unlimitedDrivers.checked) {
            const last = getTrimmedValue("driverLastName");
            const first = getTrimmedValue("driverFirstName");
            const licenseNumber = getTrimmedValue("driverLicenseNumber");
            if (!last || !first) return "Заполните ФИО водителя";
            if (!/^[А-Яа-яA-Za-zЁё\-\s]{2,}$/.test(last)) return "Проверьте фамилию водителя";
            if (!/^[А-Яа-яA-Za-zЁё\-\s]{2,}$/.test(first)) return "Проверьте имя водителя";
            if (!driverBirthDate.value) return "Укажите дату рождения водителя";
            if (!licenseIssuedDate.value) return "Укажите дату начала стажа";
            if (!isAdult(driverBirthDate.value, 16)) return "Возраст водителя должен быть не менее 16 лет";
            if (parseDateInput(licenseIssuedDate.value) > new Date()) return "Дата начала стажа не может быть в будущем";
            if (!licenseNumber || digitsOnly(licenseNumber).length < 6) return "Проверьте номер водительского удостоверения";
        }
        return null;
    }

    function validateStep3() {
        const insuredLast = getTrimmedValue("insuredLastName");
        const insuredFirst = getTrimmedValue("insuredFirstName");
        const insuredBirthDate = document.getElementById("insuredBirthDate").value;
        const passportRaw = getTrimmedValue("passportSeries");
        const passportIssueDate = document.getElementById("passportIssueDate").value;
        const regAddress = getTrimmedValue("registrationAddress");
        const email = getTrimmedValue("email");
        const phone = getTrimmedValue("phone");
        const consentPersonalData = document.getElementById("consentPersonalData").checked;
        const consentAccuracy = document.getElementById("consentAccuracy").checked;

        if (!insuredLast || !insuredFirst) return "Заполните ФИО страхователя";
        if (!/^[А-Яа-яA-Za-zЁё\-\s]{2,}$/.test(insuredLast)) return "Проверьте фамилию страхователя";
        if (!/^[А-Яа-яA-Za-zЁё\-\s]{2,}$/.test(insuredFirst)) return "Проверьте имя страхователя";
        if (!insuredBirthDate) return "Укажите дату рождения страхователя";
        if (!isAdult(insuredBirthDate, 18)) return "Страхователь должен быть не младше 18 лет";
        if (!passportRaw) return "Укажите серию и номер паспорта";
        if (digitsOnly(passportRaw).length < 10) return "Проверьте серию и номер паспорта";
        if (!passportIssueDate) return "Укажите дату выдачи паспорта";
        if (parseDateInput(passportIssueDate) > new Date()) return "Дата выдачи паспорта не может быть в будущем";
        if (!regAddress || regAddress.length < 6) return "Укажите полный адрес регистрации";
        if (!email) return "Укажите электронную почту";
        if (!isValidEmail(email)) return "Проверьте электронную почту";
        if (!phone) return "Укажите мобильный телефон";
        if (!isValidRuPhone(phone)) return "Проверьте формат телефона";
        if (!consentPersonalData || !consentAccuracy) return "Нужно отметить оба согласия";
        return null;
    }

    async function loadRefData() {
        const headers = token ? { Authorization: `Basic ${token}` } : {};
        const res = await fetch("/api/osago/ref-data", { headers });
        if (!res.ok) {
            const txt = await res.text();
            throw new Error(txt || `Ошибка загрузки справочников (HTTP ${res.status})`);
        }
        const data = await res.json();
        fillSelect(vehicleCategoryId, data.vehicleCategories, "id", (x) => x.name);
        fillSelect(regionId, data.regions, "id", (x) => x.name);
        fillSelect(termMonths, data.terms, "months", (x) => x.name);
    }

    async function runCalculation() {
        const payload = {
            vehicleCategoryId: Number(vehicleCategoryId.value),
            regionId: Number(regionId.value),
            powerHp: Number(powerHp.value),
            unlimitedDrivers: Boolean(unlimitedDrivers.checked),
            termMonths: Number(termMonths.value),
            driverBirthDate: unlimitedDrivers.checked ? null : driverBirthDate.value,
            licenseIssuedDate: unlimitedDrivers.checked ? null : licenseIssuedDate.value,
            kbmClassCode: null
        };

        const headers = { "Content-Type": "application/json" };
        if (token) headers.Authorization = `Basic ${token}`;

        const res = await fetch("/api/osago/calc", {
            method: "POST",
            headers,
            body: JSON.stringify(payload)
        });
        if (res.status === 401) {
            sessionStorage.removeItem("auth");
            throw new Error("Сессия истекла, войдите снова");
        }
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.message || `Ошибка расчета (HTTP ${res.status})`);
        }

        calcDraft = {
            ...data,
            form: {
                vehicleCategoryId: payload.vehicleCategoryId,
                regionId: payload.regionId,
                powerHp: payload.powerHp,
                unlimitedDrivers: payload.unlimitedDrivers,
                termMonths: payload.termMonths,
                driverBirthDate: payload.driverBirthDate,
                licenseIssuedDate: payload.licenseIssuedDate,
                vinOrReg: getTrimmedValue("vin") || getTrimmedValue("regNumber"),
                vehicle: {
                    brand: getTrimmedValue("brand"),
                    model: getTrimmedValue("model"),
                    year: getTrimmedValue("year"),
                    vin: getTrimmedValue("vin"),
                    regNumber: getTrimmedValue("regNumber")
                },
                driver: {
                    lastName: getTrimmedValue("driverLastName"),
                    firstName: getTrimmedValue("driverFirstName"),
                    middleName: getTrimmedValue("driverMiddleName"),
                    birthDate: payload.driverBirthDate,
                    licenseNumber: getTrimmedValue("driverLicenseNumber"),
                    licenseIssuedDate: payload.licenseIssuedDate
                },
                insured: {
                    lastName: getTrimmedValue("insuredLastName"),
                    firstName: getTrimmedValue("insuredFirstName"),
                    middleName: getTrimmedValue("insuredMiddleName"),
                    birthDate: document.getElementById("insuredBirthDate").value || null,
                    passportRaw: getTrimmedValue("passportSeries"),
                    passportIssueDate: document.getElementById("passportIssueDate").value || null,
                    registrationAddress: getTrimmedValue("registrationAddress"),
                    apartment: getTrimmedValue("apartment")
                },
                contacts: {
                    email: getTrimmedValue("email"),
                    phone: getTrimmedValue("phone")
                },
                consents: {
                    personalData: document.getElementById("consentPersonalData").checked,
                    accuracy: document.getElementById("consentAccuracy").checked,
                    agreeWithPrice: document.getElementById("agreeWithPrice").checked
                }
            }
        };
        sessionStorage.setItem("osagoCalcDraft", JSON.stringify(calcDraft));
        resultAmountEl.textContent = formatMoney(data.resultAmount);
        resultInfoEl.textContent = `КТ: ${data.coeffRegion}, КМ: ${data.coeffPower}, КО: ${data.coeffDrivers}, КС: ${data.coeffTerm}, КВС: ${data.coeffKvs}, КБМ: ${data.coeffKbm}`;
    }

    backBtn.addEventListener("click", () => {
        hideError();
        if (currentStep > 1) {
            goToStep(currentStep - 1);
            return;
        }
        window.location.href = sessionStorage.getItem("auth") ? "/cabinet/client/index.html" : "/";
    });

    nextBtn.addEventListener("click", async () => {
        hideError();
        try {
            if (currentStep === 1) {
                const err = validateStep1();
                if (err) throw new Error(err);
                goToStep(2);
                return;
            }
            if (currentStep === 2) {
                const err = validateStep2();
                if (err) throw new Error(err);
                goToStep(3);
                return;
            }
            if (currentStep === 3) {
                const err = validateStep3();
                if (err) throw new Error(err);
                nextBtn.disabled = true;
                nextBtn.textContent = "Считаем...";
                await runCalculation();
                goToStep(4);
                return;
            }
            if (currentStep === 4) {
                if (!calcDraft) throw new Error("Сначала выполните расчет");
                if (!agreeWithPrice.checked) throw new Error("Подтвердите, что стоимость вас устраивает");
                if (!sessionStorage.getItem("auth")) {
                    const next = encodeURIComponent("/insurance/osago/checkout.html");
                    window.location.href = `/login/index.html?next=${next}`;
                    return;
                }
                window.location.href = "/insurance/osago/checkout.html";
            }
        } catch (e) {
            showError(e.message || "Ошибка");
        } finally {
            nextBtn.disabled = false;
            if (currentStep !== 4) {
                nextBtn.textContent = currentStep === 3 ? "Узнать точные цены" : "Продолжить";
            }
        }
    });

    try {
        await loadRefData();
        driverBirthDate.value = toIsoDateYearsAgo(30);
        licenseIssuedDate.value = toIsoDateYearsAgo(10);
        document.getElementById("insuredBirthDate").value = toIsoDateYearsAgo(30);
        syncDriverVisibility();
        goToStep(1);
    } catch (e) {
        showError(e.message || "Не удалось загрузить страницу");
        nextBtn.disabled = true;
    }

    unlimitedDrivers.addEventListener("change", syncDriverVisibility);
});
