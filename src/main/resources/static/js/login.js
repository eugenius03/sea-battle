document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('loginForm');
    if (!form) return;

    setupFormValidation(form);

    form.addEventListener('submit', async function (e) {
        e.preventDefault();

        const formData = {
            username: document.getElementById('username').value,
            password: document.getElementById('password').value
        };

        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(formData)
        });

        if (response.ok) {
            const redirectInput = document.getElementById('redirectUrl');
            globalThis.location.href = redirectInput ? redirectInput.value : '/game';
        } else {
            await response.text();
            showError('Invalid username or password');
        }

    });
});