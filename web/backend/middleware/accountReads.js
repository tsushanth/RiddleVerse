// Reads about one account are answered for that account's own verified sign-in.
import { callerIsUid, callerOwnsEmail, verifiedCaller } from './callerIdentity.js';

const PROOFS = { uid: callerIsUid, email: callerOwnsEmail };

/** True when the request is signed in as the account named by `value` (`kind`: 'uid' or 'email'). */
export const isOwnAccount = (req, kind, value) => PROOFS[kind](req, value);

/** Refuses other requests. A request that names no account is left to the handler. */
export const ownAccountOnly = (param, kind) => async (req, res, next) => {
    const value = req.query?.[param];
    if (value === undefined || await isOwnAccount(req, kind, value)) return next();
    const status = await verifiedCaller(req) ? 403 : 401;
    return res.status(status).json({ success: false, error: status === 401 ? 'Unauthorized' : 'Forbidden' });
};

/** Answers `fallback` to other requests, without looking the account up. */
export const ownAccountOr = (param, kind, fallback) => async (req, res, next) => {
    const value = req.query?.[param];
    if (!value || await isOwnAccount(req, kind, value)) return next();
    return res.json(fallback(req));
};
