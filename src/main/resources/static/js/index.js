document.addEventListener('DOMContentLoaded', function () {
    const createMatchForm = document.getElementById('createMatchForm');
    if (createMatchForm) {
        createMatchForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/game';
            document.body.appendChild(form);
            form.submit();
        });
    }

    const joinForm = document.getElementById('joinForm');
    if (joinForm) {
        joinForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const inviteToken = document.getElementById('inviteToken').value;

            if (!isLoggedIn) {
                const usernameInput = document.getElementById('guestUsername');
                const errorDiv = document.getElementById('usernameError');
                const username = usernameInput ? usernameInput.value.trim() : '';

                if (username.length < 3) {
                    if (errorDiv) {
                        errorDiv.textContent = 'Username must be at least 3 characters';
                        errorDiv.style.display = 'block';
                    }
                    return;
                }

                if (errorDiv) errorDiv.style.display = 'none';
            }

            globalThis.location.href = `/game/join/${inviteToken}`;
        });
    }

    const guestUsername = document.getElementById('guestUsername');
    if (guestUsername) {
        guestUsername.addEventListener('input', function () {
            const errorDiv = document.getElementById('usernameError');
            if (errorDiv) errorDiv.style.display = 'none';
        });
    }
});
