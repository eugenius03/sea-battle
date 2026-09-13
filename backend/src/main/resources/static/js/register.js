document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('registerForm');
    if (!form) return;

    setupFormValidation(form);

    const passwordInput = document.getElementById('password');
    if (passwordInput) {
        passwordInput.addEventListener('input', function () {
            const requirements = this.nextElementSibling;
            if (requirements?.classList.contains('password-requirements')) {
                requirements.classList.toggle('valid', this.value.length >= 6);
            }
        });
    }

    form.addEventListener('submit', async function (e) {
        e.preventDefault();

        const username = document.getElementById('username').value.trim();
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;

        try {
            const response = await fetch('/api/auth/register', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({username, email, password})
            });

            if (response.ok) {
                showMessage('Registration successful!', 'success', 1500);
                setTimeout(() => {
                    globalThis.location.href = '/game';
                }, 2000);
            } else {
                const text = await response.text();
                let message;
                try {
                    message = JSON.parse(text)?.message || text;
                } catch {
                    message = text;
                }
                showMessage(message || 'Registration failed', 'error');
            }
        } catch (err) {
            showMessage('Network error: ' + err.message, 'error');
        }
    });
});