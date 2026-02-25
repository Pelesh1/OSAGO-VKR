function logout() {
    sessionStorage.removeItem("auth");
    window.location.href = "/";
}

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

async function loadMe(token) {
    const res = await fetch("/api/me", { headers: { Authorization: "Basic " + token } });
    if (!res.ok) return null;
    return await res.json();
}

function isoDate(d) {
    return d.toISOString().slice(0, 10);
}

function defaultDates() {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - 14);
    return { from: isoDate(from), to: isoDate(to) };
}

function readFilters() {
    return {
        type: document.getElementById("type").value,
        from: document.getElementById("from").value,
        to: document.getElementById("to").value
    };
}

function formatMoney(v) {
    const n = Number(v);
    if (Number.isNaN(n)) return "0 ₽";
    return `${n.toLocaleString("ru-RU", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₽`;
}

function formatDateRu(v) {
    if (!v) return "—";
    const d = new Date(String(v).replace(" ", "T"));
    if (Number.isNaN(d.getTime())) return v;
    return d.toLocaleDateString("ru-RU");
}

function statusLabel(code, type) {
    if (!code) return "—";
    const c = String(code).toUpperCase();
    if (type === "CLAIMS") {
        if (c === "NEW") return "Новая";
        if (c === "IN_REVIEW") return "На рассмотрении";
        if (c === "NEED_INFO") return "Нужны документы";
        if (c === "APPROVED") return "Одобрена";
        if (c === "REJECTED") return "Отклонена";
        if (c === "CLOSED") return "Закрыта";
        return code;
    }
    if (c === "NEW") return "Новая";
    if (c === "IN_REVIEW") return "На рассмотрении";
    if (c === "NEED_INFO") return "Нужны данные";
    if (c === "APPROVED") return "Одобрена";
    if (c === "PAYMENT_PENDING") return "Ожидает оплату";
    if (c === "PAID") return "Оплачена";
    if (c === "REJECTED") return "Отклонена";
    return code;
}

async function loadReport(token, filters) {
    const params = new URLSearchParams();
    params.set("type", filters.type);
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);

    const res = await fetch(`/api/agent/reports?${params.toString()}`, {
        headers: { Authorization: "Basic " + token }
    });
    const txt = await res.text();
    let data = {};
    try { data = txt ? JSON.parse(txt) : {}; } catch {}
    if (!res.ok) throw new Error(data.message || txt || "Не удалось загрузить отчет");
    return data;
}

function rowsToMap(row) {
    const m = {};
    for (const c of row.cells || []) m[c.label] = c.value;
    return m;
}

function metricsToMap(metrics) {
    const m = {};
    for (const it of metrics || []) m[it.label] = it.value;
    return m;
}

function buildPolicySummaryRows(rows, periodText) {
    const mapped = rows.map(rowsToMap);
    const total = mapped.length;
    const osago = mapped.filter((x) => (x.policy_type || "OSAGO") === "OSAGO");
    const kasko = mapped.filter((x) => (x.policy_type || "") === "KASKO");
    const sumTotal = mapped.reduce((acc, x) => acc + (Number(x.premium_amount || 0) || 0), 0);
    const sumOsago = osago.reduce((acc, x) => acc + (Number(x.premium_amount || 0) || 0), 0);
    const sumKasko = kasko.reduce((acc, x) => acc + (Number(x.premium_amount || 0) || 0), 0);
    const avg = total > 0 ? (sumTotal / total) : 0;

    return {
        tableRows: [
            { label: "Всего оформлено полисов", count: total, sum: sumTotal, period: periodText },
            { label: "ОСАГО", count: osago.length, sum: sumOsago, period: periodText },
            { label: "КАСКО", count: kasko.length, sum: sumKasko, period: periodText },
            { label: "Средняя стоимость полиса", count: total, sum: avg, period: periodText }
        ],
        pie: [
            { name: "ОСАГО", value: osago.length, color: "#3b82f6" },
            { name: "КАСКО", value: kasko.length, color: "#10b981" }
        ],
        totalRecords: total,
        totalAmount: sumTotal
    };
}

function buildClaimSummaryRows(rows, metrics, periodText) {
    const mapped = rows.map(rowsToMap);
    const mm = metricsToMap(metrics);
    const total = Number(mm.total_claims || mapped.length || 0);
    const approvedCount = Number(mm.approved_claims || 0);
    const rejectedCount = Number(mm.rejected_claims || 0);
    const inReviewCount = Math.max(0, total - approvedCount - rejectedCount);
    const approvedSum = Number(mm.approved_amount || 0);

    return {
        tableRows: [
            { label: "Всего страховых случаев", count: total, sum: 0, period: periodText },
            { label: "Одобрено", count: approvedCount, sum: approvedSum, period: periodText },
            { label: "Отклонено", count: rejectedCount, sum: 0, period: periodText },
            { label: "В работе", count: inReviewCount, sum: 0, period: periodText }
        ],
        pie: [
            { name: "Одобрено", value: approvedCount, color: "#10b981" },
            { name: "Отклонено", value: rejectedCount, color: "#ef4444" },
            { name: "В работе", value: inReviewCount, color: "#3b82f6" }
        ],
        totalRecords: total,
        totalAmount: approvedSum
    };
}

function renderSummaryTable(summary) {
    const body = document.getElementById("summaryBody");
    body.innerHTML = summary.tableRows.map((r) => `
        <tr>
            <td>${r.label}</td>
            <td>${r.count}</td>
            <td>${formatMoney(r.sum)}</td>
            <td>${r.period}</td>
        </tr>
    `).join("");
}

function renderPie(pieData) {
    const total = pieData.reduce((a, x) => a + (x.value || 0), 0);
    const pie = document.getElementById("pie");
    const legend = document.getElementById("pieLegend");

    if (total <= 0) {
        pie.style.background = "#e5e7eb";
        legend.innerHTML = "<div class='legend-item'>Нет данных для графика</div>";
        return;
    }

    let current = 0;
    const parts = [];
    for (const item of pieData) {
        const p = (item.value / total) * 100;
        const from = current;
        const to = current + p;
        parts.push(`${item.color} ${from}% ${to}%`);
        current = to;
    }
    pie.style.background = `conic-gradient(${parts.join(",")})`;

    legend.innerHTML = pieData
        .filter((x) => x.value > 0)
        .map((x) => {
            const p = Math.round((x.value / total) * 100);
            return `
                <div class="legend-item">
                    <span class="legend-dot" style="background:${x.color}"></span>
                    <span>${x.name}: ${p}%</span>
                </div>
            `;
        }).join("");
}

function renderCards(summary, from, to) {
    document.getElementById("cardTotal").textContent = String(summary.totalRecords);
    document.getElementById("cardAmount").textContent = formatMoney(summary.totalAmount);

    const d1 = new Date(from + "T00:00:00");
    const d2 = new Date(to + "T00:00:00");
    const days = Math.max(1, Math.floor((d2 - d1) / 86400000) + 1);
    document.getElementById("cardDays").textContent = `${days} дней`;
}

async function exportCsv(token, filters) {
    const params = new URLSearchParams();
    params.set("type", filters.type);
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);

    const res = await fetch(`/api/agent/reports/export.csv?${params.toString()}`, {
        headers: { Authorization: "Basic " + token }
    });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || "Не удалось скачать CSV");
    }

    const blob = await res.blob();
    const cd = res.headers.get("Content-Disposition") || "";
    const m = /filename="([^"]+)"/i.exec(cd);
    const fileName = m ? m[1] : "agent_report.csv";

    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

function exportPdf() {
    window.print();
}

document.addEventListener("DOMContentLoaded", async () => {
    const token = sessionStorage.getItem("auth");
    if (!token) {
        window.location.href = "/login/index.html?next=" + encodeURIComponent("/cabinet/agent/reports/index.html");
        return;
    }

    const me = await loadMe(token);
    if (!me || me.status !== "AGENT") {
        window.location.href = "/";
        return;
    }

    const d = defaultDates();
    document.getElementById("from").value = d.from;
    document.getElementById("to").value = d.to;

    document.getElementById("backBtn").addEventListener("click", () => {
        window.location.href = "/cabinet/agent/index.html";
    });

    async function refresh() {
        hideError();
        try {
            const f = readFilters();
            const data = await loadReport(token, f);

            const typeText = f.type === "CLAIMS" ? "По страховым случаям" : "По оформленным полисам";
            document.getElementById("reportTitle").textContent = typeText;
            const periodText = `${formatDateRu(f.from)} - ${formatDateRu(f.to)}`;
            document.getElementById("periodText").textContent = `Период: ${periodText}`;

            const summary = f.type === "CLAIMS"
                ? buildClaimSummaryRows(data.rows || [], data.metrics || [], periodText)
                : buildPolicySummaryRows(data.rows || [], periodText);

            renderSummaryTable(summary);
            renderPie(summary.pie);
            renderCards(summary, f.from, f.to);
        } catch (e) {
            showError(e.message);
        }
    }

    document.getElementById("buildBtn").addEventListener("click", refresh);
    document.getElementById("type").addEventListener("change", refresh);

    document.getElementById("csvBtn").addEventListener("click", async () => {
        hideError();
        try {
            await exportCsv(token, readFilters());
        } catch (e) {
            showError(e.message);
        }
    });

    document.getElementById("pdfBtn").addEventListener("click", exportPdf);

    await refresh();
});
