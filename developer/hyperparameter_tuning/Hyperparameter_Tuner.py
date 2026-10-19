"""
Hyperparameter Tuning for Nuclear Level Matcher
================================================

# High-level Structure and Workflow Explanation:
======================================

Workflow Diagram:
[Start]
   |
   v
[Synthetic Data Gen] --> [Split: Train / Validation (80/20)]
   |
   v
[Test Loop: Iterate Configs]
   |
   |--> [Train Model (XGBoost)] --> [Validate MSE]
   |
   |--> [Ingest Test Data] --> [Pairwise Inference] --> [Clustering]
   |
   v
[Compute Metrics] --> [MSE, Probability Spread, Confidence Separation]
   |
   v
[Select Best Config] --> [End]

Strategy:
1. Split synthetic training data for validation (holdout method)
2. Train multiple model configurations
3. Validate with MSE on held-out synthetic data
4. Run full pipeline (pairwise + clustering) for each config on real datasets
5. Calculate discrimination metrics: probability spread, confidence separation
6. Compare clustering results against expert knowledge
7. Select best configuration balancing accuracy (MSE) and discrimination (separation)

Discrimination Metrics:
- MSE: Prediction accuracy on synthetic validation data
- Probability Spread: Standard deviation of all match probabilities (higher = better differentiation)
- Confidence Separation: Mean(high prob) - Mean(low prob) (higher = clearer decision boundaries)
- High/Medium/Low Counts: Distribution of matches by probability ranges

Note: Real datasets (A, B, C) are NEVER used for training, only inference validation
"""

import pandas as pd
import numpy as np
import sys
import os

# Robustly add project root to sys.path to allow importing Feature_Engineer
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.abspath(os.path.join(current_dir, '../../'))
if project_root not in sys.path:
    sys.path.append(project_root)

from xgboost import XGBRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error
from Feature_Engineer import extract_features, generate_synthetic_training_data, parse_json_datasets

# ==========================================
# Configuration: Parameter Grid
# ==========================================
# Define candidate configurations to test
# Baseline is current: n_estimators=1000, max_depth=10, learning_rate=0.05
# Hyperparameter meanings (concise):
# - n_estimators: number of boosting trees; higher can fit harder patterns but increases time
# - max_depth: maximum tree depth; higher depth captures complex interactions but can overfit
# - learning_rate: shrinkage per tree; lower rate needs more trees but can improve stability
# - reg_lambda: L2 regularization weight on leaf scores; higher values discourage overfitting

parameter_configurations = [
    {
        'name': 'Baseline (Current)',
        'n_estimators': 1000,
        'max_depth': 10,
        'learning_rate': 0.05,
        'reg_lambda': 1.0
    },
    {
        'name': 'Conservative (Shallow)',
        'n_estimators': 500,
        'max_depth': 5,
        'learning_rate': 0.05,
        'reg_lambda': 1.0
    },
    {
        'name': 'Aggressive (Deep)',
        'n_estimators': 1000,
        'max_depth': 15,
        'learning_rate': 0.05,
        'reg_lambda': 1.0
    },
    {
        'name': 'Slow Learner (High Regularization)',
        'n_estimators': 2000,
        'max_depth': 10,
        'learning_rate': 0.01,
        'reg_lambda': 5.0
    },
    {
        'name': 'Fast Learner (Low Regularization)',
        'n_estimators': 500,
        'max_depth': 10,
        'learning_rate': 0.1,
        'reg_lambda': 0.5
    }
]

# Thresholds for inference and clustering (same as Level_Matcher.py)
pairwise_output_threshold = 0.01
clustering_merge_threshold = 0.15


def train_and_validate_model(config):
    """
    Train model with given configuration and compute validation metrics.
    Returns trained model and Mean Squared Error validation score.
    """
    print(f"\n{'='*60}")
    print(f"Testing Configuration: {config['name']}")
    print(f"{'='*60}")
    print(f"Parameters: n_estimators={config['n_estimators']}, max_depth={config['max_depth']}, "
          f"learning_rate={config['learning_rate']}, reg_lambda={config['reg_lambda']}")
    
    # Generate synthetic training data
    training_features, training_labels = generate_synthetic_training_data()
    
    # Split into train and validation sets (80/20 split)
    train_features, validation_features, train_labels, validation_labels = train_test_split(
        training_features, training_labels, test_size=0.2, random_state=42
    )
    
    # Train model with current configuration
    model = XGBRegressor(
        objective='binary:logistic',
        monotone_constraints='(1, 1, 1, 1, 1)',  # Energy, Spin, Parity, Specificity, Gamma
        random_state=42,
        n_estimators=config['n_estimators'],
        max_depth=config['max_depth'],
        learning_rate=config['learning_rate'],
        reg_lambda=config['reg_lambda']
    )
    
    model.fit(train_features, train_labels)
    
    # Compute validation metrics
    validation_predictions = model.predict(validation_features)
    
    # Note: training_labels are probabilities (0.0-1.0), not binary classes
    # Mean Squared Error is appropriate for regression
    mean_squared_error_value = mean_squared_error(validation_labels, validation_predictions)
    
    print(f"  Validation Mean Squared Error: {mean_squared_error_value:.4f}")
    
    return model, mean_squared_error_value


def run_inference_and_clustering(model, config_name):
    """
    Run full pairwise inference and clustering pipeline with trained model.
    Returns clustering results and matching pairs for expert validation.
    """
    print(f"\n  Running inference and clustering for: {config_name}")
    
    # Load real test datasets
    all_levels = parse_json_datasets([
        'data/raw/test_dataset_A.json',
        'data/raw/test_dataset_B.json',
        'data/raw/test_dataset_C.json'
    ])
    dataframe = pd.DataFrame(all_levels)
    
    # Pairwise inference
    matching_level_pairs = []
    level_list = list(dataframe.iterrows())
    
    for index_1 in range(len(level_list)):
        for index_2 in range(index_1 + 1, len(level_list)):
            _, level_1 = level_list[index_1]
            _, level_2 = level_list[index_2]
            
            if level_1['dataset_code'] == level_2['dataset_code']:
                continue
            
            feature_vector = extract_features(level_1, level_2)
            match_probability = model.predict([feature_vector])[0]
            
            if match_probability > pairwise_output_threshold:
                matching_level_pairs.append({
                    'level_id_1': level_1['level_id'],
                    'level_id_2': level_2['level_id'],
                    'dataset_code_1': level_1['dataset_code'],
                    'dataset_code_2': level_2['dataset_code'],
                    'probability': match_probability,
                    'features': feature_vector
                })
    
    matching_level_pairs.sort(key=lambda x: x['probability'], reverse=True)
    
    # Clustering (simplified version - just count clusters)
    level_lookup = {row['level_id']: row for _, row in dataframe.iterrows()}
    initial_clusters = [{row['dataset_code']: row['level_id']} for _, row in dataframe.iterrows()]
    
    level_id_to_clusters = {}
    for cluster in initial_clusters:
        member_id = list(cluster.values())[0]
        level_id_to_clusters[member_id] = [cluster]
    
    valid_pairs = set()
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] >= clustering_merge_threshold:
            valid_pairs.add((matching_level_pair['level_id_1'], matching_level_pair['level_id_2']))
            valid_pairs.add((matching_level_pair['level_id_2'], matching_level_pair['level_id_1']))
    
    # Greedy merging (same logic as Level_Clusterer.py)
    for matching_level_pair in matching_level_pairs:
        if matching_level_pair['probability'] < clustering_merge_threshold:
            break
        
        level_id_1, level_id_2 = matching_level_pair['level_id_1'], matching_level_pair['level_id_2']
        cluster_list_1 = list(level_id_to_clusters[level_id_1])
        cluster_list_2 = list(level_id_to_clusters[level_id_2])
        pair_processed = False
        
        for cluster_1 in cluster_list_1:
            for cluster_2 in cluster_list_2:
                if cluster_1 is cluster_2:
                    pair_processed = True
                    continue
                
                datasets_1 = set(cluster_1.keys())
                datasets_2 = set(cluster_2.keys())
                
                if not datasets_1.isdisjoint(datasets_2):
                    if matching_level_pair['dataset_code_1'] not in datasets_2:
                        if all((level_id_1, member_id) in valid_pairs for member_id in cluster_2.values()):
                            if level_id_1 not in cluster_2.values():
                                cluster_2[matching_level_pair['dataset_code_1']] = level_id_1
                                if cluster_2 not in level_id_to_clusters[level_id_1]:
                                    level_id_to_clusters[level_id_1].append(cluster_2)
                                pair_processed = True
                    
                    if matching_level_pair['dataset_code_2'] not in datasets_1:
                        if all((level_id_2, member_id) in valid_pairs for member_id in cluster_1.values()):
                            if level_id_2 not in cluster_1.values():
                                cluster_1[matching_level_pair['dataset_code_2']] = level_id_2
                                if cluster_1 not in level_id_to_clusters[level_id_2]:
                                    level_id_to_clusters[level_id_2].append(cluster_1)
                                pair_processed = True
                    continue
                
                consistent = True
                for member_1 in cluster_1.values():
                    for member_2 in cluster_2.values():
                        if (member_1, member_2) not in valid_pairs:
                            consistent = False
                            break
                    if not consistent:
                        break
                
                if consistent:
                    cluster_1.update(cluster_2)
                    for member_id in cluster_2.values():
                        if cluster_2 in level_id_to_clusters[member_id]:
                            level_id_to_clusters[member_id].remove(cluster_2)
                        if cluster_1 not in level_id_to_clusters[member_id]:
                            level_id_to_clusters[member_id].append(cluster_1)
                    pair_processed = True
        
        if not pair_processed:
            for cluster in level_id_to_clusters[level_id_1]:
                if len(cluster) == 1 and matching_level_pair['dataset_code_2'] not in cluster:
                    cluster[matching_level_pair['dataset_code_2']] = level_id_2
                    if cluster not in level_id_to_clusters[level_id_2]:
                        level_id_to_clusters[level_id_2].append(cluster)
                    pair_processed = True
                    break
            
            if not pair_processed:
                for cluster in level_id_to_clusters[level_id_2]:
                    if len(cluster) == 1 and matching_level_pair['dataset_code_1'] not in cluster:
                        cluster[matching_level_pair['dataset_code_1']] = level_id_1
                        if cluster not in level_id_to_clusters[level_id_1]:
                            level_id_to_clusters[level_id_1].append(cluster)
                        pair_processed = True
                        break
            
            if not pair_processed:
                new_cluster = {
                    matching_level_pair['dataset_code_1']: level_id_1,
                    matching_level_pair['dataset_code_2']: level_id_2
                }
                level_id_to_clusters[level_id_1].append(new_cluster)
                level_id_to_clusters[level_id_2].append(new_cluster)
    
    # Extract unique clusters
    unique_clusters = []
    seen = set()
    for cluster_list in level_id_to_clusters.values():
        for cluster in cluster_list:
            cluster_id = id(cluster)
            if cluster_id not in seen:
                unique_clusters.append(cluster)
                seen.add(cluster_id)
    
    # Calculate average cluster size
    cluster_sizes = [len(cluster) for cluster in unique_clusters]
    average_cluster_size = sum(cluster_sizes) / len(cluster_sizes) if cluster_sizes else 0
    multi_member_clusters = [c for c in unique_clusters if len(c) >= 2]
    
    print(f"    Total Clusters: {len(unique_clusters)}")
    print(f"    Multi-member Clusters (size >= 2): {len(multi_member_clusters)}")
    print(f"    Average Cluster Size: {average_cluster_size:.2f}")
    print(f"    Total High-confidence Pairs (>= {clustering_merge_threshold}): {len(valid_pairs)//2}")
    
    # Calculate additional discriminating metrics to differentiate hyperparameters
    probability_list = [pair['probability'] for pair in matching_level_pairs]
    high_prob_pairs = [p for p in probability_list if p >= 0.7]  # Strong matches
    medium_prob_pairs = [p for p in probability_list if 0.3 <= p < 0.7]  # Ambiguous
    low_prob_pairs = [p for p in probability_list if 0.15 <= p < 0.3]  # Weak matches
    
    probability_spread = np.std(probability_list) if probability_list else 0
    confidence_separation = np.mean(high_prob_pairs) - np.mean(low_prob_pairs) if high_prob_pairs and low_prob_pairs else 0
    
    return {
        'total_clusters': len(unique_clusters),
        'multi_member_clusters': len(multi_member_clusters),
        'average_cluster_size': average_cluster_size,
        'high_confidence_pairs': len(valid_pairs)//2,
        'clusters': unique_clusters,
        'level_lookup': level_lookup,
        'matching_pairs': matching_level_pairs,
        'probability_spread': probability_spread,
        'confidence_separation': confidence_separation,
        'high_prob_count': len(high_prob_pairs),
        'medium_prob_count': len(medium_prob_pairs),
        'low_prob_count': len(low_prob_pairs)
    }


def save_clustering_results(config_name, clustering_results, model):
    """
    Save detailed clustering results to file for expert review.
    Includes match probabilities relative to anchor (like Level_Matcher.py).
    """
    base_dir = os.path.dirname(os.path.abspath(__file__))
    filename = os.path.join(base_dir, f"Output_Hyperparameter_Test_{config_name.replace(' ', '_')}.txt")
    
    with open(filename, 'w', encoding='utf-8') as output_file:
        output_file.write(f"=== CLUSTERING RESULTS: {config_name} ===\n")
        output_file.write(f"Total Clusters: {clustering_results['total_clusters']}\n")
        output_file.write(f"Multi-member Clusters: {clustering_results['multi_member_clusters']}\n\n")
        
        for i, cluster in enumerate(clustering_results['clusters']):
            if len(cluster) >= 2:  # Only output multi-member clusters
                # Select anchor as the level with smallest energy uncertainty (most precise measurement)
                anchor_id = min(cluster.values(), 
                               key=lambda x: clustering_results['level_lookup'][x].get('energy_uncertainty', 999))
                anchor_data = clustering_results['level_lookup'][anchor_id]
                anchor_energy = anchor_data['energy_value']
                anchor_uncertainty = anchor_data.get('energy_uncertainty', 0)
                anchor_spin_parity = anchor_data.get('spin_parity_string', 'N/A')
                if anchor_spin_parity == "unknown": anchor_spin_parity = "N/A"
                
                output_file.write(f"\nCluster {i+1}:\n")
                output_file.write(f"  Anchor: {anchor_id} | E={anchor_energy:.1f}±{anchor_uncertainty:.1f} keV | Jπ={anchor_spin_parity}\n")
                output_file.write(f"  Members:\n")
                
                for dataset_code in sorted(cluster.keys()):
                    member_id = cluster[dataset_code]
                    member_data = clustering_results['level_lookup'][member_id]
                    energy = member_data['energy_value']
                    uncertainty = member_data.get('energy_uncertainty', 0)
                    spin_parity = member_data.get('spin_parity_string', 'N/A')
                    if spin_parity == "unknown": spin_parity = "N/A"
                    
                    if member_id == anchor_id:
                        output_file.write(f"    [{dataset_code}] {member_id}: E={energy:.1f}±{uncertainty:.1f} keV, Jπ={spin_parity} (Anchor)\n")
                    else:
                        # Calculate match probability relative to anchor
                        input_vector = extract_features(member_data, anchor_data)
                        probability = model.predict([input_vector])[0]
                        output_file.write(f"    [{dataset_code}] {member_id}: E={energy:.1f}±{uncertainty:.1f} keV, Jπ={spin_parity} (Match Probability: {probability:.1%})\n")
    
    print(f"    Saved detailed results to: {filename}")


def save_pairwise_results(config_name, matching_level_pairs):
    """
    Save pairwise inference results with match probabilities to file for expert review.
    """
    base_dir = os.path.dirname(os.path.abspath(__file__))
    filename = os.path.join(base_dir, f"Output_Hyperparameter_Pairwise_{config_name.replace(' ', '_')}.txt")
    
    with open(filename, 'w', encoding='utf-8') as output_file:
        output_file.write(f"=== PAIRWISE INFERENCE RESULTS: {config_name} ===\n")
        output_file.write(f"Total Level Pairs: {len(matching_level_pairs)}\n\n")
        
        for matching_pair in matching_level_pairs:
            energy_similarity, spin_similarity, parity_similarity, specificity, gamma_similarity = matching_pair['features']
            output_file.write(
                f"{matching_pair['level_id_1']} <-> {matching_pair['level_id_2']} | "
                f"Probability: {matching_pair['probability']:.1%}\n"
                f"  Features: Energy_Similarity={energy_similarity:.2f}, Spin_Similarity={spin_similarity:.2f}, "
                f"Parity_Similarity={parity_similarity:.2f}, Specificity={specificity:.2f}, Gamma_Similarity={gamma_similarity:.2f}\n\n"
            )
    
    print(f"    Saved pairwise results to: {filename}")


# ==========================================
# Main Tuning Loop
# ==========================================
if __name__ == "__main__":
    print("\n" + "="*60)
    print("HYPERPARAMETER TUNING FOR NUCLEAR LEVEL MATCHER")
    print("="*60)
    print("\nStrategy:")
    print("  1. Validate on synthetic data split (Mean Squared Error regression metric)")
    print("  2. Run full inference + clustering on real datasets A, B, C")
    print("  3. Compare clustering results with your expert knowledge")
    print("  4. Select best configuration based on metrics + expert validation")
    
    results_summary = []
    
    for config in parameter_configurations:
        # Train and validate on synthetic data
        model, mean_squared_error_value = train_and_validate_model(config)
        
        # Run inference and clustering on real datasets
        clustering_results = run_inference_and_clustering(model, config['name'])
        
        # Save detailed clustering for expert review (with probabilities)
        save_clustering_results(config['name'], clustering_results, model)
        
        # Save pairwise inference results with match probabilities
        save_pairwise_results(config['name'], clustering_results['matching_pairs'])
        
        # Store summary results with expanded metrics
        results_summary.append({
            'name': config['name'],
            'mean_squared_error': mean_squared_error_value,
            'total_clusters': clustering_results['total_clusters'],
            'multi_member_clusters': clustering_results['multi_member_clusters'],
            'average_cluster_size': clustering_results['average_cluster_size'],
            'high_confidence_pairs': clustering_results['high_confidence_pairs'],
            'probability_spread': clustering_results['probability_spread'],
            'confidence_separation': clustering_results['confidence_separation'],
            'high_prob_count': clustering_results['high_prob_count'],
            'medium_prob_count': clustering_results['medium_prob_count'],
            'low_prob_count': clustering_results['low_prob_count']
        })

    
    # Print expanded comparison table
    print("\n" + "="*100)
    print("SUMMARY: HYPERPARAMETER COMPARISON (EXPANDED TEST CASES)")
    print("="*100)
    print(f"\n{'Configuration':<40} {'MSE':<8} {'Clusters':<10} {'Multi':<8} {'AvgSize':<10}")
    print("-" * 100)
    
    for result in results_summary:
        print(f"{result['name']:<40} {result['mse']:<8.4f} "
              f"{result['total_clusters']:<10} {result['multi_member_clusters']:<8} {result['average_cluster_size']:<10.2f}")
    
    print("\n" + "-" * 100)
    print(f"\n{'Configuration':<40} {'High(>70%)':<12} {'Med(30-70%)':<14} {'Low(15-30%)':<14} {'Spread':<10} {'Separation':<12}")
    print("-" * 100)
    
    for result in results_summary:
        print(f"{result['name']:<40} {result['high_prob_count']:<12} "
              f"{result['medium_prob_count']:<14} {result['low_prob_count']:<14} "
              f"{result['probability_spread']:<10.4f} {result['confidence_separation']:<12.4f}")
    
    # Identify best by multiple criteria
    best_by_mse = min(results_summary, key=lambda x: x['mse'])
    best_by_separation = max(results_summary, key=lambda x: x['confidence_separation'])
    best_by_spread = max(results_summary, key=lambda x: x['probability_spread'])
    
    print("\n" + "="*100)
    print("RECOMMENDATION ANALYSIS")
    print("="*100)
    print(f"\nBest by Validation MSE: {best_by_mse['name']} (MSE: {best_by_mse['mse']:.4f})")
    print(f"  → Lowest prediction error on synthetic validation data")
    
    print(f"\nBest by Confidence Separation: {best_by_separation['name']} (Sep: {best_by_separation['confidence_separation']:.4f})")
    print(f"  → Best discrimination between high-confidence and low-confidence matches")
    
    print(f"\nBest by Probability Spread: {best_by_spread['name']} (Spread: {best_by_spread['probability_spread']:.4f})")
    print(f"  → Widest range of probabilities, better differentiation between cases")
    
    print("\nNext Steps:")
    print("  1. Review detailed clustering files: Output_Hyperparameter_Test_*.txt")
    print("  2. Review pairwise probabilities: Output_Hyperparameter_Pairwise_*.txt")
    print("  3. Compare with your expert knowledge of correct clusters")
    print("  4. Consider all metrics: MSE measures accuracy, Separation measures discrimination")
    print("  5. Select configuration that best balances accuracy and discrimination")
    print("  6. Update Level_Matcher.py parameters with chosen configuration")
