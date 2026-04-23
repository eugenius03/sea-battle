document.addEventListener('DOMContentLoaded', function () {
    const form = document.querySelector('form');
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
});