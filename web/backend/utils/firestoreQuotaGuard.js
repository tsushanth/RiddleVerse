// Detects Firestore quota exhaustion (gRPC code 8 / RESOURCE_EXHAUSTED) on every SDK call, including
// ones whose caller swallows the error (the streak job burned the free quota unseen). Wraps the
// promise-returning methods on the Firestore classes, observes the rejection, rethrows unchanged.
import { isFirestoreQuotaError, reportFirestoreQuota } from './failureReporter.js';

const METHODS = {
  DocumentReference: ['get', 'set', 'update', 'delete', 'create'],
  Query: ['get'],
  CollectionReference: ['add'],
  WriteBatch: ['commit'],
  Firestore: ['getAll', 'runTransaction', 'listCollections'],
};

/** classes: { DocumentReference, Query, ... } constructors; onQuota(flow) is called per quota rejection. */
export function installFirestoreQuotaGuard(classes, onQuota = reportFirestoreQuota) {
  let wrapped = 0;
  for (const [cname, methods] of Object.entries(METHODS)) {
    const proto = classes?.[cname]?.prototype;
    if (!proto) continue;
    for (const m of methods) {
      const orig = Object.getOwnPropertyDescriptor(proto, m)?.value;
      if (typeof orig !== 'function' || orig.__quotaGuard) continue;
      const fn = function (...args) {
        const out = orig.apply(this, args);
        if (out && typeof out.then === 'function') {
          out.then(undefined, (e) => { if (isFirestoreQuotaError(e)) { try { onQuota(`${cname}.${m}`); } catch { /* never throw */ } } });
        }
        return out;
      };
      fn.__quotaGuard = true;
      Object.defineProperty(proto, m, { value: fn, writable: true, configurable: true });
      wrapped++;
    }
  }
  return wrapped;
}
