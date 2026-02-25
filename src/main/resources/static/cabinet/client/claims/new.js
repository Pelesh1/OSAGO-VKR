const MAX_FILE_SIZE = 20 * 1024 * 1024;
const MAX_FILES_PER_GROUP = 10;

let damageFiles = [];
let docsFiles = [];

function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

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

function hideSuccess() {
    const box = document.getElementById("successBox");
    box.style.display = "none";
    box.textContent = "";
}

function toIsoWithTimezone(dateValue, timeValue) {
    const local = new Date(`${dateValue}T${timeValue}`);
    if (Number.isNaN(local.getTime())) return null;
    return local.toISOString();
}

function normalizePhone(raw) {
    if (!raw) return null;
    const digits = raw.replace(/\D+/g, "");
    if (digits.length === 11 && digits.startsWith("8")) return "+7" + digits.slice(1);
    if (digits.length === 11 && digits.startsWith("7")) return "+" + digits;
    if (digits.length === 10) return "+7" + digits;
    return null;
}

function validateEmail(email) {
    if (!email) return true;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function clearInvalid() {
    document.querySelectorAll(".invalid").forEach((el) => el.classList.remove("invalid"));
}

function markInvalid(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add("invalid");
    if (typeof el.focus === "function") el.focus();
}

function validateFiles(files, kind) {
    if (files.length > MAX_FILES_PER_GROUP) {
        return `Можно прикрепить не более ${MAX_FILES_PER_GROUP} файлов в блоке \"${kind}\".`;
    }
    for (const f of files) {
        if (f.size > MAX_FILE_SIZE) return `Файл \"${f.name}\" больше 20MB.`;
        const lower = f.name.toLowerCase();
        if (kind === "Фото") {
            if (!(f.type || "").startsWith("image/")) return `Файл \"${f.name}\" не является изображением.`;
        } else {
            const ok = lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".doc") || lower.endsWith(".docx");
            if (!ok) return `Файл \"${f.name}\" имеет недопустимый формат.`;
        }
    }
    return null;
}

function renderDamageFiles() {
    const box = document.getElementById("damageFileList");
    if (!damageFiles.length) {
        box.innerHTML = "";
        return;
    }
    box.innerHTML = damageFiles.map((f, i) => {
        const mb = (f.size / (1024 * 1024)).toFixed(2);
        return `<div class=\"file-item\"><span class=\"file-name\" title=\"${f.name}\">${f.name} (${mb} MB)</span><button type=\"button\" class=\"file-remove\" data-damage-index=\"${i}\">Удалить</button></div>`;
    }).join("");
}

function renderDocsFiles() {
    const box = document.getElementById("docsFileList");
    if (!docsFiles.length) {
        box.innerHTML = "";
        return;
    }
    box.innerHTML = docsFiles.map((f, i) => {
        const mb = (f.size / (1024 * 1024)).toFixed(2);
        return `<div class=\"file-item\"><span class=\"file-name\" title=\"${f.name}\">${f.name} (${mb} MB)</span><button type=\"button\" class=\"file-remove\" data-doc-index=\"${i}\">Удалить</button></div>`;
    }).join("");
}

function attachDropzone(dropzoneId, inputId, onFilesPicked) {
    const zone = document.getElementById(dropzoneId);
    const input = document.getElementById(inputId);

    zone.addEventListener("click", () => input.click());
    zone.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            input.click();
        }
    });

    input.addEventListener("change", () => {
        if (input.files && input.files.length) {
            onFilesPicked(Array.from(input.files));
            input.value = "";
        }
    });

    zone.addEventListener("dragover", (e) => {
        e.preventDefault();
        zone.classList.add("dragover");
    });
    zone.addEventListener("dragleave", () => zone.classList.remove("dragover"));
    zone.addEventListener("drop", (e) => {
        e.preventDefault();
        zone.classList.remove("dragover");
        const dropped = e.dataTransfer?.files ? Array.from(e.dataTransfer.files) : [];
        if (dropped.length) onFilesPicked(dropped);
    });
}

async function loadPolicies(token) {
    const res = await fetch("/api/client/claims/policies", {
        headers: { Authorization: `Basic ${token}` }
    });

    if (res.status === 401) {
        logout();
        return [];
    }
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Ошибка загрузки полисов (HTTP ${res.status})`);
    }

    return await res.json();
}

function fillPolicies(policies) {
    const select = document.getElementById("policyId");
    select.innerHTML = "";

    if (!policies.length) {
        const option = document.createElement("option");
        option.value = "";
        option.textContent = "Нет доступных полисов";
        select.appendChild(option);
        select.disabled = true;
        return;
    }

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Выберите полис";
    placeholder.disabled = true;
    placeholder.selected = true;
    select.appendChild(placeholder);

    for (const policy of policies) {
        const option = document.createElement("option");
        option.value = String(policy.id);
        option.textContent = policy.number || `Полис #${policy.id}`;
        select.appendChild(option);
    }
}

function validateForm() {
    clearInvalid();

    const policyIdRaw = document.getElementById("policyId").value;
    const accidentDate = document.getElementById("accidentDate").value;
    const accidentTime = document.getElementById("accidentTime").value;
    const accidentAt = toIsoWithTimezone(accidentDate, accidentTime);

    if (!policyIdRaw) {
        markInvalid("policyId");
        return { ok: false, message: "Выберите полис." };
    }
    if (!accidentAt) {
        markInvalid("accidentDate");
        return { ok: false, message: "Укажите корректные дату и время аварии." };
    }
    if (new Date(accidentAt).getTime() > Date.now()) {
        markInvalid("accidentDate");
        return { ok: false, message: "Дата и время аварии не могут быть в будущем." };
    }

    const accidentPlace = document.getElementById("accidentPlace").value.trim();
    if (accidentPlace.length < 5) {
        markInvalid("accidentPlace");
        return { ok: false, message: "Место происшествия должно быть не короче 5 символов." };
    }

    const description = document.getElementById("description").value.trim();
    if (description.length < 20) {
        markInvalid("description");
        return { ok: false, message: "Описание должно быть не короче 20 символов." };
    }

    const phoneNormalized = normalizePhone(document.getElementById("contactPhone").value.trim());
    if (!phoneNormalized) {
        markInvalid("contactPhone");
        return { ok: false, message: "Укажите корректный номер телефона в формате +7XXXXXXXXXX." };
    }

    const email = document.getElementById("contactEmail").value.trim();
    if (email && !validateEmail(email)) {
        markInvalid("contactEmail");
        return { ok: false, message: "Укажите корректный email." };
    }

    if (!document.getElementById("consentAccuracy").checked) {
        markInvalid("consentAccuracy");
        return { ok: false, message: "Подтвердите достоверность введенных данных." };
    }
    if (!document.getElementById("consentPersonalData").checked) {
        markInvalid("consentPersonalData");
        return { ok: false, message: "Подтвердите согласие на обработку персональных данных." };
    }

    const photoErr = validateFiles(damageFiles, "Фото");
    if (photoErr) return { ok: false, message: photoErr };
    const docsErr = validateFiles(docsFiles, "Документы");
    if (docsErr) return { ok: false, message: docsErr };

    return {
        ok: true,
        payload: {
            policyId: Number(policyIdRaw),
            accidentType: document.getElementById("accidentType").value,
            accidentAt,
            accidentPlace,
            description,
            contactPhone: phoneNormalized,
            contactEmail: email || null,
            consentPersonalData: true,
            consentAccuracy: true
        }
    };
}

async function uploadFiles(token, claimId, files, attachmentType) {
    for (const file of files) {
        const form = new FormData();
        form.append("file", file);
        form.append("attachmentType", attachmentType);

        const res = await fetch(`/api/client/claims/${claimId}/attachments`, {
            method: "POST",
            headers: { Authorization: `Basic ${token}` },
            body: form
        });

        if (res.status === 401) {
            logout();
            return;
        }
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || `Не удалось загрузить файл \"${file.name}\"`);
        }
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html";
        return;
    }

    document.getElementById("cabinetBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });
    document.getElementById("cancelBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/client/index.html";
    });

    attachDropzone("damageDropzone", "damageFilesInput", (picked) => {
        damageFiles = [...damageFiles, ...picked].slice(0, MAX_FILES_PER_GROUP);
        renderDamageFiles();
    });

    attachDropzone("docsDropzone", "accidentDocsInput", (picked) => {
        docsFiles = [...docsFiles, ...picked].slice(0, MAX_FILES_PER_GROUP);
        renderDocsFiles();
    });

    document.getElementById("damageFileList").addEventListener("click", (e) => {
        const btn = e.target.closest("[data-damage-index]");
        if (!btn) return;
        const idx = Number(btn.dataset.damageIndex);
        if (Number.isNaN(idx)) return;
        damageFiles.splice(idx, 1);
        renderDamageFiles();
    });

    document.getElementById("docsFileList").addEventListener("click", (e) => {
        const btn = e.target.closest("[data-doc-index]");
        if (!btn) return;
        const idx = Number(btn.dataset.docIndex);
        if (Number.isNaN(idx)) return;
        docsFiles.splice(idx, 1);
        renderDocsFiles();
    });

    try {
        const policies = await loadPolicies(token);
        fillPolicies(policies);
    } catch (e) {
        showError(e.message || "Не удалось загрузить полисы");
    }

    document.getElementById("claimForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        hideError();
        hideSuccess();

        const check = validateForm();
        if (!check.ok) {
            showError(check.message);
            return;
        }

        const submitBtn = document.getElementById("submitBtn");
        submitBtn.disabled = true;
        submitBtn.textContent = "Отправка...";

        try {
            const createRes = await fetch("/api/client/claims", {
                method: "POST",
                headers: {
                    Authorization: `Basic ${token}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(check.payload)
            });

            if (createRes.status === 401) {
                logout();
                return;
            }

            const created = await createRes.json().catch(() => ({}));
            if (!createRes.ok) {
                throw new Error(created.message || `Ошибка отправки (HTTP ${createRes.status})`);
            }

            if (damageFiles.length) await uploadFiles(token, created.id, damageFiles, "DAMAGE_PHOTO");
            if (docsFiles.length) await uploadFiles(token, created.id, docsFiles, "ACCIDENT_DOC");

            showSuccess(`Заявка ${created.number || ""} успешно создана.`);
            setTimeout(() => {
                window.location.href = "/cabinet/client/claims/index.html";
            }, 1200);
        } catch (e) {
            showError(e.message || "Не удалось отправить заявку");
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "Отправить заявку";
        }
    });
});
