import pandas as pd
import numpy as np
import json
import os
from xgboost import XGBRegressor
# support Soft Labels (continuous probabilities like 0.3, 0.5) instead of just binary 0/1

# ==========================================
# 1. DATA INGESTION (From JSON files)
# ==========================================
levels = []
for ds_code in ['A', 'B', 'C']:
    filename = f"dataset_{ds_code}.json"
    if os.path.exists(filename):
        with open(filename, 'r', encoding='utf-8') as f:
            levels.extend(json.load(f))

df = pd.DataFrame(levels)

# Generate unique IDs (e.g., A_1000) for tracking
df['ID'] = df.apply(lambda x: f"{x['DS']}_{int(x['E_level'])}", axis=1)

# ==========================================
# 2. MODEL TRAINING (Physics-Informed XGBoost)
# ==========================================
# Input Features:
# 1. Z-Score: Energy difference in standard deviations (abs(E1-E2)/sigma).
# 2. Physics Veto: Binary flag (1=Mismatch in Spin/Parity, 0=Match/Unknown).
#
# Objective: Predict probability of two levels being the same physical state.

# Training Data: (Z_Score, Veto, Probability)
# Synthetic data encoding physics logic: Low Z + No Veto = High Prob.

MATCH_EXCELLENT = [
    (0.0, 0, 1.00), (0.5, 0, 0.99), (1.0, 0, 0.98), (1.5, 0, 0.95)
]

MATCH_TRANSITION = [
    (1.8, 0, 0.90), (2.0, 0, 0.80), (2.2, 0, 0.70), (2.5, 0, 0.60), (2.8, 0, 0.55), (3.0, 0, 0.50)
]

MATCH_WEAK = [
    (3.2, 0, 0.40), (3.5, 0, 0.20), (4.0, 0, 0.10), (5.0, 0, 0.01), (10.0, 0, 0.00), (20.0, 0, 0.00), (100.0, 0, 0.00)
]

# Veto=1 forces Probability to 0.0 (Strict Physics Rules)
MATCH_VETOED = [
    (0.0, 1, 0.00), (0.1, 1, 0.00), (0.2, 1, 0.00), (0.3, 1, 0.00),
    (0.4, 1, 0.00), (0.5, 1, 0.00), (0.6, 1, 0.00), (0.7, 1, 0.00),
    (0.8, 1, 0.00), (0.9, 1, 0.00), (1.0, 1, 0.00), (1.5, 1, 0.00),
    (2.0, 1, 0.00), (3.0, 1, 0.00), (5.0, 1, 0.00), (100.0, 1, 0.00)
]

training_data_points = MATCH_EXCELLENT + MATCH_TRANSITION + MATCH_WEAK + MATCH_VETOED

# Split into X (Input Features) and y (Target Labels)
X_train = np.array([[pt[0], pt[1]] for pt in training_data_points])
y_train = np.array([pt[2] for pt in training_data_points])

# Monotonic constraints: 
# Feature 0 (Z-Score): Increasing Z reduces prob (-1)
# Feature 1 (Veto): Increasing Veto (0->1) reduces prob (-1)
level_matcher_model = XGBRegressor(objective='binary:logistic', # Objective Function: Training Loss + Regularization. Optimizes the log loss function to predict probability of an instance belonging to a class.
                                   monotone_constraints='(-1, -1)', # Enforce a decreasing constraint on both predictors. In some cases, where there is a very strong prior belief that the true relationship has some quality, constraints can be used to improve the predictive performance of the model.
                                   n_estimators=200, # Number of gradient boosted trees
                                   max_depth=4, # Maximum tree depth for base learners
                                   random_state=42) # Random number seed
# Train the model on the training data
level_matcher_model.fit(X_train, y_train)

# Print all matching candidates with probabilities
# for pt in training_data_points:
#     z, veto, true_prob = pt
#     pred_prob = level_matcher_model.predict([[z, veto]])[0]
#     print(f"Z: {z:<6} Veto: {veto} | True Prob: {true_prob:<5} Predicted Prob: {pred_prob:.4f}")


# ==========================================
# 3. PAIRWISE INFERENCE
# ==========================================
# Calculate match probability for every pair of levels across different datasets.
candidates = []

for i, r1 in df.iterrows():
    for j, r2 in df.iterrows():
        # Rule: Only compare levels from different datasets (e.g., A vs B).
        # We use 'r1['DS'] < r2['DS']' to avoid:
        # 1. Self-matches (A_1000 vs A_1000)
        # 2. Internal matches (A_1000 vs A_2000)
        # 3. Duplicate pairs (Checking A vs B AND B vs A)
        if not (r1['DS'] < r2['DS']):
            continue

        # 1. Calculate Z-Score (Energy difference in sigma units)
        # If DE_level is missing, we assume a default uncertainty of 10 keV.
        sig1 = r1['DE_level'] if pd.notna(r1['DE_level']) else 10
        sig2 = r2['DE_level'] if pd.notna(r2['DE_level']) else 10
        combined_err = np.sqrt(sig1**2 + sig2**2)
        z_score = abs(r1['E_level'] - r2['E_level']) / combined_err

        # 2. Check Physics Veto (Spin/Parity Mismatch)
        veto = 0
        if pd.notna(r1['Spin']) and pd.notna(r2['Spin']) and r1['Spin'] != r2['Spin']: veto = 1
        if pd.notna(r1['Parity']) and pd.notna(r2['Parity']) and r1['Parity'] != r2['Parity']: veto = 1

        # Calculate the match probability (0.0 to 1.0) using the trained XGBoost model.
        # The model interpolates between our training rules (Z-Score and Physics Veto).
        # .predict() requires a 2D list [[Z, Veto]] and returns a list of results; 
        # we use [0] to get the specific probability for this pair.
        prob = level_matcher_model.predict([[z_score, veto]])[0]
        
        # WORKFLOW: This is a "First Pass" (Pairwise Analysis).
        # We look at every possible pair of levels across different datasets (A vs B, A vs C, B vs C).
        # If the model says there is a >50% chance they match, we save them to the candidates list.
        if prob > 0.1:
            candidates.append({
                'ID1': r1['ID'], 'DS1': r1['DS'],
                'ID2': r2['ID'], 'DS2': r2['DS'],
                'prob': prob
            })

candidates.sort(key=lambda x: x['prob'], reverse=True)

print("\n=== MATCHING CANDIDATES (>10% Probability) ===")
for cand in candidates:
    print(f"{cand['ID1']} <-> {cand['ID2']} | Probability: {cand['prob']:.2%}")

# ==========================================
# 4. GRAPH CLUSTERING (Greedy Merge with Overlap Support)
# ==========================================
# Algorithm:
# 1. Initialize every level as a unique cluster.
# 2. Iterate through high-probability candidates.
# 3. Merge clusters if they don't contain conflicting levels.
# 4. Support "Doublets": If two clusters cannot merge due to conflict (e.g. A_3000 vs A_3005),
#    but a level (C_3005) matches both, allow C_3005 to belong to BOTH clusters.

level_lookup = {row['ID']: row for _, row in df.iterrows()}

# Map ID -> List of Cluster Objects (A level can belong to multiple clusters)
# Each cluster is a dict {DS: ID}
initial_clusters = [{row['DS']: row['ID']} for _, row in df.iterrows()]
id_to_clusters = {}
for c in initial_clusters:
    mid = list(c.values())[0]
    id_to_clusters[mid] = [c]

# Create a set of valid ML-approved pairs for O(1) lookup
valid_ml_matches = set()
for cand in candidates:
    valid_ml_matches.add((cand['ID1'], cand['ID2']))
    valid_ml_matches.add((cand['ID2'], cand['ID1']))

for cand in candidates:
    if cand['prob'] < 0.5: break
    
    id1 = cand['ID1']
    id2 = cand['ID2']
    
    # Work on snapshots of the cluster lists since we might modify them
    clusters_1 = list(id_to_clusters[id1])
    clusters_2 = list(id_to_clusters[id2])
    
    for c1 in clusters_1:
        for c2 in clusters_2:
            if c1 is c2: continue # Already in same cluster
            
            # Check Dataset Conflict between the two clusters
            ds_c1 = set(c1.keys())
            ds_c2 = set(c2.keys())
            
            # If datasets overlap (e.g. both have 'A'), we cannot merge the clusters.
            if not ds_c1.isdisjoint(ds_c2):
                # CONFLICT DETECTED.
                # Try "Soft Assignment" (Doublet Logic).
                # If we can't merge the clusters, can we add the individual level to the other cluster?
                
                # Case A: Add id1 to c2?
                # Req 1: c2 must not have a level from id1's dataset.
                # Req 2: id1 must be consistent with all members of c2.
                if cand['DS1'] not in ds_c2:
                    consistent = True
                    for m_id in c2.values():
                        if (id1, m_id) not in valid_ml_matches:
                            consistent = False
                            break
                    if consistent:
                        # Add id1 to c2
                        c2[cand['DS1']] = id1
                        if c2 not in id_to_clusters[id1]:
                            id_to_clusters[id1].append(c2)

                # Case B: Add id2 to c1?
                if cand['DS2'] not in ds_c1:
                    consistent = True
                    for m_id in c1.values():
                        if (id2, m_id) not in valid_ml_matches:
                            consistent = False
                            break
                    if consistent:
                        # Add id2 to c1
                        c1[cand['DS2']] = id2
                        if c1 not in id_to_clusters[id2]:
                            id_to_clusters[id2].append(c1)
                
                continue # Done with this pair (Merge rejected, but overlap maybe added)

            # No Dataset Conflict. Check Consistency for Merge.
            # All members of c1 must match all members of c2
            consistent_merge = True
            for m1 in c1.values():
                for m2 in c2.values():
                    if (m1, m2) not in valid_ml_matches:
                        consistent_merge = False
                        break
                if not consistent_merge: break
            
            if consistent_merge:
                # Perform Merge: c2 into c1
                c1.update(c2)
                
                # Update references for all members of c2
                for m in c2.values():
                    # Remove c2 from their list
                    if c2 in id_to_clusters[m]:
                        id_to_clusters[m].remove(c2)
                    # Add c1 if not present
                    if c1 not in id_to_clusters[m]:
                        id_to_clusters[m].append(c1)
                
                # Since c2 is merged (consumed), we shouldn't process it further in this loop
                # But we are iterating a snapshot 'clusters_2', so it's fine.
                # The check 'if c2 in id_to_clusters[m]' handles cleanup.

# Extract unique active clusters
unique_clusters = []
seen_ids = set()
for clist in id_to_clusters.values():
    for c in clist:
        cid = id(c)
        if cid not in seen_ids:
            unique_clusters.append(c)
            seen_ids.add(cid)

clusters = []
for members in unique_clusters:
    # Anchor Selection: The member with the lowest uncertainty (DE) defines the cluster physics.
    best_mid = None
    min_de = float('inf')
    
    for mid in members.values():
        row = level_lookup[mid]
        de = row['DE_level'] if pd.notna(row['DE_level']) else 10.0
        if de < min_de:
            min_de = de
            best_mid = mid
            
    clusters.append({'Anchor_ID': best_mid, 'Members': members})

print("\n=== CLUSTERING RESULTS ===")
for i, cluster in enumerate(clusters):
    members_str = ", ".join([f"{ds}:{mid}" for ds, mid in sorted(cluster['Members'].items())])
    print(f"Cluster {i+1}: Anchor={cluster['Anchor_ID']} | Members=[{members_str}]")

# ==========================================
# 5. ADOPTED LEVEL GENERATION
# ==========================================
# Calculate final properties for each cluster.
adopted_levels = []

for cluster in clusters:
    members = cluster['Members']
    anchor = level_lookup[cluster['Anchor_ID']]
    
    # 1. Adopted Energy: Weighted average of all members (Weight = 1/sigma^2)
    vals = []
    for mid in members.values():
        row = level_lookup[mid]
        err = row['DE_level'] if pd.notna(row['DE_level']) else 10.0
        vals.append((row['E_level'], err))
        
    weights = [1/err**2 for _, err in vals]
    adopted_e = sum(e*w for (e, _), w in zip(vals, weights)) / sum(weights)
    
    # 2. Adopted Spin/Parity: Taken from the Anchor (most precise measurement)
    sp = ""
    if pd.notna(anchor['Spin']):
        sp = f"{anchor['Spin']}{anchor['Parity'] if pd.notna(anchor['Parity']) else ''}"
    
    # 3. XREF (Cross-Reference): List of datasets contributing to this level.
    # Derived strictly from the ML clustering results.
    xref_entries = []
    for mid in sorted(members.values()):
        if mid == cluster['Anchor_ID']:
            xref_entries.append(f"{mid}(100%)")
        else:
            # Calculate probability against Anchor
            r1 = level_lookup[mid]
            r2 = anchor # Anchor row
            
            # Recalculate Z-Score and Veto
            sig1 = r1['DE_level'] if pd.notna(r1['DE_level']) else 10
            sig2 = r2['DE_level'] if pd.notna(r2['DE_level']) else 10
            combined_err = np.sqrt(sig1**2 + sig2**2)
            z_score = abs(r1['E_level'] - r2['E_level']) / combined_err
            
            veto = 0
            if pd.notna(r1['Spin']) and pd.notna(r2['Spin']) and r1['Spin'] != r2['Spin']: veto = 1
            if pd.notna(r1['Parity']) and pd.notna(r2['Parity']) and r1['Parity'] != r2['Parity']: veto = 1
            
            prob = level_matcher_model.predict([[z_score, veto]])[0]
            xref_entries.append(f"{mid}({prob:.0%})")

    adopted_levels.append({
        'Adopted_E': round(adopted_e, 1),
        'XREF': " + ".join(xref_entries),
        'Spin_Parity': sp
    })

final_df = pd.DataFrame(adopted_levels).sort_values('Adopted_E')
print("\n=== ML-Predicted Adopted Dataset ===")
print(final_df.to_string(index=False))