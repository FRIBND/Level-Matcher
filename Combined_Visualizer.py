"""
Combined Level Scheme and Clustering Visualizer
======================================

# High-level Structure and Workflow Explanation:
======================================

All input datasets share one vertical energy axis so levels at the same energy
align horizontally across dataset columns for direct visual comparison.

Workflow Diagram:
[data/json datasets] --> [load_dataset] --> [Level Bars at True Energies]
                                               |
                                               v
                             [Gamma Arrows between Levels]
                                               |
                                               v
                          [One Shared Energy Axis for All Columns]

[clustering reports] --> [parse_clustering_results] --> [Cluster Table Renderer]
                                               |
                                               v
                        [outputs/figures/*.png]

Numbered Technical Steps:
1. Level scheme rendering: scaled level bars and gamma arrows per dataset column on a shared energy scale.
2. Clustering rendering: cluster memberships as aligned text blocks sorted by anchor energy.

Architecture:
- `load_dataset`: Reads standardized JSON level and gamma tables.
- `plot_level_schemes`: Shared-axis level bar and gamma arrow renderer.
- `parse_clustering_results`: Parses clustering report text into structured clusters.
- `plot_clustering_results`: Cluster membership table renderer.
- `spread_text_positions`: Iterative label relaxation used by the cluster table renderer.
"""

import matplotlib.pyplot as plt
import numpy as np
import json
import os
import re

# Import the inference dataset list from the single source of truth (Level_Matcher.py user configuration)
from Level_Matcher import inference_dataset_labels

# Tunable level scheme layout knobs (bars and arrows are drawn to the true energy scale)
level_scheme_column_spacing = 3.0         # Horizontal distance between dataset columns
level_scheme_bar_width = 2.4             # Level bar length; gamma arrows spread within this width
level_scheme_maximum_arrows = 30          # Strongest gamma transitions drawn per dataset
level_scheme_figure_height_inches = 14.0  # Shared-axis figure height for comparable energy scales

# Tunable clustering layout knobs for quick manual adjustments
clustering_box_padding = 1.0
clustering_minimum_distance = 500
clustering_figure_height_multiplier = 1.0
clustering_figure_width_inches = 10.0
clustering_x_spacing = 0.45
clustering_x_axis_margin = 0.3

# Font sizes shared by both renderers
Font_Config = {
    'axis_labels': 20,          # Axis labels ("Energy (keV)", dataset names)
    'tick_labels': 16,          # Axis tick values
    'title': 20,                # Figure titles
    'cluster_text': 12,         # Text inside cluster boxes
    'cluster_axis_labels': 16,  # Clustering axis labels
    'cluster_tick_labels': 14,  # Clustering axis tick values
    'cluster_title': 20,        # Clustering figure title
}

# ============================================================================
# SHARED UTILITY FUNCTIONS
# ============================================================================

def spread_text_positions(values, minimum_distance):
    """Iterative relaxation that enforces a minimum distance between sorted values.

    Used by the cluster table renderer to prevent text block overlap: positions start
    at their true values and overlapping pairs are pushed apart until every gap
    satisfies the minimum distance. Exits early once converged.
    """
    if not values:
        return []

    number_of_positions = len(values)
    positions = np.array(values, dtype=float)

    for iteration in range(2000):
        changed = False
        for index in range(number_of_positions - 1):
            distance = positions[index + 1] - positions[index]
            if distance < minimum_distance:
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
    """Render inference datasets on one shared energy axis: level bars and gamma arrows only.

    Bars and arrows are drawn to the true energy scale with no per-level text labels,
    so datasets of any density remain directly comparable across columns. The dataset
    list is imported from Level_Matcher.py so this visualizer always renders exactly
    the datasets that were selected for inference.
    """
    datasets = inference_dataset_labels
    dataset_contents = {code: load_dataset(code) for code in datasets}

    figure_width = 4.0 + len(datasets) * level_scheme_column_spacing
    figure, axis = plt.subplots(figsize=(figure_width, level_scheme_figure_height_inches))

    maximum_energy = 0.0
    for column_index, dataset_code in enumerate(datasets):
        raw_levels, gammas_table = dataset_contents[dataset_code]

        # True level energies in JSON order (gamma level indices refer to this order)
        level_energies = []
        for level in raw_levels:
            if isinstance(level.get('energy'), dict):
                energy_value = level.get('energy', {}).get('value')
            else:
                energy_value = level.get('energy_value')
            if energy_value is not None:
                level_energies.append(float(energy_value))
        if not level_energies:
            continue
        maximum_energy = max(maximum_energy, max(level_energies))

        # Level bars at their true energy positions
        x_center = column_index * level_scheme_column_spacing
        half_width = level_scheme_bar_width / 2.0
        for energy_value in level_energies:
            axis.hlines(y=energy_value, xmin=x_center - half_width, xmax=x_center + half_width,
                        colors='black', linewidth=0.6)

        # Gamma arrows from initial to final level, distributed across the bar width
        gamma_arrows = []
        for gamma in gammas_table:
            initial_index = gamma.get('initialLevel')
            final_index = gamma.get('finalLevel')
            if initial_index is None or final_index is None:
                continue
            if not (0 <= initial_index < len(level_energies) and 0 <= final_index < len(level_energies)):
                continue
            intensity_entry = gamma.get('gammaIntensity') or {}
            gamma_arrows.append((level_energies[initial_index], level_energies[final_index],
                                 intensity_entry.get('value') or 0.0))

        # Keep only the strongest transitions: drawing every weak gamma buries the levels
        gamma_arrows.sort(key=lambda arrow: arrow[2], reverse=True)
        gamma_arrows = gamma_arrows[:level_scheme_maximum_arrows]

        for arrow_index, (initial_energy, final_energy, _) in enumerate(gamma_arrows):
            horizontal_fraction = (arrow_index + 0.5) / len(gamma_arrows) - 0.5
            arrow_x_position = x_center + horizontal_fraction * level_scheme_bar_width
            axis.annotate('', xy=(arrow_x_position, final_energy), xytext=(arrow_x_position, initial_energy),
                          arrowprops=dict(arrowstyle='->', color='black', linewidth=0.5))

    # Shared energy scale and clean styling
    maximum_energy = max(maximum_energy, 1.0)
    axis.set_ylim(-0.02 * maximum_energy, 1.05 * maximum_energy)
    axis.set_xlim(-1.5, (len(datasets) - 1) * level_scheme_column_spacing + 1.5)
    axis.set_xticks([index * level_scheme_column_spacing for index in range(len(datasets))])
    axis.set_xticklabels([f'Dataset {code}' for code in datasets],
                         fontsize=Font_Config['axis_labels'], fontweight='bold', family='Times New Roman')

    axis.spines['top'].set_visible(False)
    axis.spines['right'].set_visible(False)
    axis.spines['bottom'].set_visible(False)
    axis.spines['left'].set_linewidth(1.5)

    axis.set_ylabel("Energy (keV)", fontsize=Font_Config['axis_labels'], family='Times New Roman')
    axis.tick_params(axis='x', length=0)
    axis.tick_params(axis='y', labelsize=Font_Config['tick_labels'])
    for tick_label in axis.get_yticklabels():
        tick_label.set_family('Times New Roman')

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
    """Parse the clustering report into a list of clusters with their members."""
    clusters = []
    current_cluster = None

    with open(clustering_file_path, 'r', encoding='utf-8') as file_handle:
        for line in file_handle:
            line = line.strip()

            cluster_match = re.match(r'^Cluster (\d+):$', line)
            if cluster_match:
                if current_cluster is not None:
                    clusters.append(current_cluster)
                current_cluster = {'cluster_number': int(cluster_match.group(1)), 'members': []}
                continue

            anchor_match = re.match(r'^Anchor:\s+(\S+)\s+\|\s+E=([\d.]+)±([\d.]+)\s+keV\s+\|\s+Spin-Parity=(.+)$', line)
            if anchor_match and current_cluster is not None:
                current_cluster['anchor_id'] = anchor_match.group(1)
                current_cluster['anchor_energy'] = float(anchor_match.group(2))
                current_cluster['anchor_uncertainty'] = float(anchor_match.group(3))
                current_cluster['anchor_spin_parity'] = anchor_match.group(4)
                continue

            member_match = re.match(r'^\[(\w+)\]\s+(\S+):\s+E=([\d.]+)±([\d.]+)\s+keV,\s+Spin-Parity=(.+?)\s+\((.+)\)$', line)
            if member_match and current_cluster is not None:
                status_info = member_match.group(6).strip()
                is_anchor = status_info == "Anchor"
                match_probability = None
                if not is_anchor:
                    probability_match = re.search(r'Match Probability:\s+([\d.]+)%', status_info)
                    if probability_match:
                        match_probability = float(probability_match.group(1)) / 100.0
                current_cluster['members'].append({
                    'dataset': member_match.group(1),
                    'level_id': member_match.group(2),
                    'energy': float(member_match.group(3)),
                    'uncertainty': float(member_match.group(4)),
                    'spin_parity': member_match.group(5).strip(),
                    'is_anchor': is_anchor,
                    'match_probability': match_probability
                })

    if current_cluster is not None:
        clusters.append(current_cluster)
    return clusters

def plot_clustering_results(input_path, output_path, title_suffix=''):
    """Render clustering results as an aligned table: text block per member, rows sorted by anchor energy."""
    if not os.path.exists(input_path):
        print(f"[WARNING] Input file {input_path} does not exist. Skipping.")
        return

    clusters = parse_clustering_results(input_path)
    if not clusters:
        print(f"[WARNING] No clusters found in {input_path}")
        return

    # Sort clusters by anchor energy (low energy at the bottom)
    clusters.sort(key=lambda x: x.get('anchor_energy', 0))

    # Collect the dataset columns present in the members (not hardcoded)
    datasets = sorted({member['dataset'] for cluster in clusters for member in cluster['members']})

    figure_height = max(8, len(clusters) * clustering_figure_height_multiplier)
    figure, axis = plt.subplots(figsize=(clustering_figure_width_inches, figure_height))

    x_positions = {code: index * clustering_x_spacing for index, code in enumerate(datasets)}

    # Spread rows vertically so text blocks never overlap
    anchor_energies = [cluster.get('anchor_energy', 0) for cluster in clusters]
    y_positions = spread_text_positions(anchor_energies, minimum_distance=clustering_minimum_distance)
    y_maximum_limit = (y_positions[-1] + 300) if len(y_positions) > 0 else 1000

    for index, cluster in enumerate(clusters):
        y_position = y_positions[index]
        cluster_number = cluster['cluster_number']
        for member in cluster['members']:
            dataset_code = member['dataset']
            if dataset_code not in x_positions:
                continue

            energy_string = f"{member['energy']:.0f}({int(member['uncertainty'])})"
            if member['is_anchor']:
                probability_string = "Anchor"
            elif member.get('match_probability') is not None:
                probability_string = f"{member['match_probability']:.1%}"
            else:
                probability_string = "N/A"

            # Two-line block: cluster identity plus energy on the first line, physics plus probability on the second
            text_block = (
                f"Cluster {cluster_number} | {energy_string}\n"
                f"{member['spin_parity']} | {probability_string}"
            )
            axis.text(x_positions[dataset_code], y_position, text_block,
                      ha='center', va='center',
                      fontsize=Font_Config['cluster_text'], family='Times New Roman',
                      bbox=dict(boxstyle=f"round,pad={clustering_box_padding}", facecolor="white", edgecolor="gray", alpha=0.9))

    axis.set_ylim(-200, y_maximum_limit)
    axis.set_xlim(-clustering_x_axis_margin,
                  (len(datasets) - 1) * clustering_x_spacing + clustering_x_axis_margin)
    axis.set_xticks([index * clustering_x_spacing for index in range(len(datasets))])
    axis.set_xticklabels([f'Dataset {code}' for code in datasets],
                         fontsize=Font_Config['cluster_axis_labels'], fontweight='bold', family='Times New Roman')
    axis.set_ylabel("Energy / Cluster Index (Spread)", fontsize=Font_Config['cluster_axis_labels'], family='Times New Roman')

    axis.spines['top'].set_visible(False)
    axis.spines['right'].set_visible(False)
    axis.spines['bottom'].set_visible(False)
    axis.spines['left'].set_visible(False)
    axis.tick_params(left=True, bottom=False, labelsize=Font_Config['cluster_tick_labels'])
    for tick_label in axis.get_yticklabels():
        tick_label.set_family('Times New Roman')

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
    print("Combined Visualizer: generating plots...")
    plot_level_schemes()
    plot_clustering_results(
        input_path='outputs/clustering/Output_Clustering_Results_XGBoost.txt',
        output_path='outputs/figures/Output_Cluster_Scheme_XGBoost.png',
        title_suffix='(XGBoost / Energy-Dominant)'
    )
    print("All visualizations complete.")
