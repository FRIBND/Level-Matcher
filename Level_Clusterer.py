import pandas as pd
from Feature_Engineer import extract_features

"""
# High-level Structure and Module Architecture:
======================================

Workflow Diagram:
[Start]
   |
   v
[Step 1: Edge Score Filtering] --> [XGBoost/LightGBM Probabilities]
   |
   v
[Step 2: Constraint Validation] --> [Dataset Uniqueness Rule]
   |
   v
[Step 3: Clique Partitioning] --> [Graph Consensus]
   |
   v
[Step 4: Consensus Assignment] --> [Final Level Scheme]

Numbered Technical Steps:
1. **Edge Filtering**: Identify all level match candidates with high Machine Learning probability.
2. **Deterministic Partitioning**: Group levels across different datasets that represent the same physical state.
3. **Consensus Merging**: Calculate the best-estimate energy and quantum properties for each merged cluster.
4. **Physical Validation**: Enforce that a single physical level cannot contain multiple levels from the same dataset.

Module Purpose:
This module implements a Constrained Graph Partitioning algorithm to reconcile matched level pairs into coherent physical levels.

ALGORITHM CLASSIFICATION & COMPARISON:
--------------------------------------
This module is NOT traditional unsupervised Machine Learning clustering (e.g., discovering communities in raw data). It is a deterministic, rule-based partitioning engine.

| Aspect | Unsupervised Graph Clustering (Standard ML) | Constrained Graph Partitioning (This Module) |
| :--- | :--- | :--- |
| **Logic** | Discovers hidden patterns/densities. | Reconciles known identities with hard rules. |
| **Learning Type** | Unsupervised (no labels). | Deterministic (rules + supervised predictions). |
| **Edge Weights** | Topological proximity or similarity. | Machine Learning Predicted Probabilities (XGBoost/LightGBM). |
| **Constraints** | Usually none (soft clusters). | **Hard Physics Constraints** (Dataset Uniqueness). |
| **Structure** | Overlapping or fuzzy groups. | **Clique-Based** (Mutual Consistency required). |
| **Optimization** | Maximizes Modularity / Minimizes Cut. | Enforces Physical Validity & Clique Integrity. |
| **Examples** | Louvain, DBSCAN, Spectral Clustering. | This custom Clique Partitioning Engine. |
"""

def perform_clustering_and_output(matching_level_pairs, model_instance, output_filename, model_name, level_dataframe, clustering_merge_threshold):
    """
    Executes constrained graph partitioning and writes results to file.
    Algorithm: Deterministic rule-based partitioning using supervised predictions (NOT unsupervised clustering).
    clustering_merge_threshold: minimum match probability for two levels to join one cluster (set in the Parameters dialog).
    Refactored to allow independent execution for different models (XGBoost vs LightGBM).
    Preserves all original logic and educational comments.
    """
    
    # Feature names for explicit labeling (prevents sklearn warnings)
    feature_names = ['Energy_Similarity', 'Spin_Similarity', 'Parity_Similarity', 
                     'Specificity', 'Gamma_Decay_Pattern_Similarity']

    # ==========================================
    # Logic: Greedy Constrained Partitioning
    # ==========================================
    # Algorithm: Greedy partition merging based on supervised match probability ranking (NOT unsupervised clustering)
    # Classification: Constrained Agglomerative Partitioning with domain-specific hard constraints
    # Key Constraints:
    #   1. Dataset Uniqueness: Each cluster contains at most one level per dataset (no duplicate sources)
    #   2. Mutual Consistency (Clique Structure): All cluster members must be pairwise compatible (all pairs > clustering_merge_threshold).
    #      This prevents "chain" merges where A-B (strong) and B-C (strong) but A-C (weak < threshold).
    #      Before merging two clusters, code verifies EVERY member in Cluster 1 matches EVERY member in Cluster 2 (lines 130-145).
    #   3. Multi-Cluster Membership: Unresolved levels (large uncertainty) can belong to multiple distinct clusters simultaneously,
    #      representing doublet/triplet cases where low-resolution data matches multiple high-resolution resolved states.
    
    print(f"\n--- Starting Clustering for {model_name} ---")

    # Initialize singleton clusters and lookup table
    # Note: Using level_dataframe passed as argument
    level_lookup = {row['level_id']: row for _, row in level_dataframe.iterrows()}
    initial_clusters = [{row['dataset_code']: row['level_id']} for _, row in level_dataframe.iterrows()]
    
    # Track which clusters each level belongs to (one level can be in multiple clusters)
    level_id_to_clusters = {}
    for cluster in initial_clusters:
        # Extract the single level_id from this single-member cluster
        # cluster.values() returns dict_values(['A_1000']), list() converts to ['A_1000'], [0] gets 'A_1000'
        member_id = list(cluster.values())[0]
        level_id_to_clusters[member_id] = [cluster]

    # Extract strong candidate pairs for merging
    # Only level pairs exceeding clustering_merge_threshold qualify for cluster operations
    valid_pairs = set()
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] >= clustering_merge_threshold:
            valid_pairs.add((matching_level_pair['level_1_id'], matching_level_pair['level_2_id']))
            valid_pairs.add((matching_level_pair['level_2_id'], matching_level_pair['level_1_id']))

    # Greedy cluster merging
    # Process candidates in descending probability order, attempting merges or multi-cluster assignment for ambiguous levels
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] < clustering_merge_threshold:
            break  # Skip weak matches (list is sorted by probability)
        
        level_id_1, level_id_2 = matching_level_pair['level_1_id'], matching_level_pair['level_2_id']
        
        # Snapshot current cluster memberships before modification
        cluster_list_1 = list(level_id_to_clusters[level_id_1])
        cluster_list_2 = list(level_id_to_clusters[level_id_2])

        # Track if this pair successfully joined any existing cluster
        pair_processed = False

        # Try to merge each cluster pair that contains level_id_1 and level_id_2
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
                    
                    # Can we add level_id_1 to cluster_2?
                    if matching_level_pair['dataset_code_1'] not in datasets_2:
                        # Check if level_id_1 is compatible with ALL existing members of cluster_2
                        if all((level_id_1, member_id) in valid_pairs for member_id in cluster_2.values()):
                            if level_id_1 not in cluster_2.values():
                                cluster_2[matching_level_pair['dataset_code_1']] = level_id_1
                                if cluster_2 not in level_id_to_clusters[level_id_1]:
                                    level_id_to_clusters[level_id_1].append(cluster_2)
                                pair_processed = True
                    
                    # Can we add level_id_2 to cluster_1?
                    if matching_level_pair['dataset_code_2'] not in datasets_1:
                        # Check if level_id_2 is compatible with ALL existing members of cluster_1
                        if all((level_id_2, member_id) in valid_pairs for member_id in cluster_1.values()):
                            if level_id_2 not in cluster_1.values():
                                cluster_1[matching_level_pair['dataset_code_2']] = level_id_2
                                if cluster_1 not in level_id_to_clusters[level_id_2]:
                                    level_id_to_clusters[level_id_2].append(cluster_1)
                                pair_processed = True
                    
                    continue  # Move to next cluster pair

                # Scenario B: No dataset overlap (can potentially merge)
                # Verify all-to-all compatibility before merging (Critical: prevents chain-of-weak-matches)
                # Example: If A matches B (80%), B matches C (80%), but A-C (10%) fails threshold,
                # this loop will detect (A, C) not in valid_pairs and reject the merge.
                # This enforces clique structure: every member must match every other member above threshold.
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
                        if cluster_2 in level_id_to_clusters[member_id]:
                            level_id_to_clusters[member_id].remove(cluster_2)
                        if cluster_1 not in level_id_to_clusters[member_id]:
                            level_id_to_clusters[member_id].append(cluster_1)
                    
                    pair_processed = True
        
        # If this valid pair was not absorbed into any existing cluster, try adding to singleton clusters first
        if not pair_processed:
            # Check if level_id_1 is in a singleton cluster that we can expand
            for cluster in level_id_to_clusters[level_id_1]:
                if len(cluster) == 1 and matching_level_pair['dataset_code_2'] not in cluster:
                    # Expand singleton cluster by adding level_id_2
                    cluster[matching_level_pair['dataset_code_2']] = level_id_2
                    if cluster not in level_id_to_clusters[level_id_2]:
                        level_id_to_clusters[level_id_2].append(cluster)
                    pair_processed = True
                    break
            
            # If still not processed, check if level_id_2 is in a singleton cluster
            if not pair_processed:
                for cluster in level_id_to_clusters[level_id_2]:
                    if len(cluster) == 1 and matching_level_pair['dataset_code_1'] not in cluster:
                        # Expand singleton cluster by adding level_id_1
                        cluster[matching_level_pair['dataset_code_1']] = level_id_1
                        if cluster not in level_id_to_clusters[level_id_1]:
                            level_id_to_clusters[level_id_1].append(cluster)
                        pair_processed = True
                        break
            
            # Only create new cluster if neither member has a singleton to expand
            if not pair_processed:
                new_cluster = {
                    matching_level_pair['dataset_code_1']: level_id_1,
                    matching_level_pair['dataset_code_2']: level_id_2
                }
                level_id_to_clusters[level_id_1].append(new_cluster)
                level_id_to_clusters[level_id_2].append(new_cluster)

    # Extract unique active clusters (remove duplicates)
    unique_clusters = []
    seen = set()
    for cluster_list in level_id_to_clusters.values():
        for cluster in cluster_list:
            cluster_id = id(cluster)
            if cluster_id not in seen:
                unique_clusters.append(cluster)
                seen.add(cluster_id)
    
    # Sort clusters by average energy for consistent output
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
        anchor_spin_parity = anchor_data.get('spin_parity_string', '')
        if anchor_spin_parity == "unknown": anchor_spin_parity = "N/A"
        
        cluster_header = f"\nCluster {i+1}:"
        anchor_line = f"  Anchor: {anchor_id} | E={anchor_energy:.1f}±{anchor_uncertainty:.1f} keV | Spin-Parity={anchor_spin_parity}"
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
        # Anchor member shows no probability (identity match). Other members show Machine Learning-predicted probability.
        for dataset_code in sorted(cluster.keys()):
            member_id = cluster[dataset_code]
            member_data = level_lookup[member_id]
            member_energy = member_data['energy_value']
            member_uncertainty = member_data['energy_uncertainty'] if pd.notna(member_data['energy_uncertainty']) else 0
            member_spin_parity = member_data.get('spin_parity_string', '')
            if member_spin_parity == "unknown": member_spin_parity = "N/A"
            
            if member_id == anchor_id:
                member_line = f"    [{dataset_code}] {member_id}: E={member_energy:.1f}±{member_uncertainty:.1f} keV, Spin-Parity={member_spin_parity} (Anchor)"
                if model_name == "XGBoost": print(member_line)
                clustering_output_lines.append(member_line + "\n")
            else:
                input_vector = extract_features(member_data, anchor_data)
                # Use the specific model instance passed to the function
                # Convert to DataFrame with explicit feature names
                input_dataframe = pd.DataFrame([input_vector], columns=feature_names)
                probability = model_instance.predict(input_dataframe)[0]
                member_line = f"    [{dataset_code}] {member_id}: E={member_energy:.1f}±{member_uncertainty:.1f} keV, Spin-Parity={member_spin_parity} (Match Probability: {probability:.1%})"
                if model_name == "XGBoost": print(member_line)
                clustering_output_lines.append(member_line + "\n")
    
    # Write clustering results to file
    clustering_threshold_percent = clustering_merge_threshold * 100
    with open(output_filename, 'w', encoding='utf-8') as output_file:
        output_file.write(f"=== FINAL CLUSTERING RESULTS: {model_name} (Merge Threshold: >{clustering_threshold_percent:g}%) ===\n")
        output_file.write(f"Total Clusters: {len(unique_clusters)}\n")
        output_file.writelines(clustering_output_lines)
    
    print(f"\n[INFO] Clustering Complete ({model_name}): {len(unique_clusters)} clusters written to '{output_filename}'")
