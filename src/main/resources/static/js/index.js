document.addEventListener('DOMContentLoaded', function () {
    const createMatchForm = document.getElementById('createMatchForm');
    if (createMatchForm) {
        createMatchForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            try {
                const response = await fetch('/api/match/create', {
                    method: 'POST',
                    credentials: 'same-origin'
                });

                if (response.ok) {
                    const data = await response.json();
                    globalThis.location.href = `/game/${data.inviteToken}`;
                } else {
                    showMessage(`Failed to create match: ${await response.text()}`, 'error');
                }
            } catch (err) {
                showMessage(`Error: ${err.message}`, 'error');
            }
        });
    }

    const joinForm = document.getElementById('joinForm');
    if (joinForm) {
        joinForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const inviteToken = document.getElementById('inviteToken').value.trim();

            if (!inviteToken) return;

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