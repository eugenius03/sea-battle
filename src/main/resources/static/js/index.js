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

    const findGameBtn = document.getElementById('findGameBtn');
    if (findGameBtn) {
        findGameBtn.addEventListener('click', async function () {
                findGameBtn.disabled = true;
                findGameBtn.innerText = 'Finding...';

                const response = await fetch('/api/auth/me', {
                    method: 'GET',
                    headers: {'Accept': 'application/json'}
                });

                let userId;
                if (response.ok) {
                    const data = await response.json();
                    document.cookie = `nav_username=${data.username}; path=/; max-age=86400`;
                    userId = data.id;
                }

                if (!userId) {
                    if (typeof showMessage !== 'undefined') showMessage("User ID not found, please log in again.", "error");
                    findGameBtn.disabled = false;
                    findGameBtn.innerText = 'Find Game';
                    return;
                }

                const socket = new SockJS('/ws');
                const stompClient = Stomp.over(socket);

                stompClient.debug = null;

                stompClient.connect({'X-User-Id': userId}, function () {
                    stompClient.subscribe('/topic/matchmaking/' + userId, function (message) {
                        const data = JSON.parse(message.body);
                        if (data.inviteToken) {
                            stompClient.disconnect();

                            globalThis.location.href = `/game/${data.inviteToken}`;
                        } else if (data.queuePosition !== undefined && data.queuePosition !== null) {
                            findGameBtn.innerText = `Finding... (Position: ${data.queuePosition})`;
                        }
                    });

                    fetch('/api/match/find', {
                        method: 'POST',
                        credentials: 'same-origin'
                    }).catch(err => {
                        if (typeof showMessage !== 'undefined') showMessage(`Error: ${err.message}`, 'error');
                        findGameBtn.disabled = false;
                        findGameBtn.innerText = 'Find Game';
                    });
                }, function (error) {
                    if (typeof showMessage !== 'undefined') showMessage(`Connection error: ${error}`, 'error');
                    findGameBtn.disabled = false;
                    findGameBtn.innerText = 'Find Game';
                });
            }
        )
        ;
    }
});