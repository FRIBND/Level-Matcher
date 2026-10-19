import pandas as pd
import numpy as np
from xgboost import XGBRegressor
from lightgbm import LGBMRegressor
from Feature_Engineer import extract_features, generate_synthetic_training_data, load_levels_from_json
import warnings

"""
Engine for Nuclear Level Matching
======================================

# High-level Structure and Workflow Explanation:
-------------------------

1. **Data Loading & Standardization** (`load_levels_from_json`):
   - Parse ENSDF JSON (datasets A, B, C) into flat records: energy_value, energy_uncertainty, spin_parity_list, spin_parity_string, dataset_code, level_id.

2. **Physics-Informed Feature Space** (from Feature_Engineer):
   - Compute [Energy_Similarity, Spin_Similarity, Parity_Similarity, Specificity, Gamma_Decay_Pattern_Similarity].
   - All features are monotonic increasing: higher value strictly indicates better match likelihood.

3. **Model Training** (XGBoost and LightGBM with monotone constraints):
   - Train binary logistic regressor on synthetic physics-labeled pairs from Feature_Engineer.
   - Monotone constraints: all five features constrained positive to preserve physics priors.
   - Feature Correlation: Model learns that perfect spin+parity can "rescue" mediocre energy similarity.
   - It takes 10-20 seconds to train both models.
   - XGBoost: Standard robust performance.
   - LightGBM: Independent test for comparison.
     * Configuration: Heavily regularized (L1/L2, restricted depth) to prevent overfitting on small datasets.
     * Behavior: Observed to favor Gamma patterns more strongly than XGBoost (e.g., A_2000 vs B_2006).

4. **Pairwise Inference** (cross-dataset only):
   - Enumerate all level pairs across datasets, extract features, predict match probability pair-by-pair.
   - Keep pairs above pairwise_output_threshold (default 0.1%) and write Output_Level_Pairwise_Inference.txt.

5. **Graph Clustering (rule-based, no ML)**:
   - Greedy merge clusters in probability order with dataset-uniqueness and all-to-all compatibility checks (threshold clustering_merge_threshold=0.15).
   - Support multi-cluster membership for ambiguous levels; output clusters to Output_Clustering_Results.txt with anchor and member probabilities.
"""

# Configuration Parameters
pairwise_output_threshold = 0.001  # Minimum probability for outputting level pairs (0.1%)
clustering_merge_threshold = 0.15  # Minimum probability for cluster merging (15%)

# Feature names for explicit labeling (prevents sklearn warnings)
feature_names = ['Energy_Similarity', 'Spin_Similarity', 'Parity_Similarity', 
                 'Specificity', 'Gamma_Decay_Pattern_Similarity']

# ==========================================
# 1. Test Data Ingestion (From JSON files)
# ==========================================
levels = load_levels_from_json(['A', 'B', 'C'])

dataframe = pd.DataFrame(levels)
# Generate unique level IDs for tracking (e.g., A_1000)
dataframe['level_id'] = dataframe.apply(lambda row: f"{row['dataset_code']}_{int(row['energy_value'])}", axis=1)

# ==========================================
# 2. Model Training (Physics-Informed XGBoost + LightGBM Ensemble)
# ==========================================
# Feature Vector: [Energy_Similarity, Spin_Similarity, Parity_Similarity, Specificity, Gamma_Decay_Pattern_Similarity]
# All Features Monotonic Increasing (higher value → higher match probability):
# 1. Energy_Similarity (+1): Gaussian kernel exp(-0.1×z²) where z=ΔE/σ_combined. Perfect overlap (z=0)→1.0, far apart (z>5)→~0.0
# 2. Spin_Similarity (+1): Best-case compatibility across spin options. Firm match→1.0, tentative match→0.9, adjacent mismatch→0.05-0.2, incompatible→0.0
# 3. Parity_Similarity (+1): Best-case compatibility across parity options. Firm match→1.0, tentative match→0.9, opposite parity→0.0-0.1
# 4. Specificity (+1): Ambiguity penalty = 1/sqrt(multiplicity). Single Jπ→1.0, double options→0.71, triple options→0.58, high ambiguity→0.33
# 5. Gamma_Decay_Pattern_Similarity (+1): Cosine similarity of Gaussian-broadened spectra. Perfect match→1.0, no overlap→0.0, missing data→0.5 (neutral)
#
# Note: Certainty features removed (redundant - tentativeness already encoded in similarity scores via 1.0 vs 0.9 penalty)

# ==========================================
# 4. Graph Clustering Function (Refactored for Independent Execution)
# ==========================================
def perform_clustering_and_output(matching_level_pairs, model_instance, output_filename, model_name):
    """
    Executes greedy graph clustering and writes results to file.
    Refactored to allow independent execution for different models (XGB vs LGBM).
    Preserves all original logic and educational comments.
    """
    
    # ==========================================
    # 4. Graph Clustering with Overlap Support
    # ==========================================
    # Algorithm: Greedy cluster merging based on match probability ranking
    # Key Constraints:
    #   - Dataset Uniqueness: Each cluster contains at most one level per dataset
    #   - Mutual Consistency: All cluster members must be pairwise compatible
    #   - Ambiguity Resolution: Levels with poor resolution (large uncertainty) can belong to multiple clusters when they are compatible with multiple well-resolved levels
    # This section uses NO ML, only rule-based graph algorithms for logical cluster merging
    
    print(f"\n--- Starting Clustering for {model_name} ---")

    # Step 1 - Initialize singleton clusters and lookup table
    # Note: dataframe is accessed from global scope
    level_lookup = {row['level_id']: row for _, row in dataframe.iterrows()}
    initial_clusters = [{row['dataset_code']: row['level_id']} for _, row in dataframe.iterrows()]
    
    # Track which clusters each level belongs to (one level can be in multiple clusters)
    id_to_clusters = {}
    for cluster in initial_clusters:
        # Extract the single level_id from this single-member cluster
        # cluster.values() returns dict_values(['A_1000']), list() converts to ['A_1000'], [0] gets 'A_1000'
        member_id = list(cluster.values())[0]
        id_to_clusters[member_id] = [cluster]

    # Step 2 - Extract strong candidate pairs for merging
    # Only level pairs exceeding clustering_merge_threshold qualify for cluster operations
    valid_pairs = set()
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] >= clustering_merge_threshold:
            valid_pairs.add((matching_level_pair['ID1'], matching_level_pair['ID2']))
            valid_pairs.add((matching_level_pair['ID2'], matching_level_pair['ID1']))

    # Step 3 - Greedy cluster merging
    # Process candidates in descending probability order, attempting merges or multi-cluster assignment for ambiguous levels
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] < clustering_merge_threshold:
            break  # Skip weak matches (list is sorted by probability)
        
        id_1, id_2 = matching_level_pair['ID1'], matching_level_pair['ID2']
        
        # Snapshot current cluster memberships before modification
        cluster_list_1 = list(id_to_clusters[id_1])
        cluster_list_2 = list(id_to_clusters[id_2])

        # Track if this pair successfully joined any existing cluster
        pair_processed = False

        # Try to merge each cluster pair that contains id_1 and id_2
        for cluster_1 in cluster_list_1:
            for cluster_2 in cluster_list_2:
                if cluster_1 is cluster_2:
                    pair_processed = True  # Already in same cluster
                    continue
                
                datasets_1 = set(cluster_1.keys())
                datasets_2 = set(cluster_2.keys())
                
                # Scenario A: Dataset overlap (both clusters have levels from same dataset)
                if not datasets_1.isdisjoint(datasets_2):
                    # Example: cluster_1 has A_3000, cluster_2 has A_3005 (both resolved), C_3002 (poorly resolved with large uncertainty) matches both
                    # Cannot merge these clusters (violates dataset uniqueness), but try multi-cluster assignment for ambiguous levels
                    
                    # Can we add id_1 to cluster_2?
                    if matching_level_pair['dataset_1'] not in datasets_2:
                        # Check if id_1 is compatible with ALL existing members of cluster_2
                        if all((id_1, member_id) in valid_pairs for member_id in cluster_2.values()):
                            if id_1 not in cluster_2.values():
                                cluster_2[matching_level_pair['dataset_1']] = id_1
                                if cluster_2 not in id_to_clusters[id_1]:
                                    id_to_clusters[id_1].append(cluster_2)
                                pair_processed = True
                    
                    # Can we add id_2 to cluster_1?
                    if matching_level_pair['dataset_2'] not in datasets_1:
                        # Check if id_2 is compatible with ALL existing members of cluster_1
                        if all((id_2, member_id) in valid_pairs for member_id in cluster_1.values()):
                            if id_2 not in cluster_1.values():
                                cluster_1[matching_level_pair['dataset_2']] = id_2
                                if cluster_1 not in id_to_clusters[id_2]:
                                    id_to_clusters[id_2].append(cluster_1)
                                pair_processed = True
                    
                    continue  # Move to next cluster pair

                # Scenario B: No dataset overlap (can potentially merge)
                # Verify all-to-all compatibility before merging
                consistent = True
                for member_1 in cluster_1.values():
                    for member_2 in cluster_2.values():
                        if (member_1, member_2) not in valid_pairs:
                            consistent = False
                            break
                    if not consistent:
                        break
                
                if consistent:
                    # Perform merge: absorb cluster_2 into cluster_1
                    cluster_1.update(cluster_2)
                    
                    # Update all members of cluster_2 to point to cluster_1
                    for member_id in cluster_2.values():
                        if cluster_2 in id_to_clusters[member_id]:
                            id_to_clusters[member_id].remove(cluster_2)
                        if cluster_1 not in id_to_clusters[member_id]:
                            id_to_clusters[member_id].append(cluster_1)
                    
                    pair_processed = True
        
        # If this valid pair was not absorbed into any existing cluster, try adding to singleton clusters first
        if not pair_processed:
            # Check if id_1 is in a singleton cluster that we can expand
            for cluster in id_to_clusters[id_1]:
                if len(cluster) == 1 and matching_level_pair['dataset_2'] not in cluster:
                    # Expand singleton cluster by adding id_2
                    cluster[matching_level_pair['dataset_2']] = id_2
                    if cluster not in id_to_clusters[id_2]:
                        id_to_clusters[id_2].append(cluster)
                    pair_processed = True
                    break
            
            # If still not processed, check if id_2 is in a singleton cluster
            if not pair_processed:
                for cluster in id_to_clusters[id_2]:
                    if len(cluster) == 1 and matching_level_pair['dataset_1'] not in cluster:
                        # Expand singleton cluster by adding id_1
                        cluster[matching_level_pair['dataset_1']] = id_1
                        if cluster not in id_to_clusters[id_1]:
                            id_to_clusters[id_1].append(cluster)
                        pair_processed = True
                        break
            
            # Only create new cluster if neither member has a singleton to expand
            if not pair_processed:
                new_cluster = {
                    matching_level_pair['dataset_1']: id_1,
                    matching_level_pair['dataset_2']: id_2
                }
                id_to_clusters[id_1].append(new_cluster)
                id_to_clusters[id_2].append(new_cluster)

    # Step 4 - Extract unique active clusters (remove duplicates)
    unique_clusters = []
    seen = set()
    for cluster_list in id_to_clusters.values():
        for cluster in cluster_list:
            cluster_id = id(cluster)
            if cluster_id not in seen:
                unique_clusters.append(cluster)
                seen.add(cluster_id)
    
    # Step 5 - Sort clusters by average energy for consistent output
    def calculate_cluster_average_energy(cluster):
        energies = [level_lookup[member_id]['energy_value'] for member_id in cluster.values()]
        return sum(energies) / len(energies)
    
    unique_clusters.sort(key=calculate_cluster_average_energy)

    # Write clustering results to both console and file
    clustering_output_lines = []
    clustering_output_lines.append(f"=== FINAL CLUSTERING RESULTS ({model_name}) ===\n")
    
    print(f"\n=== FINAL CLUSTERING RESULTS ({model_name}) ===")
    for i, cluster in enumerate(unique_clusters):
        # Select anchor as the level with smallest energy uncertainty (most precise measurement)
        anchor_id = min(cluster.values(), key=lambda x: level_lookup[x]['energy_uncertainty'] if pd.notna(level_lookup[x]['energy_uncertainty']) else 999)
        anchor_data = level_lookup[anchor_id]
        anchor_energy = anchor_data['energy_value']
        anchor_uncertainty = anchor_data['energy_uncertainty'] if pd.notna(anchor_data['energy_uncertainty']) else 0
        anchor_jpi = anchor_data.get('spin_parity_string', '')
        if anchor_jpi == "unknown": anchor_jpi = "N/A"
        
        cluster_header = f"\nCluster {i+1}:"
        anchor_line = f"  Anchor: {anchor_id} | E={anchor_energy:.1f}±{anchor_uncertainty:.1f} keV | Jπ={anchor_jpi}"
        members_header = "  Members:"
        
        # Only print details to console for the primary model (XGBoost) to avoid cluttered output
        if model_name == "XGBoost":
            print(cluster_header)
            print(anchor_line)
            print(members_header)
        
        clustering_output_lines.append(cluster_header + "\n")
        clustering_output_lines.append(anchor_line + "\n")
        clustering_output_lines.append(members_header + "\n")
        
        # Display each member with match probability relative to anchor
        # Anchor member shows no probability (identity match). Other members show ML-predicted probability.
        for dataset_code in sorted(cluster.keys()):
            member_id = cluster[dataset_code]
            member_data = level_lookup[member_id]
            member_energy = member_data['energy_value']
            member_uncertainty = member_data['energy_uncertainty'] if pd.notna(member_data['energy_uncertainty']) else 0
            member_jpi = member_data.get('spin_parity_string', '')
            if member_jpi == "unknown": member_jpi = "N/A"
            
            if member_id == anchor_id:
                member_line = f"    [{dataset_code}] {member_id}: E={member_energy:.1f}±{member_uncertainty:.1f} keV, Jπ={member_jpi} (Anchor)"
                if model_name == "XGBoost": print(member_line)
                clustering_output_lines.append(member_line + "\n")
            else:
                input_vector = extract_features(member_data, anchor_data)
                # Use the specific model instance passed to the function
                # Convert to DataFrame with explicit feature names
                input_dataframe = pd.DataFrame([input_vector], columns=feature_names)
                probability = model_instance.predict(input_dataframe)[0]
                member_line = f"    [{dataset_code}] {member_id}: E={member_energy:.1f}±{member_uncertainty:.1f} keV, Jπ={member_jpi} (Match Prob: {probability:.1%})"
                if model_name == "XGBoost": print(member_line)
                clustering_output_lines.append(member_line + "\n")
    
    # Write clustering results to file
    clustering_threshold_percent = int(clustering_merge_threshold * 100)
    with open(output_filename, 'w', encoding='utf-8') as output_file:
        output_file.write(f"=== FINAL CLUSTERING RESULTS: {model_name} (Merge Threshold: >{clustering_threshold_percent}%) ===\n")
        output_file.write(f"Total Clusters: {len(unique_clusters)}\n")
        output_file.writelines(clustering_output_lines)
    
    print(f"\n[INFO] Clustering Complete ({model_name}): {len(unique_clusters)} clusters written to '{output_filename}'")


if __name__ == "__main__":
    # Get training data from physics parser
    training_features, training_labels = generate_synthetic_training_data()
    
    # Convert to DataFrame with explicit feature names to prevent sklearn warnings
    training_dataframe = pd.DataFrame(training_features, columns=feature_names)

    # Train XGBoost regressor with monotonic constraints enforcing physics rules
    # All five features designed as higher value → better match probability
    print("Training XGBoost Model...")
    level_matcher_model_xgb = XGBRegressor(objective='binary:logistic',
                                       # Learning Objective Function: Training Loss + Regularization.
                                       # The loss function computes the difference between the true y value and the predicted y value.
                                       # The regularization term discourages overly complex trees.
                                       monotone_constraints='(1, 1, 1, 1, 1)',
                                       # Enforce increasing constraint on all five features.
                                       # Physics prior: higher feature values always indicate better matches.
                                       # Monotonic constraints improve predictive performance when strong prior beliefs exist.
                                       n_estimators=1000,
                                       # Number of gradient boosted trees or boosting rounds. Typical ranges for n_estimators are between 50 and 1000, with higher values generally leading to better performance but longer training times.
                                       max_depth=10,
                                       # Maximum tree depth. Lower values prevent overfitting but may underfit, while larger values allow the model to capture more complex relationships but may lead to overfitting.
                                       # Value of 3 balances model complexity with generalization.
                                       learning_rate=0.05,
                                       # Step size; determines the contribution of each tree to the final outcome by scaling the weights of the features.
                                       # Impact on model performance:
                                       # Lower values slow down learning but can improve generalization
                                       # Higher values speed up learning but may lead to suboptimal results
                                       # Interaction with the number of trees:
                                       # Lower learning rates typically require more trees
                                       # Higher learning rates may converge faster but require careful tuning of other parameters
                                       random_state=42)
                                       # Random number seed for reproducibility.
    
    # Essential Step 1: Train model on synthetic training data
    level_matcher_model_xgb.fit(training_dataframe, training_labels)

    # Initialize LightGBM Regressor (Ensemble component)
    print("Training LightGBM Model...")
    # Note: 'objective="binary"' in LightGBM is equivalent to 'binary:logistic' in XGBoost (outputs probability).
    # We apply strong regularization to prevent overconfidence (100%/0% outputs) on small datasets.
    # Configuration Logic:
    # 1. reg_alpha=1.0 (L1), reg_lambda=10.0 (L2): Penalize extreme weights to ensure soft probability outputs (e.g. 99.5% instead of 100%).
    # 2. num_leaves=7, max_depth=5: Constrain model complexity to prevent memorization of synthetic samples.
    # 3. min_child_samples=20: Force generalization by requiring more data per leaf.
    # Observation: This configuration causes LGBM to weight perfect Gamma Patterns higher than XGBoost (e.g., A_2000<->B_2006 case).
    level_matcher_model_lgbm = LGBMRegressor(objective='binary',
                                             monotone_constraints="1,1,1,1,1",  # Increasing constraints
                                             n_estimators=500,
                                             max_depth=5,
                                             num_leaves=7,
                                             min_child_samples=20,
                                             learning_rate=0.02,
                                             reg_alpha=1.0,       # L1 regularization to encourage sparse solutions
                                             reg_lambda=10.0,     # L2 regularization to prevent extreme leaf weights (100% confidence)
                                             verbose=-1,
                                             random_state=42)
    
    # Train LightGBM model
    level_matcher_model_lgbm.fit(training_dataframe, training_labels)

    # ==========================================
    # 3. Pairwise Inference
    # ==========================================
    # Extract match probabilities for all cross-dataset level pairs using the trained XGBoost model
    matching_level_pairs_xgb = []
    matching_level_pairs_lgbm = []
    all_pairs_display = []

    rows_list = list(dataframe.iterrows())
    
    print("\nRunning Pairwise Inference (XGBoost & LightGBM)...")
    for i in range(len(rows_list)):
        for j in range(i + 1, len(rows_list)):
            _, level_1 = rows_list[i]
            _, level_2 = rows_list[j]
            
            # Skip same-dataset pairs (only match across different datasets)
            if level_1['dataset_code'] == level_2['dataset_code']:
                continue

            # Extract the input features for this level pair
            feature_vector = extract_features(level_1, level_2)
            
            # Essential Step 2: Use the trained Ensemble models to predict match probability for this input feature vector
            # Convert to DataFrame with explicit feature names
            feature_dataframe = pd.DataFrame([feature_vector], columns=feature_names)
            xb_prob = level_matcher_model_xgb.predict(feature_dataframe)[0]
            lgbm_prob = level_matcher_model_lgbm.predict(feature_dataframe)[0]
            
            # Separate logic: No averaging. Each model is independent.
            
            # Record level pairs above output threshold (Dual Report)
            if xb_prob > pairwise_output_threshold or lgbm_prob > pairwise_output_threshold:
                 all_pairs_display.append({
                    'id1': level_1['level_id'], 'id2': level_2['level_id'],
                    'd1': level_1['dataset_code'], 'd2': level_2['dataset_code'],
                    'p_xgb': xb_prob, 'p_lgbm': lgbm_prob,
                    'features': feature_vector
                })

            # Prepare separate lists for clustering
            if xb_prob > pairwise_output_threshold:
                matching_level_pairs_xgb.append({
                    'ID1': level_1['level_id'],
                    'ID2': level_2['level_id'],
                    'dataset_1': level_1['dataset_code'],
                    'dataset_2': level_2['dataset_code'],
                    'probability': xb_prob,
                    'features': feature_vector
                })
            
            if lgbm_prob > pairwise_output_threshold:
                matching_level_pairs_lgbm.append({
                    'ID1': level_1['level_id'],
                    'ID2': level_2['level_id'],
                    'dataset_1': level_1['dataset_code'],
                    'dataset_2': level_2['dataset_code'],
                    'probability': lgbm_prob,
                    'features': feature_vector
                })

    # Sort by probability descending (using max of both for the combined list)
    all_pairs_display.sort(key=lambda x: max(x['p_xgb'], x['p_lgbm']), reverse=True)
    matching_level_pairs_xgb.sort(key=lambda x: x['probability'], reverse=True)
    matching_level_pairs_lgbm.sort(key=lambda x: x['probability'], reverse=True)

    # Write pairwise inference results to file (Side-by-Side Comparison)
    threshold_percent = pairwise_output_threshold * 100
    with open('outputs/pairwise/Output_Level_Pairwise_Inference.txt', 'w', encoding='utf-8') as output_file:
        output_file.write(f"=== PAIRWISE INFERENCE RESULTS (>{threshold_percent:.1f}%) ===\n")
        output_file.write(f"Model Comparison: XGBoost vs LightGBM\n")
        output_file.write(f"Total Level Pairs Found: {len(all_pairs_display)}\n\n")
        
        for item in all_pairs_display:
            energy_sim, spin_sim, parity_sim, specificity, gamma_pattern_sim = item['features']
            output_file.write(
                f"{item['id1']} <-> {item['id2']} | "
                f"XGB: {item['p_xgb']:.1%} | LGBM: {item['p_lgbm']:.1%}\n"
                f"  Features: Energy_Sim={energy_sim:.2f}, Spin_Sim={spin_sim:.2f}, "
                f"Parity_Sim={parity_sim:.2f}, Specificity={specificity:.2f}, Gamma_Pattern_Sim={gamma_pattern_sim:.2f}\n\n"
            )
    
    print(f"\n[INFO] Pairwise Inference Complete: {len(all_pairs_display)} level pairs (>{threshold_percent:.1f}%) written to 'outputs/pairwise/Output_Level_Pairwise_Inference.txt'")

    # ==========================================
    # 5. Execute Independent Clustering
    # ==========================================
    
    # Run clustering for XGBoost (Primary)
    perform_clustering_and_output(matching_level_pairs_xgb, level_matcher_model_xgb, "outputs/clustering/Output_Clustering_Results_XGB.txt", "XGBoost")
    
    # Run clustering for LightGBM (Comparison)
    perform_clustering_and_output(matching_level_pairs_lgbm, level_matcher_model_lgbm, "outputs/clustering/Output_Clustering_Results_LightGBM.txt", "LightGBM")
