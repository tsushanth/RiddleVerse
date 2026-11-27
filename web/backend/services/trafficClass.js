// Labels where a telemetry request came from, so automated installs can be left out of retention numbers.
// Only the label is stored (puzzle_events.traffic), never the address.
//   'testlab' the app says it runs on Firebase Test Lab, or the address is in Test Lab's published device ranges
//             (testLabIpBlocks.json; Play's pre-launch checks run there)
//   'other'   an address outside those ranges (this does not prove a person)
//   null      no readable address
import { BlockList, isIP } from 'node:net';
import { readFileSync } from 'node:fs';

const { blocks } = JSON.parse(readFileSync(new URL('./testLabIpBlocks.json', import.meta.url), 'utf8'));
const testLab = new BlockList();
for (const block of blocks) {
    const [address, prefix] = block.split('/');
    testLab.addSubnet(address, Number(prefix), isIP(address) === 6 ? 'ipv6' : 'ipv4');
}

/** The player's address. Requests arrive through Cloudflare and Fly, so req.ip alone is a proxy. */
export function clientAddress(req) {
    const raw = req.headers?.['cf-connecting-ip'] || req.headers?.['fly-client-ip'] || req.ip || '';
    return String(raw).trim().replace(/^::ffff:(?=\d+\.)/i, '');
}

export function trafficLabel(address, saysTestLab = false) {
    if (saysTestLab) return 'testlab';
    const family = isIP(address || '');
    if (!family) return null;
    return testLab.check(address, family === 6 ? 'ipv6' : 'ipv4') ? 'testlab' : 'other';
}
