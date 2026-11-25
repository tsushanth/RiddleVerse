/** A value as a quoted JavaScript string for use inside an inline <script>: it cannot end the string or the element. */
export const jsLiteral = (value) => JSON.stringify(String(value ?? ''))
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
