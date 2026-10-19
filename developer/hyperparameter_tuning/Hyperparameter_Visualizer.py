"""Hyperparameter Tuning Results Visualizer

This script generates five separate PNG visualizations comparing clustering results from 
different hyperparameter configurations. Each figure shows one configuration's 
clustering output aligned horizontally by cluster.

Explanation of Code Structure
1) Parse each hyperparameter test output file into cluster data structures.
2) Compute collision-resolved y-positions for clusters using spread_text_positions.
3) Generate individual figure per configuration with shared x-axis (datasets A, B, C).
4) Plot cluster members with energy/Jπ/probability labels, aligned horizontally by cluster.
5) Save separate PNG file for each configuration for expert review.
"""

import os
import numpy as np
import matplotlib.pyplot as plt

# Tunable layout parameters (matching Combined_Visualizer.py)
clustering_box_padding = 1.0                # Box padding around text labels
clustering_minimum_distance = 500           # Minimum vertical spacing between cluster rows
clustering_figure_height_multiplier = 1.0  # Height scaling factor
clustering_figure_width_inches = 10.0      # Overall figure width in inches
clustering_x_spacing = 0.45             # Horizontal distance between dataset columns
clustering_x_axis_margin = 0.3               # Extra blank space padding on left/right

def spread_text_positions(energies, minimum_distance=500):
    """
    Adjusts text positions to ensure they are at least minimum_distance apart vertically.
    Uses iterative relaxation to push apart overlapping labels.
    """
    if not energies:
        return np.array([])
    
    number_of_positions = len(energies)
    positions = np.array(energies, dtype=float)
    
    # Relaxation iterations to push apart overlapping labels
    for iteration in range(200):
        moved = False
        for i in range(number_of_positions - 1):
            distance = positions[i + 1] - positions[i]
            if distance < minimum_distance:
                overlap = minimum_distance - distance
                positions[i] -= overlap / 2
                positions[i + 1] += overlap / 2
                moved = True
        if not moved:
            break
    
    return positions

def parse_clustering_results(clustering_file_path):
    """Parse hyperparameter test output file to extract cluster information."""
    clusters = []
    current_cluster = None
    cluster_counter = 0
    anchor_id = None
    in_members_section = False
    
    with open(clustering_file_path, 'r', encoding='utf-8') as file_handle:
        for line in file_handle:
            line = line.strip()
            
            # Detect cluster header: "Cluster N:"
            if line.startswith('Cluster ') and ':' in line and 'Anchor' not in line:
                if current_cluster is not None:
                    clusters.append(current_cluster)
                
                cluster_counter += 1
                
                current_cluster = {
                    'cluster_number': cluster_counter,
                    'size': 0,
                    'members': [],
                    'anchor_energy': None
                }
                anchor_id = None
                in_members_section = False
            
            # Detect anchor line: "  Anchor: A_1000 | E=1000.0±1.0 keV | Jπ=N/A"
            elif line.startswith('Anchor:') and current_cluster is not None:
                anchor_id = line.split('Anchor:')[1].split('|')[0].strip()
                in_members_section = False
            
            # Detect members section header
            elif line == 'Members:' and current_cluster is not None:
                in_members_section = True
            
            # Parse member line: "    [A] A_1000: E=1000.0±1.0 keV, Jπ=N/A (Anchor)"
            elif line.startswith('[') and current_cluster is not None and in_members_section:
                # Extract dataset code
                dataset_code = line[1:2]  # Extract 'A', 'B', or 'C'
                
                # Extract energy value
                energy_part = line.split('E=')[1].split('±')[0]
                energy = float(energy_part)
                
                # Extract uncertainty
                uncertainty_part = line.split('±')[1].split(' keV')[0]
                uncertainty = float(uncertainty_part)
                
                # Extract spin-parity value
                if 'Jπ=' in line:
                    spin_parity_part = line.split('Jπ=')[1]
                    # Split on '(' to get Jπ before the anchor/probability marker
                    spin_parity = spin_parity_part.split('(')[0].strip().rstrip(',')
                else:
                    spin_parity = 'N/A'
                
                # Extract probability or anchor marker
                is_anchor = '(Anchor)' in line
                match_probability = None
                
                if not is_anchor and 'Match Probability:' in line:
                    # Extract percentage: "Match Probability: 76.9%)"
                    probability_string = line.split('Match Probability:')[1].strip().rstrip(')')
                    match_probability = float(probability_string.rstrip('%')) / 100.0
                
                # Store member information
                current_cluster['members'].append({
                    'dataset': dataset_code,
                    'energy': energy,
                    'uncertainty': uncertainty,
                    'spin_parity': spin_parity,
                    'is_anchor': is_anchor,
                    'match_probability': match_probability
                })
                
                current_cluster['size'] += 1
                
                # Set anchor energy
                if current_cluster['anchor_energy'] is None or energy < current_cluster['anchor_energy']:
                    current_cluster['anchor_energy'] = energy
    
    # Add final cluster
    if current_cluster is not None:
        clusters.append(current_cluster)
    
    return clusters

def plot_single_configuration(axis, clusters, config_name):
    """
    Plot clustering results for a single hyperparameter configuration on given axis.
    Aligned horizontally by cluster, sorted vertically by energy.
    Exactly matches Combined_Visualizer.py style.
    """
    if not clusters:
        axis.text(0.5, 0.5, 'No clusters found', ha='center', va='center', fontsize=14)
        return
    
    # Sort clusters by anchor energy (low energy at bottom)
    clusters.sort(key=lambda x: x.get('anchor_energy', 0))
    
    datasets = ['A', 'B', 'C']
    
    # X-axis positions
    x_positions = {ds: index * clustering_x_spacing for index, ds in enumerate(datasets)}
    
    # Y-Position Calculation: use anchor energies as base, then spread to prevent overlap
    anchor_energies = [c.get('anchor_energy', 0) for c in clusters]
    y_positions = spread_text_positions(anchor_energies, minimum_distance=clustering_minimum_distance)
    
    if len(y_positions) > 0:
        y_max_limit = max(y_positions) + 500
    else:
        y_max_limit = 1000
    
    # Plot text for each cluster
    for i, cluster in enumerate(clusters):
        y_pos = y_positions[i]
        cluster_id = cluster['cluster_number']
        
        # Plot members
        for member in cluster['members']:
            ds = member['dataset']
            if ds not in x_positions:
                continue
            
            x_pos = x_positions[ds]
            
            # Text Content: Format matching Combined_Visualizer
            # 'Energy(Unc)', 'Jpi', 'Probability', 'ClusterID'
            energy_string = f"{member['energy']:.0f}({int(member['uncertainty'])})"
            spin_parity_string = member['spin_parity']
            
            if member['is_anchor']:
                probability_string = "Anchor"
            elif member.get('match_probability') is not None:
                probability_string = f"{member['match_probability']:.1%}"
            else:
                probability_string = "N/A"
            
            # Combine into block - 2 lines for wider, shorter box
            text_block = (
                f"C{cluster_id} | {energy_string}\n"
                f"{spin_parity_string} | {probability_string}"
            )
            
            axis.text(x_pos, y_pos, text_block,
                     ha='center', va='center',
                     fontsize=12, family='Times New Roman',
                     bbox=dict(boxstyle=f"round,pad={clustering_box_pad}", fc="white", ec="gray", alpha=0.9))
    
    # Determine y-axis limits
    axis.set_ylim(-200, y_max_limit)
    right_limit = (len(datasets) - 1) * clustering_x_spacing + clustering_x_margin
    left_limit = -clustering_x_margin
    axis.set_xlim(left_limit, right_limit)
    
    # X-axis labels
    axis.set_xticks([index * clustering_x_spacing for index in range(len(datasets))])
    
    # Y-axis label
    axis.set_ylabel("Energy / Cluster Index (Spread)", fontsize=16, family='Times New Roman')
    
    # Styling: Remove spines for clean table look
    axis.spines['top'].set_visible(False)
    axis.spines['right'].set_visible(False)
    axis.spines['bottom'].set_visible(False)
    axis.spines['left'].set_visible(False)
    
    axis.tick_params(left=True, bottom=False, labelsize=14)
    for label in axis.get_yticklabels():
        label.set_family('Times New Roman')
    
    # Title for this configuration
    axis.set_title("Clustering Results (Aligned by Cluster)", fontsize=20, fontweight='bold', pad=20, family='Times New Roman')

def plot_all_hyperparameter_results():
    """
    Generates five separate PNG files, one for each hyperparameter configuration.
    Each figure shows clustering results aligned horizontally by cluster.
    Matches Combined_Visualizer.py figure size and style exactly.
    """
    # Define all five configuration files
    base_dir = os.path.dirname(os.path.abspath(__file__))
    configurations = [
        ('Baseline (Current)', os.path.join(base_dir, 'Output_Hyperparameter_Test_Baseline_(Current).txt')),
        ('Conservative (Shallow)', os.path.join(base_dir, 'Output_Hyperparameter_Test_Conservative_(Shallow).txt')),
        ('Aggressive (Deep)', os.path.join(base_dir, 'Output_Hyperparameter_Test_Aggressive_(Deep).txt')),
        ('Slow Learner (High Regularization)', os.path.join(base_dir, 'Output_Hyperparameter_Test_Slow_Learner_(High_Regularization).txt')),
        ('Fast Learner (Low Regularization)', os.path.join(base_dir, 'Output_Hyperparameter_Test_Fast_Learner_(Low_Regularization).txt'))
    ]
    
    # Create separate figure for each configuration
    for config_name, file_path in configurations:
        if not os.path.exists(file_path):
            print(f"[WARNING] File not found: {file_path}")
            continue
        
        # Parse clustering results
        clusters = parse_clustering_results(file_path)
        
        # Calculate figure size based on density (matching Combined_Visualizer)
        fig_height = max(8, len(clusters) * clustering_fig_height_multiplier)
        
        # Create individual figure
        figure, axis = plt.subplots(figsize=(clustering_fig_width_inches, fig_height))
        
        # Plot this configuration
        plot_single_configuration(axis, clusters, config_name)
        
        # Add x-axis labels
        axis.set_xticklabels(['Dataset A', 'Dataset B', 'Dataset C'], 
                            fontsize=16, fontweight='bold', family='Times New Roman')
        
        plt.tight_layout()
        
        # Save individual PNG
        safe_name = config_name.replace(' ', '_').replace('(', '').replace(')', '')
        output_file = os.path.join(base_dir, f'Output_Hyperparameter_Visualization_{safe_name}.png')
        plt.savefig(output_file, dpi=300)
        print(f"[INFO] Saved {output_file}")
        plt.close()

# ============================================================================
# MAIN EXECUTION
# ============================================================================

if __name__ == "__main__":
    print("=" * 60)
    print("Hyperparameter Visualizer: Comparing all configurations...")
    print("=" * 60)
    
    plot_all_hyperparameter_results()
    
    print("=" * 60)
    print("Visualization complete!")
    print("=" * 60)
