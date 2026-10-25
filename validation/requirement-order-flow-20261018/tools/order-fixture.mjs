// Replay fixture equivalent to the local HTTP contract exercised by the Java acceptance harness.
// Synthetic local service, not a production backend and not an AI model.
import http from 'node:http';

const portIndex = process.argv.indexOf('--port');
const port = portIndex < 0 ? 18880 : Number(process.argv[portIndex + 1]);
const faulty = process.argv.includes('--faulty-zero-status');
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid --port');

http.createServer((req, res) => {
  let raw = '';
  req.on('data', chunk => { raw += chunk; });
  req.on('end', () => {
    let status;
    let body;
    try {
      const input = JSON.parse(raw || '{}');
      if (req.url !== '/demo/orders') { status = 404; body = {code: 'NOT_FOUND'}; }
      else if (req.method !== 'POST') { status = 405; body = {code: 'METHOD_NOT_ALLOWED'}; }
      else if (input.sku == null || String(input.sku).trim() === '') { status = 400; body = {code: 'SKU_REQUIRED'}; }
      else if (input.quantity == null || Number(input.quantity) <= 0) {
        status = faulty ? 200 : 400;
        body = {code: 'INVALID_QUANTITY'};
      } else { status = 201; body = {code: 'OK', orderId: 'ORDER-DEMO-001'}; }
    } catch { status = 400; body = {code: 'INVALID_JSON'}; }
    console.log(JSON.stringify({method: req.method, path: req.url, request: raw, status, faulty}));
    res.writeHead(status, {'Content-Type': 'application/json; charset=utf-8'});
    res.end(JSON.stringify(body));
  });
}).listen(port, '127.0.0.1', () => {
  console.log(`Local synthetic order service: http://127.0.0.1:${port}/demo/orders ; faulty=${faulty}`);
});
