/* global inviteToken */

document.addEventListener('DOMContentLoaded', function () {
    const guestForm = document.getElementById('guestForm');
    if (guestForm) {
        guestForm.addEventListener('submit', async function (e) {
            e.preventDefault();

            const username = document.getElementById('guestUsername').value.trim();
            const errorDiv = document.getElementById('guestError');

            if (username.length < 3) {
                errorDiv.textContent = 'Username must be at least 3 characters';
                errorDiv.style.display = 'block';
                return;
            }

            try {
                const response = await fetch(`/api/match/join?inviteToken=${inviteToken}&username=${encodeURIComponent(username)}`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'}
                });

                if (response.ok) {
                    const data = await response.json();
                    globalThis.location.href = `/game/${inviteToken}/${data.playerId}`;
                } else {
                    const error = await response.text();
                    errorDiv.textContent = error || 'Failed to join game';
                    errorDiv.style.display = 'block';
                }
            } catch (error) {
                errorDiv.textContent = 'An error occurred. Please try again.';
                errorDiv.style.display = 'block';
            }
        });
    }

    const guestUsername = document.getElementById('guestUsername');
    if (guestUsername) {
        guestUsername.addEventListener('input', function () {
            const errorDiv = document.getElementById('guestError');
            if (errorDiv) errorDiv.style.display = 'none';
        });
    }
});

async function joinMatchWithUsername(username) {
    try {
        const response = await fetch(`/api/match/join?inviteToken=${inviteToken}&username=${encodeURIComponent(username)}`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
        });

        if (response.ok) {
            const data = await response.json();
            globalThis.location.href = `/game/${inviteToken}/${data.playerId}`;
        } else {
            const error = await response.text();
            showJoinError(`Failed to join: ${error}`);
        }
    } catch (error) {
        showJoinError('An error occurred. Please try again.');
    }
}

function showJoinError(message) {
    document.querySelector('.container').innerHTML = `
        <h1>🚢 Join Sea Battle</h1>
        <div style="text-align: center; padding: 40px;">
            <div style="background-color: #ffebee; color: #c62828; padding: 20px; border-radius: 10px; margin-bottom: 20px;">
                ${message}
            </div>
            <button onclick="location.reload()" style="padding: 12px 30px; background-color: #667eea; color: white; border: none; border-radius: 5px; cursor: pointer;">
                Try Again
            </button>
        </div>
    `;
}