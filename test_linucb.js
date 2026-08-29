const http = require('http');

const PORT = 8080;

function request(method, path, body = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: PORT,
      path: path,
      method: method,
      headers: {
        'Content-Type': 'application/json',
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => {
        data += chunk;
      });
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data || '{}') });
        } catch (e) {
          resolve({ status: res.statusCode, body: data });
        }
      });
    });

    req.on('error', (e) => reject(e));

    if (body) {
      req.write(JSON.stringify(body));
    }
    req.end();
  });
}

async function setAlpha(alpha) {
  const res = await request('PATCH', '/api/dashboard/settings/bandit', { alpha });
  if (res.status === 200) {
    console.log(`\n>>> Set LinUCB Alpha to: ${res.body.alpha}`);
  } else {
    console.error(`Failed to set alpha: ${res.status}`, res.body);
  }
}

async function sendChatRequest(message) {
  const res = await request('POST', '/api/chat', { message, priority: "HIGH" });
  if (res.status === 200) {
    console.log(`[Chat] Answered by: ${res.body.provider}`);
    console.log(`       Engine: ${res.body.activeEngine}`);
    console.log(`       Reason: ${res.body.routingReason}`);
    return res.body;
  } else {
    console.error(`[Chat] Failed: ${res.status}`, res.body);
    return null;
  }
}

async function runTests() {
  // Ensure FEDERATED strategy
  await request('PATCH', '/api/dashboard/settings/routing', { strategy: 'FEDERATED' });

  const alphas = [0.1, 0.8, 1.5, 3.0];
  
  for (const alpha of alphas) {
    await setAlpha(alpha);
    
    // We'll send 3 chat requests per alpha to see if it explores or exploits
    console.log(`Testing with Alpha = ${alpha}...`);
    for (let i = 1; i <= 3; i++) {
      await sendChatRequest(`Task ${i} under alpha ${alpha}`);
      // wait a bit
      await new Promise(r => setTimeout(r, 200));
    }
  }
}

runTests();
