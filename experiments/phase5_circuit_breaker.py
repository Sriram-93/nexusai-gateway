import requests
import time
import subprocess
import json

GATEWAY_URL = "http://localhost:8080/api/chat"

def sabotage_groq():
    print("\n⚠️  [CHAOS MONKEY] Sabotaging Groq Provider's Base URL in PostgreSQL to http://localhost:9999...")
    subprocess.run(
        "PGPASSWORD=postgrespassword psql -U postgres -d nexusai -c \"UPDATE provider_configs SET base_url='http://localhost:9999' WHERE slug='groq';\"",
        shell=True, capture_output=True
    )

def restore_groq():
    print("\n✅ [RECOVERY] Restoring Groq Provider's Base URL...")
    subprocess.run(
        "PGPASSWORD=postgrespassword psql -U postgres -d nexusai -c \"UPDATE provider_configs SET base_url='https://api.groq.com/openai/v1/chat/completions' WHERE slug='groq';\"",
        shell=True, capture_output=True
    )

def run_phase5():
    print("=== Phase 5: Circuit Breaker Chaos Testing ===")
    
    # 1. Baseline Request
    print("\n[Step 1] Sending Baseline Request (Expected: Routed to Groq or Gemini)...")
    payload = {"message": "Hello, how are you?", "tenantId": "enterprise-a"}
    
    start = time.time()
    res = requests.post(GATEWAY_URL, json=payload).json()
    print(f"  Baseline Routed To: {res.get('provider')} (Latency: {time.time()-start:.2f}s)")
    
    # 2. Chaos Injection
    sabotage_groq()
    
    # 3. Request during outage
    print("\n[Step 2] Sending Requests during Groq Outage. Circuit Breaker should detect failure and Fallback!")
    
    for i in range(5):
        start = time.time()
        try:
            res = requests.post(GATEWAY_URL, json=payload).json()
            provider = res.get("provider")
            reason = res.get("routingReason", "")
            
            status = "FALLBACK" if provider != "groq" else "GROQ"
            print(f"  Query {i}: Routed To: {provider} [{status}] (Latency: {time.time()-start:.2f}s)")
            if "Circuit Breaker Fallback" in reason or provider != "groq":
                print(f"    Reason: {reason}")
        except Exception as e:
            print(f"  Query {i} Failed: {e}")
            
    # 4. Recovery
    restore_groq()
    
    print("\n[Step 3] Circuit Breaker Recovery (Groq is back online).")
    for i in range(2):
        start = time.time()
        res = requests.post(GATEWAY_URL, json=payload).json()
        print(f"  Query {i}: Routed To: {res.get('provider')} (Latency: {time.time()-start:.2f}s)")

if __name__ == "__main__":
    try:
        run_phase5()
    except KeyboardInterrupt:
        restore_groq()
