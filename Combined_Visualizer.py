"""
Combined Level Scheme and Clustering Visualizer
======================================

# High-level Structure and Workflow Explanation:
======================================

Workflow Diagram:
[Start]
   |
   v
[Input: JSON Levels & Clustering Results]
   |
   v
[Step 1: Data Parsing] --> [Clusters & Members]
   |
   v
[Step 2: Geometry Engine] --> [Collision Resolution]
   |                                  |
   |                                  v
   |                  [Iterative Label Spreading (Relaxation)]
   v
[Step 3: Rendering] --> [Draw Levels (True Energy)]
   |                              |
   v                              v
[Align Clusters (Vertical Axis)] --> [Connect Labels]
   |
   v
[End: Output Scheme Images]

Numbered Technical Steps:
1. **Data Ingestion**: Parses standardized JSON level data and Machine Learning clustering output logs.
2. **Collision Resolution**: Applies iterative relaxation to spread overlapping energy and Spin-Parity labels.
3. **Geometry Calculation**: Computes vertical alignment indices for clusters to ensure horizontal consensus visibility.
4. **Rendering**: Uses Matplotlib to generate high-fidelity physical level schemes with gamma transition arrows.

Architecture:
- `spread_text_positions`: Iterative relaxation algorithm for label spacing.
- `plot_level_schemes`: Main rendering engine for input datasets (dataset list imported from Level_Matcher.py).
- `plot_clustering_results`: Visualization engine for reconciled physical levels.
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np
import json
import os
import re

# Import the inference dataset list from the single source of truth (Level_Matcher.py user configuration).
# This keeps the visualizer aligned with whatever datasets were last matched, with no second place to edit.
from Level_Matcher import inference_dataset_labels

# Tunable clustering layout knobs for quick manual adjustments
clustering_box_padding = 1.0                # Box padding; increase for wider/taller boxes
clustering_minimum_distance = 500           # Minimum vertical spacing between rows
clustering_figure_height_multiplier = 1.0  # Height scaling; raise to add vertical breathing room
clustering_figure_width_inches = 10.0      # Overall figure width in inches; shrink to reduce horizontal scale
clustering_x_spacing = 0.45             # Horizontal distance between dataset columns (reduce for tighter columns)
clustering_x_axis_margin = 0.3               # Extra blank space padding on left/right of the outer columns

# ============================================================================
# Font Configuration
# ============================================================================
Font_Config = {
    # Level Scheme Visualizer
    'level_labels': 8,        # Energy and Spin labels next to bars (small for dense datasets)
    'gamma_labels': 6,        # Branching ratio labels on arrows
    'axis_labels': 20,        # X and Y axis labels (Dataset Names, "Energy (keV)")
    'tick_labels': 16,        # Axis tick values
    'title': 20,              # Main figure title

    # Clustering Visualizer
    'cluster_text': 12,       # Text inside cluster boxes
    'cluster_axis_labels': 16,# X and Y axis labels
    'cluster_tick_labels': 14,# Axis tick values
    'cluster_title': 20,      # Main figure title
}

# ============================================================================
# SHARED UTILITY FUNCTIONS
# ============================================================================

def spread_text_positions(energies, minimum_distance=250):
    """
    Adjusts text positions to ensure they are at least minimum_distance apart vertically.
    Collision resolution algorithm using iterative relaxation.
    """
    if not energies:
        return []
    
    number_of_positions = len(energies)
    positions = np.array(energies, dtype=float)
    
    # Relaxation iterations to push apart overlapping labels
    for iteration in range(200):
        changed = False
        for index in range(number_of_positions - 1):
            distance = positions[index + 1] - positions[index]
            if distance < minimum_distance:
                # Overlap detected: push levels apart
                center = (positions[index + 1] + positions[index]) / 2.0
                overlap = minimum_distance - distance
                positions[index] -= overlap / 2.0
                positions[index + 1] += overlap / 2.0
                changed = True
        if not changed:
            break
    
    return positions

# ============================================================================
# LEVEL SCHEME VISUALIZER (INPUT DATA)
# ============================================================================

def load_dataset(dataset_code):
    """Load level data and gamma table from JSON file for a given dataset code."""
    filename = f"data/json/test_dataset_{dataset_code}.json"
    if not os.path.exists(filename):
        filename = f"data/raw/test_dataset_{dataset_code}.json"
    if not os.path.exists(filename):
        return [], []
    
    with open(filename, 'r', encoding='utf-8') as file_handle:
        data = json.load(file_handle)
        if isinstance(data, dict) and 'levelsTable' in data:
            levels = data['levelsTable'].get('levels', [])
            gammas = data.get('gammasTable', {}).get('gammas', [])
            return levels, gammas
        elif isinstance(data, list):
            return data, []
    return [], []

def plot_level_schemes():
    """Generate visualization of input level schemes from all datasets with gamma transitions.

    The dataset list is imported from Level_Matcher.py (inference_dataset_labels) so this
    visualizer always renders exactly the datasets that were selected for inference.
    """
    datasets = inference_dataset_labels

    # Pre-load all datasets to count levels for proportional figure sizing
    preloaded_data = {code: load_dataset(code) for code in datasets}
    maximum_level_count = max(
        (len([level for level in preloaded_data[code][0]
              if level.get('energy') is not None or level.get('energy_value') is not None])
         for code in datasets),
        default=100
    )
    # Scale figure height proportionally: 0.12 inches per level, minimum 20 inches
    figure_height = max(20, maximum_level_count * 0.12)
    # Scale figure width with the number of dataset columns (16 inches for two columns)
    figure_width = max(10, len(datasets) * 8)
    figure, axis = plt.subplots(figsize=(figure_width, figure_height))

    # X-axis positions for each dataset column (wider spacing to accommodate dense labels)
    dataset_column_spacing = 5.0
    x_positions = {code: index * dataset_column_spacing for index, code in enumerate(datasets)}
    line_width = 0.8
    maximum_energy = 0
    maximum_label_position = 0  # Track the highest spread label position for ylim
    
    for dataset_code in datasets:
        raw_levels, gammas_table = preloaded_data[dataset_code]
        
        # Extract level data
        levels_data = []
        for level in raw_levels:
            # Energy
            if isinstance(level.get('energy'), dict):
                energy_value = level.get('energy', {}).get('value')
                uncertainty = level.get('energy', {}).get('uncertainty', {}).get('value')
            else:
                energy_value = level.get('energy_value')
                uncertainty = level.get('energy_uncertainty')
                
            if energy_value is None:
                continue
            energy_value = float(energy_value)
            
            # Spin/Parity
            spin_parity_string = ""
            if isinstance(level.get('spinParity'), dict):
                spin_parity_string = level.get('spinParity', {}).get('evaluatorInput', '')
            
            # Format level label for the reader (e.g. 600(1))
            # The JSON property 'evaluatorInput' uses NNDC space-separated format (e.g. 600 1)
            label_left = ""
            if isinstance(level.get('energy'), dict):
                evaluator_input = level['energy'].get('evaluatorInput', '')
                if ' ' in evaluator_input:
                    # Convert NNDC "Value Uncertainty" to scientific "Value(Uncertainty)" for the plot
                    value_part, uncertainty_part = evaluator_input.split(' ', 1)
                    label_left = f"{value_part}({uncertainty_part})"
                else:
                    label_left = evaluator_input
            else:
                uncertainty_string = f"({int(uncertainty)})" if uncertainty is not None else ""
                label_left = f"{int(energy_value)}{uncertainty_string}"
                
            label_right = spin_parity_string
            
            levels_data.append({
                'energy': energy_value,
                'label_left': label_left,
                'label_right': label_right
            })
            
            if energy_value > maximum_energy:
                maximum_energy = energy_value

        # Sort by energy
        levels_data.sort(key=lambda x: x['energy'])
        
        # Compute adaptive minimum label spacing from level density and font height
        # Term 1 (average gap): spreads labels evenly when levels are densely clustered
        # Term 2 (font guard): guarantees at least one label height of separation when the
        #                      energy range is large relative to the figure height
        energies = [x['energy'] for x in levels_data]
        energy_range = (max(energies) - min(energies)) if len(energies) > 1 else 1000
        average_gap_distance = energy_range / max(len(energies), 1)
        font_guard_distance = energy_range * 0.14 / figure_height
        adaptive_minimum_distance = max(30, average_gap_distance, font_guard_distance)
        text_y_positions = spread_text_positions(energies, minimum_distance=adaptive_minimum_distance)

        # Track highest label position across all datasets for ylim
        if len(text_y_positions) > 0:
            maximum_label_position = max(maximum_label_position, max(text_y_positions))
        
        # Plot levels (bars drawn at true energy positions; no artificial vertical offsets)
        x_center = x_positions[dataset_code]
        x_start = x_center - line_width / 2
        x_end = x_center + line_width / 2

        for index, item in enumerate(levels_data):
            energy = item['energy']
            y_text_position = text_y_positions[index]
            bar_y_position = energy

            # Thin bar: 0.5 pt linewidth separates closely spaced levels in dense regions
            axis.hlines(y=bar_y_position, xmin=x_start, xmax=x_end, colors='black', linewidth=0.5)
            
            # Only draw connector line when label displacement is significant (reduces clutter)
            is_displaced = abs(bar_y_position - y_text_position) > 200

            if is_displaced:
                # Energy label with thin connector
                axis.annotate(item['label_left'],
                            xy=(x_start, bar_y_position), xycoords='data',
                            xytext=(x_start - 0.2, y_text_position), textcoords='data',
                            arrowprops=dict(arrowstyle="-", color='gray', linewidth=0.4),
                            va='center', ha='right', fontsize=Font_Config['level_labels'], family='Times New Roman')

                # Spin/Parity label with thin connector
                if item['label_right']:
                    axis.annotate(item['label_right'],
                                xy=(x_end, bar_y_position), xycoords='data',
                                xytext=(x_end + 0.2, y_text_position), textcoords='data',
                                arrowprops=dict(arrowstyle="-", color='gray', linewidth=0.4),
                                va='center', ha='left', fontsize=Font_Config['level_labels'], family='Times New Roman')
            else:
                # Standard text placement
                axis.text(x_start - 0.1, y_text_position, item['label_left'], va='center', ha='right', fontsize=Font_Config['level_labels'], family='Times New Roman')
                if item['label_right']:
                    axis.text(x_end + 0.1, y_text_position, item['label_right'], va='center', ha='left', fontsize=Font_Config['level_labels'], family='Times New Roman')
        
        # Draw gamma transitions
        if gammas_table:
            # Collect all gammas with their positions for global arrow placement
            all_gamma_data = []
            
            for gamma_index, gamma in enumerate(gammas_table):
                initial_level_index = gamma.get('initialLevel')
                final_level_index = gamma.get('finalLevel')
                
                # Validate indices
                if initial_level_index is None or final_level_index is None:
                    continue
                if initial_level_index >= len(levels_data) or final_level_index >= len(levels_data):
                    continue
                if initial_level_index < 0 or final_level_index < 0:
                    continue
                
                # Get energy positions at true level energies
                initial_energy = levels_data[initial_level_index]['energy']
                final_energy = levels_data[final_level_index]['energy']
                
                # Get gamma properties
                gamma_energy = gamma.get('energy', {}).get('value', 0)
                gamma_intensity = gamma.get('gammaIntensity', {}).get('value', 100)
                
                all_gamma_data.append({
                    'gamma_index': gamma_index,
                    'initial_level_index': initial_level_index,
                    'final_level_index': final_level_index,
                    'initial_energy': initial_energy,
                    'final_energy': final_energy,
                    'gamma_energy': gamma_energy,
                    'gamma_intensity': gamma_intensity
                })
            
            # Calculate unique horizontal positions for ALL gammas in this dataset
            # Arrows should be positioned NEAR the right side of the bar, but still within reasonable bounds
            # Level bar extends from (x_center - 0.4) to (x_center + 0.4)
            # Place arrows starting close to center, spreading rightward within/near the bar

            # Limit to top 30 strongest gammas: 873 transitions in Dataset K would form a solid black
            # wedge if all drawn; showing only the dominant transitions is standard practice
            if len(all_gamma_data) > 30:
                all_gamma_data.sort(key=lambda x: x['gamma_intensity'], reverse=True)
                all_gamma_data = all_gamma_data[:30]

            num_total_gammas = len(all_gamma_data)

            # Constrain all gamma arrows to fit within bar width to prevent horizontal overflow
            # Maximum arrow spread = 85% of bar width (line_width); arrow_spacing capped at 0.04 units
            max_arrow_spread = line_width * 0.85
            if num_total_gammas > 1:
                arrow_spacing = min(0.04, max_arrow_spread / (num_total_gammas - 1))
                total_arrow_width = (num_total_gammas - 1) * arrow_spacing
                arrow_base_offset = -total_arrow_width / 2.0
            else:
                arrow_spacing = 0.0
                arrow_base_offset = 0.0

            for index, gamma_data in enumerate(all_gamma_data):
                arrow_x_position = x_center + arrow_base_offset + (index * arrow_spacing)
                
                initial_energy = gamma_data['initial_energy']
                final_energy = gamma_data['final_energy']
                gamma_intensity = gamma_data['gamma_intensity']
                
                # Draw vertical arrow from initial to final level
                axis.annotate('',
                            xy=(arrow_x_position, final_energy),
                            xytext=(arrow_x_position, initial_energy),
                            arrowprops=dict(arrowstyle='->', color='black', linewidth=0.5))

                # Only label gammas with intensity >= 10% to reduce visual noise in dense regions
                if gamma_intensity >= 10:
                    mid_energy = (initial_energy + final_energy) / 2.0
                    label_text = f"{int(gamma_intensity)}"
                    axis.text(arrow_x_position, mid_energy, label_text,
                             va='center', ha='center', fontsize=Font_Config['gamma_labels'],
                             family='Times New Roman',
                             bbox=dict(boxstyle='square,pad=0.1', facecolor='white', edgecolor='none', alpha=1.0))

    # Styling
    axis.set_xlim(-2.5, (len(datasets) - 1) * dataset_column_spacing + 2.5)
    # Extend ylim to the highest spread label position so no labels are clipped
    axis.set_ylim(-200, max(maximum_energy * 1.05, maximum_label_position * 1.03))
    axis.set_xticks([index * dataset_column_spacing for index in range(len(datasets))])
    axis.set_xticklabels([f'Dataset {code}' for code in datasets],
                         fontsize=Font_Config['axis_labels'], fontweight='bold', family='Times New Roman')
    
    axis.spines['top'].set_visible(False)
    axis.spines['right'].set_visible(False)
    axis.spines['bottom'].set_visible(False)
    axis.spines['left'].set_linewidth(1.5)
    
    axis.set_ylabel("Energy (keV)", fontsize=Font_Config['axis_labels'], family='Times New Roman')
    axis.tick_params(axis='x', length=0)
    axis.tick_params(axis='y', labelsize=Font_Config['tick_labels'])
    for label in axis.get_yticklabels():
        label.set_family('Times New Roman')
    
    axis.set_title("Input Level Schemes", fontsize=Font_Config['title'], fontweight='bold', pad=20, family='Times New Roman')
    
    plt.tight_layout()
    output_file = 'outputs/figures/Input_Level_Scheme.png'
    plt.savefig(output_file, dpi=300)
    print(f"[INFO] Level scheme visualization saved to {output_file}")
    plt.close()

# ============================================================================
# CLUSTERING VISUALIZER (OUTPUT RESULTS)
# ============================================================================

def parse_clustering_results(clustering_file_path):
    """Parse Output_Clustering_Results.txt to extract cluster information."""
    clusters = []
    current_cluster = None
    
    with open(clustering_file_path, 'r', encoding='utf-8') as file_handle:
        for line in file_handle:
            line = line.strip()
            
            # Match cluster header
            cluster_match = re.match(r'^Cluster (\d+):$', line)
            if cluster_match:
                if current_cluster is not None:
                    clusters.append(current_cluster)
                current_cluster = {
                    'cluster_number': int(cluster_match.group(1)),
                    'members': []
                }
                continue
            
            # Match anchor line
            anchor_match = re.match(r'^Anchor:\s+(\S+)\s+\|\s+E=([\d.]+)±([\d.]+)\s+keV\s+\|\s+Spin-Parity=(.+)$', line)
            if anchor_match and current_cluster is not None:
                current_cluster['anchor_id'] = anchor_match.group(1)
                current_cluster['anchor_energy'] = float(anchor_match.group(2))
                current_cluster['anchor_uncertainty'] = float(anchor_match.group(3))
                current_cluster['anchor_spin_parity'] = anchor_match.group(4)
                continue
            
            # Match member line
            member_match = re.match(r'^\[(\w+)\]\s+(\S+):\s+E=([\d.]+)±([\d.]+)\s+keV,\s+Spin-Parity=(.+?)\s+\((.+)\)$', line)
            if member_match and current_cluster is not None:
                dataset_name = member_match.group(1)
                level_id = member_match.group(2)
                energy = float(member_match.group(3))
                uncertainty = float(member_match.group(4))
                spin_parity_string = member_match.group(5).strip()
                status_info = member_match.group(6).strip()
                
                is_anchor = status_info == "Anchor"
                match_probability = None
                if not is_anchor:
                    probability_match = re.search(r'Match Probability:\s+([\d.]+)%', status_info)
                    if probability_match:
                        match_probability = float(probability_match.group(1)) / 100.0
                
                current_cluster['members'].append({
                    'dataset': dataset_name,
                    'level_id': level_id,
                    'energy': energy,
                    'uncertainty': uncertainty,
                    'spin_parity': spin_parity_string,
                    'is_anchor': is_anchor,
                    'match_probability': match_probability
                })
                continue
    
    # Add final cluster
    if current_cluster is not None:
        clusters.append(current_cluster)
    
    return clusters

def plot_clustering_results(input_path='Output_Clustering_Results.txt', output_path='Output_Cluster_Scheme.png', title_suffix=''):
    """
    Generates a textual table-like visualization of clustering results.
    Aligned horizontally by cluster, sorted vertically by energy.
    """
    if not os.path.exists(input_path):
        print(f"[WARNING] Input file {input_path} does not exist. Skipping.")
        return

    clusters = parse_clustering_results(input_path)
    
    if not clusters:
        print(f"[WARNING] No clusters found in {input_path}")
        return
    
    # Sort clusters by anchor energy (Low Energy at Bottom)
    clusters.sort(key=lambda x: x.get('anchor_energy', 0))
    
    # Dynamically extract unique datasets from cluster members (not hardcoded)
    unique_datasets = set()
    for cluster in clusters:
        for member in cluster['members']:
            unique_datasets.add(member['dataset'])
    datasets = sorted(list(unique_datasets))
    
    # Calculate figure size based on density (tunable multiplier)
    fig_height = max(8, len(clusters) * clustering_figure_height_multiplier)
    figure, axis = plt.subplots(figsize=(clustering_figure_width_inches, fig_height))
    
    # X-axis positions (tunable spacing)
    x_positions = {ds: index * clustering_x_spacing for index, ds in enumerate(datasets)}
    
    # Y-Position Calculation
    # Use anchor energies as base, then spread them to prevent text overlap
    anchor_energies = [c.get('anchor_energy', 0) for c in clusters]
    y_positions = spread_text_positions(anchor_energies, minimum_distance=clustering_minimum_distance)
    
    if len(y_positions) > 0:
        y_maximum_limit = y_positions[-1] + 300
    else: 
        y_maximum_limit = 1000

    # Plot Text for each cluster
    for index, cluster in enumerate(clusters):
        y_position = y_positions[index]
        cluster_number = cluster['cluster_number']
        
        # Plot members
        for member in cluster['members']:
            dataset_code = member['dataset']
            if dataset_code not in x_positions: continue
            
            x_position = x_positions[dataset_code]
            
            # Text Content
            # Format: 'Energy(Uncertainty)', 'Spin-Parity', 'Probability', 'ClusterID'
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
                f"Cluster {cluster_number} | {energy_string}\n"
                f"{spin_parity_string} | {probability_string}"
            )
            
            axis.text(x_position, y_position, text_block, 
                     ha='center', va='center', 
                     fontsize=Font_Config['cluster_text'], family='Times New Roman',
                     bbox=dict(boxstyle=f"round,pad={clustering_box_padding}", facecolor="white", edgecolor="gray", alpha=0.9))

    # Determine Y-Axis Limits
    axis.set_ylim(-200, y_maximum_limit)
    right_limit = (len(datasets) - 1) * clustering_x_spacing + clustering_x_axis_margin
    left_limit = -clustering_x_axis_margin
    axis.set_xlim(left_limit, right_limit)
    
    # X-Axis Labels
    axis.set_xticks([index * clustering_x_spacing for index in range(len(datasets))])
    dataset_labels = [f'Dataset {dataset_name}' for dataset_name in datasets]
    axis.set_xticklabels(dataset_labels, 
                         fontsize=Font_Config['cluster_axis_labels'], fontweight='bold', family='Times New Roman')
    
    # Y-Axis Label
    axis.set_ylabel("Energy / Cluster Index (Spread)", fontsize=Font_Config['cluster_axis_labels'], family='Times New Roman')
    
    # Styling: Remove spines for clean table look
    axis.spines['top'].set_visible(False)
    axis.spines['right'].set_visible(False)
    axis.spines['bottom'].set_visible(False)
    axis.spines['left'].set_visible(False)
    
    axis.tick_params(left=True, bottom=False, labelsize=Font_Config['cluster_tick_labels'])
    for label in axis.get_yticklabels():
        label.set_family('Times New Roman')
    
    full_title = "Clustering Results"
    if title_suffix:
        full_title += f" {title_suffix}"
    full_title += " (Aligned by Cluster)"
    
    axis.set_title(full_title, fontsize=Font_Config['cluster_title'], fontweight='bold', pad=20, family='Times New Roman')
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=300)
    print(f"[INFO] Clustering visualization saved to {output_path}")
    plt.close()

# ============================================================================
# MAIN EXECUTION
# ============================================================================

if __name__ == "__main__":
    print("=" * 60)
    print("Combined Visualizer: Generating plots...")
    print("=" * 60)
    
    # Generate level schemes
    plot_level_schemes()
    
    # Generate Clustering Results (XGBoost)
    plot_clustering_results(
        input_path='outputs/clustering/Output_Clustering_Results_XGBoost.txt', 
        output_path='outputs/figures/Output_Cluster_Scheme_XGBoost.png', 
        title_suffix='(XGBoost / Energy-Dominant)'
    )
    
    print("=" * 60)
    print("All visualizations complete!")
    print("=" * 60)
