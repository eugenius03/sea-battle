function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
}

function setAuthUI(usernameDisplay, userSection, guestSection, brandLink, homeLink, username) {
    if (usernameDisplay) usernameDisplay.textContent = username;
    if (userSection) userSection.style.display = 'flex';
    if (guestSection) guestSection.style.display = 'none';
    if (brandLink) brandLink.href = '/game';
    if (homeLink) homeLink.style.display = 'block';
}

function setGuestUI(userSection, guestSection, brandLink, homeLink) {
    if (userSection) userSection.style.display = 'none';
    if (guestSection) guestSection.style.display = 'flex';
    if (brandLink) brandLink.href = '/login';
    if (homeLink) homeLink.style.display = 'none';
}

document.addEventListener('DOMContentLoaded', async () => {
    const guestSection = document.getElementById('navGuestSection');
    const userSection = document.getElementById('navUserSection');
    const usernameDisplay = document.getElementById('navUsernameDisplay');
    const logoutBtn = document.getElementById('navLogoutBtn');
    const brandLink = document.getElementById('navBrandLink');
    const homeLink = document.getElementById('navHomeLink');

    const cachedUsername = getCookie('nav_username');

    if (cachedUsername) {
        setAuthUI(usernameDisplay, userSection, guestSection, brandLink, homeLink, cachedUsername);
    } else {
        try {
            const response = await fetch('/api/auth/me', {
                method: 'GET',
                headers: {'Accept': 'application/json'}
            });

            if (response.ok) {
                const data = await response.json();
                document.cookie = `nav_username=${data.username}; path=/; max-age=86400`;
                setAuthUI(usernameDisplay, userSection, guestSection, brandLink, homeLink, data.username);
            } else {
                setGuestUI(userSection, guestSection, brandLink, homeLink);
            }
        } catch (error) {
            console.error("Failed to fetch auth status:", error);
            setGuestUI(userSection, guestSection, brandLink, homeLink);
        }
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            document.cookie = "nav_username=; path=/; max-age=0";
            await fetch('/api/auth/logout', {method: 'POST'});
            globalThis.location.href = '/login';
        });
    }
});