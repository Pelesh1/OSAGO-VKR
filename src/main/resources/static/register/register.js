const form = document.getElementById("regForm");
const err = document.getElementById("err");
const ok = document.getElementById("ok");
const emailInput = document.getElementById("email");
const emailErr = document.getElementById("emailErr");

function showError(msg) {
    ok.style.display = "none";
    err.textContent = msg;
    err.style.display = "block";
}

function showOk(msg) {
    err.style.display = "none";
    ok.textContent = msg;
    ok.style.display = "block";
}

function isNameValid(value) {
    return /^[A-Za-zА-Яа-яЁё\-\s]+$/.test(value);
}

function isPasswordStrong(value) {
    const hasLower = /[a-zа-яё]/i.test(value);
    const hasDigit = /\d/.test(value);
    const hasSymbol = /[^A-Za-zА-Яа-яЁё0-9]/.test(value);
    return value.length >= 8 && hasLower && hasDigit && hasSymbol;
}

function isEmailValid(value) {
    const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    if (!emailRegex.test(value)) return false;
    if (value.includes("..")) return false;
    if (value.startsWith(".") || value.endsWith(".")) return false;
    return true;
}

function showEmailError(msg) {
    emailErr.textContent = msg;
    emailErr.style.display = "block";
}

function clearEmailError() {
    emailErr.textContent = "";
    emailErr.style.display = "none";
}

function validateEmailField(value) {
    if (!value) {
        showEmailError("Укажите email.");
        return false;
    }
    if (value.length > 120) {
        showEmailError("Email не должен превышать 120 символов.");
        return false;
    }
    if (!isEmailValid(value)) {
        showEmailError("Неверный формат email. Пример: name@example.com");
        return false;
    }
    clearEmailError();
    return true;
}

function validateForm({ firstName, lastName, middleName, email, password, password2 }) {
    if (!firstName || firstName.length < 2 || firstName.length > 50) {
        return "Имя должно содержать от 2 до 50 символов.";
    }
    if (!isNameValid(firstName)) {
        return "Имя может содержать только буквы, пробел и дефис.";
    }

    if (!lastName || lastName.length < 2 || lastName.length > 50) {
        return "Фамилия должна содержать от 2 до 50 символов.";
    }
    if (!isNameValid(lastName)) {
        return "Фамилия может содержать только буквы, пробел и дефис.";
    }

    if (middleName) {
        if (middleName.length < 2 || middleName.length > 50) {
            return "Отчество должно содержать от 2 до 50 символов.";
        }
        if (!isNameValid(middleName)) {
            return "Отчество может содержать только буквы, пробел и дефис.";
        }
    }

    if (!email || email.length > 120) {
        return "Укажите корректный email.";
    }
    if (!isEmailValid(email)) {
        return "Неверный формат email.";
    }

    if (!isPasswordStrong(password)) {
        return "Пароль должен быть от 8 символов и содержать букву, цифру и спецсимвол.";
    }

    if (password !== password2) {
        return "Пароли не совпадают.";
    }

    return null;
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const data = {
        firstName: document.getElementById("firstName").value.trim(),
        lastName: document.getElementById("lastName").value.trim(),
        middleName: document.getElementById("middleName").value.trim(),
        email: document.getElementById("email").value.trim(),
        password: document.getElementById("password").value,
        password2: document.getElementById("password2").value
    };

    if (!validateEmailField(data.email)) {
        showError("Проверьте поле электронной почты.");
        return;
    }

    const validationError = validateForm(data);
    if (validationError) {
        showError(validationError);
        return;
    }

    const payload = {
        email: data.email,
        password: data.password,
        firstName: data.firstName,
        lastName: data.lastName,
        middleName: data.middleName
    };

    try {
        const res = await fetch("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showOk("Регистрация успешна. Сейчас перенаправим на вход...");
            setTimeout(() => {
                window.location.href = "/login/index.html";
            }, 900);
            return;
        }

        const text = await res.text();
        if (res.status === 400 || res.status === 409) {
            showError(text || "Пользователь с таким email уже существует.");
        } else {
            showError(text || `Ошибка регистрации (HTTP ${res.status}).`);
        }
    } catch (e2) {
        showError("Сетевая ошибка. Проверьте, что сервер запущен.");
    }
});

emailInput.addEventListener("blur", () => {
    validateEmailField(emailInput.value.trim());
});

emailInput.addEventListener("input", () => {
    const value = emailInput.value.trim();
    if (!value) {
        clearEmailError();
        return;
    }
    validateEmailField(value);
});
