// Thin Telegram Bot API client. The HTTP layer (fetchImpl) is injectable, so tests never touch the network.

export class TelegramError extends Error {
    constructor(method, status, body) {
        super(`telegram ${method} failed: ${body?.description || status}`);
        this.name = 'TelegramError';
        this.method = method;
        this.status = status;
        this.errorCode = body?.error_code || status;
        this.description = body?.description || '';
        this.retryAfter = body?.parameters?.retry_after || null;
    }
    /** The user blocked the bot, deleted the chat, or the bot was removed from the group. */
    get isGone() {
        return this.errorCode === 403 || /chat not found|bot was kicked|user is deactivated/i.test(this.description);
    }
}

export const escapeHtml = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export function createTelegramApi({ token, fetchImpl = globalThis.fetch, timeoutMs = 8000, base = 'https://api.telegram.org' } = {}) {
    if (!token) throw new Error('telegram token missing');
    async function call(method, payload = {}) {
        let res;
        try {
            res = await fetchImpl(`${base}/bot${token}/${method}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                signal: AbortSignal.timeout(timeoutMs),
            });
        } catch (e) {
            // Never leak the token through an error message.
            throw new Error(`telegram ${method} network error: ${String(e?.name || 'error')}`);
        }
        let body = null;
        try { body = await res.json(); } catch { /* non-JSON error page */ }
        if (!res.ok || !body?.ok) throw new TelegramError(method, res.status, body);
        return body.result;
    }
    return {
        call,
        sendMessage: (chatId, text, extra = {}) => call('sendMessage', {
            chat_id: chatId, text, parse_mode: 'HTML', link_preview_options: { is_disabled: true }, ...extra,
        }),
        editMessageText: (chatId, messageId, text, extra = {}) => call('editMessageText', {
            chat_id: chatId, message_id: messageId, text, parse_mode: 'HTML', link_preview_options: { is_disabled: true }, ...extra,
        }),
        answerCallbackQuery: (id, text) => call('answerCallbackQuery', { callback_query_id: id, ...(text ? { text } : {}) }),
        getMe: () => call('getMe', {}),
    };
}
