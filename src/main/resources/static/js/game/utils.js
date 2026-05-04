export function $(id) {
    return document.getElementById(id);
}

export function showMessage(message, type, duration = 3000) {
    if (typeof window.showMessage === 'function') window.showMessage(message, type, duration);
}

export function safeJson(s) {
    try {
        return JSON.parse(s);
    } catch {
        return null;
    }
}

export async function readShipId(response) {
    try {
        const trimmed = (await response.text())?.trim();
        if (!trimmed) return null;
        try {
            const parsed = JSON.parse(trimmed);
            return String('shipId' in Object(parsed) ? parsed.shipId : parsed);
        } catch {
            return trimmed;
        }
    } catch {
        return null;
    }
}
