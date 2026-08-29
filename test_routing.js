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

async function testSimulation(scenario, prompt, weights) {
  console.log(`\n--- Scenario: ${scenario} ---`);
  console.log(`Weights: Q:${weights.qualityWeight} C:${weights.costWeight} L:${weights.latencyWeight} R:${weights.reliabilityWeight}`);
  
  const payload = {
    prompt: prompt,
    taskCategory: "coding",
    ...weights
  };

  try {
    const res = await request('POST', '/api/routing/simulate', payload);
    if (res.status === 200) {
      console.log(`Selected Model: ${res.body.selectedModelDisplayName} (${res.body.selectedArmKey})`);
      console.log(`Reason: ${res.body.explanationReason}`);
      const winner = res.body.candidates.find(c => c.isWinner);
      console.log(`Winner Scores: Final: ${winner.finalScore.toFixed(3)} | Q: ${winner.qualityScore.toFixed(2)} C: ${winner.costScore.toFixed(2)} L: ${winner.latencyScore.toFixed(2)} R: ${winner.reliabilityScore.toFixed(2)}`);
      console.log(`Estimated Cost: $${winner.estimatedCostUsd.toFixed(5)} | Estimated Latency: ${winner.estimatedLatencyMs}ms`);
    } else {
      console.error(`Error: ${res.status}`, res.body);
    }
  } catch (err) {
    console.error(`Request failed: ${err.message}`);
  }
}

async function setAlpha(alpha) {
  console.log(`\n=== Setting LinUCB Alpha to ${alpha} ===`);
  try {
    const res = await request('PATCH', '/api/dashboard/settings/bandit', { alpha });
    if (res.status === 200) {
      console.log(`Alpha updated to: ${res.body.alpha}`);
    } else {
      console.error(`Failed to set alpha: ${res.status}`, res.body);
    }
  } catch (err) {
    console.error(`Request failed: ${err.message}`);
  }
}

async function runTests() {
  console.log("Checking backend connection...");
  const metrics = await request('GET', '/api/dashboard/metrics');
  if (metrics.status !== 200) {
    console.error("Backend not running or unreachable:", metrics.status, metrics.body);
    return;
  }
  console.log("Backend verified! Active Strategy:", metrics.body.activeStrategy);

  // Test Policy Filter Scenarios
  console.log("\n=============================================");
  console.log("TESTING POLICY FILTER SCENARIOS");
  console.log("=============================================");
  
  await testSimulation("High Quality (Complex coding task)", "Write a complex Python script", {
    qualityWeight: 0.8, costWeight: 0.05, latencyWeight: 0.05, reliabilityWeight: 0.1
  });

  await testSimulation("Low Cost (Batch processing / summarization)", "Summarize this long text", {
    qualityWeight: 0.1, costWeight: 0.7, latencyWeight: 0.1, reliabilityWeight: 0.1
  });

  await testSimulation("Low Latency (Real-time chat completion)", "Say hello quickly", {
    qualityWeight: 0.1, costWeight: 0.1, latencyWeight: 0.7, reliabilityWeight: 0.1
  });

  // Test LinUCB Hyperparameters
  console.log("\n=============================================");
  console.log("TESTING LinUCB EXPLORATION RANGES");
  console.log("=============================================");
  
  await setAlpha(0.1); // Pure exploitation
  await testSimulation("Alpha = 0.1 (Exploitation Focus)", "Test prompt", {
    qualityWeight: 0.25, costWeight: 0.25, latencyWeight: 0.25, reliabilityWeight: 0.25
  });

  await setAlpha(0.5); // Balanced exploration
  await testSimulation("Alpha = 0.5 (Balanced)", "Test prompt", {
    qualityWeight: 0.25, costWeight: 0.25, latencyWeight: 0.25, reliabilityWeight: 0.25
  });

  await setAlpha(1.5); // High exploration
  await testSimulation("Alpha = 1.5 (High Exploration)", "Test prompt", {
    qualityWeight: 0.25, costWeight: 0.25, latencyWeight: 0.25, reliabilityWeight: 0.25
  });
  
  await setAlpha(3.0); // Extreme exploration
  await testSimulation("Alpha = 3.0 (Extreme Exploration)", "Test prompt", {
    qualityWeight: 0.25, costWeight: 0.25, latencyWeight: 0.25, reliabilityWeight: 0.25
  });
}

runTests();
