import requests
import json
import matplotlib.pyplot as plt
import random

GATEWAY_URL = "http://localhost:8080/api/experiment/run"

def run_api_experiment():
    print("Generating 100 mock requests for experiment...")
    dataset = []
    themes = [
        "Write a Python script to sort an array.",
        "What is the capital of France?",
        "Explain quantum computing.",
        "Translate this to Spanish.",
        "Write a poem about AI."
    ]
    for i in range(100):
        # randomly assign a tenant
        tenant = random.choice(["enterprise-a", "startup-b", "research-c"])
        prompt = f"{random.choice(themes)} (Var {i})"
        dataset.append({
            "message": prompt,
            "tenantId": tenant
        })

    print(f"Sending {len(dataset)} requests to {GATEWAY_URL}...")
    try:
        res = requests.post(GATEWAY_URL, json=dataset, timeout=300)
        if res.status_code == 200:
            results = res.json()
            print("Experiment completed. Results:")
            for r in results:
                print(f"Strategy: {r.get('strategy')}")
                print(f"  Avg Reward: {r.get('averageReward'):.4f}")
                print(f"  Cum Regret: {r.get('cumulativeRegret'):.4f}")
                print(f"  Accuracy:   {r.get('selectionAccuracy'):.4f}")
                print("-" * 30)
                
            # Plot Regret
            names = [r.get('strategy') for r in results]
            regrets = [r.get('cumulativeRegret') for r in results]
            
            plt.figure(figsize=(10, 6))
            plt.bar(names, regrets, color=['gray', 'orange', 'red', 'blue', 'green'])
            plt.title('Cumulative Regret across Routing Strategies (Lower is better)')
            plt.ylabel('Cumulative Regret')
            plt.xticks(rotation=45)
            plt.tight_layout()
            plt.savefig('routing_regret_comparison.png')
            print("Saved plot to routing_regret_comparison.png")
            
        else:
            print(f"Error: {res.status_code} - {res.text}")
    except Exception as e:
        print(f"Exception: {e}")

if __name__ == "__main__":
    run_api_experiment()
