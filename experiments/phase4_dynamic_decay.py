import requests
import time
import matplotlib.pyplot as plt
import re
import os

GATEWAY_URL = "http://localhost:8080/api/chat"

def get_alpha_from_reason(reason):
    # Parses the string: "FederatedLinUCB: groq scored 1.5000 (Dynamic α_transfer=0.85)"
    match = re.search(r"α_transfer=([0-9\.]+)", reason)
    if match:
        return float(match.group(1))
    return 1.0

def run_phase4():
    print("=== Phase 4: Dynamic Cold-Start Decay (Federated Transfer) ===")
    
    # 1. Train the Global Model using Tenant A
    print("\n[Step 1] Pre-training Global Model with 'enterprise-a' (5 queries)...")
    for i in range(5):
        payload = {"message": f"Global training query {i} about cloud architecture.", "tenantId": "enterprise-a"}
        try:
            requests.post(GATEWAY_URL, json=payload, timeout=30)
            print(f"  Global training {i}/5 complete")
        except Exception as e:
            print(f"  Global training error: {e}")
            
    # 2. Introduce a Cold-Start Tenant B
    print("\n[Step 2] Introducing Cold-Start tenant 'research-c' (10 queries)...")
    alphas = []
    
    for i in range(10):
        payload = {"message": f"Cold start query {i} evaluating zero-shot performance.", "tenantId": "research-c"}
        try:
            start_time = time.time()
            response = requests.post(GATEWAY_URL, json=payload, timeout=30)
            latency = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                reason = data.get("routingReason", "")
                alpha = get_alpha_from_reason(reason)
                alphas.append(alpha)
                
                print(f"  Query {i}: α_transfer = {alpha:.2f} (Latency: {latency:.2f}s)")
            else:
                print(f"  Query {i} failed")
        except Exception as e:
            print(f"  Query {i} exception: {e}")

    # Plotting
    if alphas:
        plt.figure(figsize=(10, 5))
        plt.plot(range(len(alphas)), alphas, marker='o', linestyle='-', color='b')
        plt.title('Dynamic Federated Transfer Decay (Phase 4)')
        plt.xlabel('Number of Queries (Cold-Start Tenant)')
        plt.ylabel('α_transfer (Reliance on Global Policy)')
        plt.ylim(-0.1, 1.1)
        plt.grid(True)
        plt.tight_layout()
        
        plt.savefig('phase4_decay.png')
        print("\nSaved proof graph to 'phase4_decay.png'")

if __name__ == "__main__":
    run_phase4()
