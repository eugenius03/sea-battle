function extractErrorMessage(text) {
    if (text == null) return '';
    const str = String(text);
    const match = new RegExp(/\{[\s\S]*}/).exec(str);
    if (!match) return str;
    const obj = JSON.parse(match[0]);
    if (obj && typeof obj.message === 'string' && obj.message) {
        return str.slice(0, match.index) + obj.message;
    }
    return str;
}

function escapeHtml(str) {
    return String(str).replaceAll(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

function showMessage(message, type, duration = 3000) {
    const messageArea = document.getElementById('messageArea');
    if (!messageArea) return;

    const clean = extractErrorMessage(message).trim();
    messageArea.innerHTML = `<div class="message ${type}">${escapeHtml(clean)}</div>`;
    clearTimeout(showMessage._t);
    showMessage._t = setTimeout(() => {
        messageArea.innerHTML = '';
    }, duration);
}

function showError(message) {
    const existingAlert = document.querySelector('.alert');
    if (existingAlert) existingAlert.remove();

    const alert = document.createElement('div');
    alert.className = 'alert alert-error';
    alert.textContent = message;

    const form = document.querySelector('form');
    if (form) form.parentNode.insertBefore(alert, form);

    setTimeout(() => alert.remove(), 5000);
}

function setupFormValidation(form) {
    if (!form) return;

    form.querySelectorAll('input[required]').forEach(input => {
        input.addEventListener('blur', function () {
            this.classList.toggle('error', this.value.trim() === '');
        });
        input.addEventListener('input', function () {
            if (this.value.trim() !== '') this.classList.remove('error');
        });
    });
}

async function copyToClipboard(text) {
    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(text);
            return true;
        }
        console.warn('Clipboard API not available (possibly not in a secure context)');
        return false;
    } catch (e) {
        console.error('Clipboard copy failed:', e);
        return false;
    }
}