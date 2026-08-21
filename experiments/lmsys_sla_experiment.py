import requests
import json
import time
import matplotlib.pyplot as plt
import pandas as pd
import random
import os

GATEWAY_URL = "http://localhost:8080/api/chat"

# We'll use a mix of synthetic LMSYS-style prompts for the simulation
# to avoid heavy dependencies like huggingface-datasets for a quick run.
# In a real environment, you'd load the full 100k CSV here.
PROMPT_THEMES = [
    "Write a Python script to sort an array.",
    "What is the capital of France?",
    "Explain quantum computing to a 5-year-old.",
    "Translate this sentence to Spanish: I love programming.",
    "Write a poem about artificial intelligence.",
    "How do I fix a NullPointerException in Java?",
    "What are the best practices for REST API design?",
    "Write a SQL query to join two tables and group by date.",
    "Summarize the plot of the movie Inception.",
    "Create a regular expression to match email addresses."
]

def generate_mock_lmsys_dataset(num_samples=1000):
    print(f"Generating {num_samples} realistic mock prompts...")
    dataset = []
    for i in range(num_samples):
        base_prompt = random.choice(PROMPT_THEMES)
        # Add slight variations to make them unique
        dataset.append(f"{base_prompt} (Variation {i})")
    return dataset

def run_experiment():
    print("=== NexusAI Phase 2 & 3: SLA Non-Stationarity Experiment ===")
    
    # 1. Load 40 Prompts
    prompts = generate_mock_lmsys_dataset(40)
    
    # Trackers for plotting
    history = []
    
    tenant_id = "startup-b" 

    print(f"\n[Phase 2] Pumping 40 prompts for tenant '{tenant_id}' (BUDGET focused)...")
    
    for i, prompt in enumerate(prompts):
        if i == 20:
            print("\n=========================================================")
            print("[Phase 3] AT QUERY 20: DYNAMICALLY FLIPPING SLA POLICY")
            print("Flipping startup-b from BUDGET to MISSION_CRITICAL (Quality focus)")
            
            # Mission Critical Weights: [Quality, Latency, Cost, Availability]
            # Prioritize Quality heavily, ignore Cost.
            new_policy = [0.80, 0.10, 0.00, 0.10]
            try:
                res = requests.put(f"http://localhost:8080/api/tenant/{tenant_id}/policy", json=new_policy)
                print(f"Policy Update Response: {res.status_code} - {res.text}")
            except Exception as e:
                print(f"Failed to update policy: {e}")
                
            print("=========================================================\n")
            
        payload = {
            "message": prompt,
            "tenantId": tenant_id
        }
        
        try:
            start_time = time.time()
            # We add a short timeout so the experiment doesn't hang if the provider is down
            response = requests.post(GATEWAY_URL, json=payload, timeout=30)
            latency = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                provider = data.get("provider", "unknown")
                model = data.get("model", "unknown")
                
                history.append({
                    "iteration": i,
                    "provider": provider,
                    "model": model,
                    "latency": latency
                })
                
                print(f"Query {i}: Routed to {provider}:{model} in {latency:.2f}s")
            else:
                print(f"Query {i} failed with status {response.status_code}")
                
        except Exception as e:
            print(f"Query {i} exception: {e}")
            
    print("\nExperiment complete. Plotting results...")
    
    # Plotting
    df = pd.DataFrame(history)
    if not df.empty:
        # Group by chunks of 5 to see model distribution over time
        df['chunk'] = df['iteration'] // 5
        distribution = df.groupby(['chunk', 'model']).size().unstack(fill_value=0)
        
        distribution.plot(kind='bar', stacked=True, figsize=(12, 6))
        plt.title('Routing Adaptation Over Time (SLA Non-Stationarity)')
        plt.xlabel('Query Chunk (x5)')
        plt.ylabel('Number of Requests Routed')
        plt.legend(title='Selected Model')
        plt.tight_layout()
        plt.savefig('sla_adaptation.png')
        print("Saved routing graph to 'sla_adaptation.png'")

if __name__ == "__main__":
    run_experiment()