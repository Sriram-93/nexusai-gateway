import requests
import time

GATEWAY_URL = "http://localhost:8080/api/chat"
PROVISION_URL = "http://localhost:8080/api/tenant/provision"

def run_saas_flow():
    print("=== Phase 6: B2B SaaS Product Flow (Provisioning, Auth, Rate Limiting) ===")
    
    # 1. Provision a New Tenant
    print("\n[Step 1] Customer signs up: Provisioning a new Tenant...")
    new_tenant = {
        "tenantId": "customer-xyz",
        "tenantName": "XYZ Corp",
        "dailyBudgetUsd": 5.0,
        "maxRequestsPerMinute": 3,  # Strict limit for testing
        "piiEnforcementEnabled": True,
        "jailbreakEnforcementEnabled": True
    }
    
    res = requests.post(PROVISION_URL, json=new_tenant)
    if res.status_code != 200:
        print(f"Failed to provision: {res.text}")
        return
        
    provisioned = res.json()
    api_key = provisioned.get("apiKey")
    print(f"  ✅ Provisioned successfully! API Key generated: {api_key}")
    
    # 2. Authenticated Chat Request
    print("\n[Step 2] Customer sends a chat request using ONLY their API Key (no tenantId)...")
    headers = {"X-API-Key": api_key, "Content-Type": "application/json"}
    payload = {"message": "Hello NexusAI, are you working securely?"}
    
    res = requests.post(GATEWAY_URL, json=payload, headers=headers)
    if res.status_code == 200:
        print(f"  ✅ Success! Routed to: {res.json().get('provider')}")
    else:
        print(f"  ❌ Failed: {res.status_code} - {res.text}")

    # 3. Rate Limit Trigger
    print("\n[Step 3] Simulating traffic spike (exceeding 3 req/min)...")
    for i in range(1, 5):
        print(f"  Sending request {i}...")
        res = requests.post(GATEWAY_URL, json=payload, headers=headers)
        if res.status_code == 429:
            print("  🛑 SUCCESS: Gateway blocked request with 429 Too Many Requests! Redis Rate Limiter works.")
        elif res.status_code == 200:
            print("  ✅ Allowed.")
        else:
            print(f"  Unexpected status: {res.status_code}")
            
    print("\nEnd of SaaS Flow Demonstration.")

if __name__ == "__main__":
    run_saas_flow()
