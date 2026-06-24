import urllib.request
import json
import time

url = "http://localhost:8080/api/chat"
headers = {"Content-Type": "application/json"}

# 5 High-complexity coding prompts to trigger Gemini path
prompt = "write a python function to compute fibonacci numbers"

print("==================================================")
print("STARTING CIRCUIT BREAKER SIMULATION TEST")
print("==================================================")

for i in range(1, 6):
    data = json.dumps({
        "message": f"{prompt} - test {i}",
        "priority": "HIGH"
    }).encode("utf-8")
    
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode("utf-8")
            res_json = json.loads(res_body)
            latency = int((time.time() - start_time) * 1000)
            print(f"Request {i}:")
            print(f"  - Provider: {res_json.get('provider')}")
            print(f"  - Latency: {res_json.get('latencyMs')} ms (Actual client time: {latency} ms)")
            print(f"  - Answer snippet: {res_json.get('answer')[:60]}...")
    except Exception as e:
        print(f"Request {i} failed: {e}")
    print("-" * 50)
    time.sleep(1)
