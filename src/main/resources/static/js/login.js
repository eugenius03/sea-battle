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

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                globalThis.location.href = '/game';
            } else {
                await response.text();
                showError('Invalid username or password');
            }
        } catch (error) {
            showError('An error occurred. Please try again.');
        }
    });
});