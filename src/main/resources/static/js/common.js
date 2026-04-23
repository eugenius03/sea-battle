function showMessage(message, type, duration = 3000) {
    const messageArea = document.getElementById('messageArea');
    if (!messageArea) return;

    messageArea.innerHTML = `<div class="message ${type}">${message}</div>`;
    setTimeout(() => {
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