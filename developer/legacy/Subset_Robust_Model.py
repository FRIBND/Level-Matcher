import matplotlib.pyplot as plt
import numpy as np
import os

def plot_example_1_a_vs_b():
    """Example 1: A vs B (Partial Subset Match)"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    # Dataset A (Reference)
    # 2000(100), 1400(80), 900(25), 600(5), 300(5)
    A_vals = [100, 80, 25, 5, 5]
    
    # Dataset B (Subset candidate)
    # Matches 2000->2005(85), 1400->1405(100). Others missing.
    B_vals = [85, 100, 0, 0, 0]
    
    labels = ['γ1\n2000', 'γ2\n1400', 'γ3\n900', 'γ4\n600', 'γ5\n300']
    
    x = np.arange(len(labels))
    width = 0.35
    
    # Plot Bars
    ax.bar(x - width/2, A_vals, width, label='Dataset A (Complete)', color='#4472C4', alpha=0.9, edgecolor='black')
    ax.bar(x + width/2, B_vals, width, label='Dataset B (Subset)', color='#ED7D31', alpha=0.9, edgecolor='black')
    
    # Matching Logic Visualization - Updated per user instructions
    overlaps = [92.5, 90] # (100+85)/2, (80+100)/2
    match_indices = [0, 1]
    
    for i, idx in enumerate(match_indices):
        overlap = overlaps[i]
        ax.plot([idx - width/2, idx + width/2], [overlap, overlap], 'g-', linewidth=4, zorder=10)
        ax.text(idx, overlap + 5, f'{overlap:.1f}', ha='center', color='green', fontweight='bold', fontsize=12)
        
    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=11)
    ax.set_ylabel('Normalized Intensity', fontsize=12)
    ax.set_title(f'Example 1: A vs B (Subset Match)\nScore: {92.5+90:.1f} / min(215, 185) = 0.986', 
                  fontsize=14, fontweight='bold', pad=15, color='#2F5597')
    
    ax.legend(loc='upper right')
    ax.grid(axis='y', alpha=0.3)
    ax.set_ylim(0, 125)
    
    # Annotation for subset
    ax.annotate('Unmatched in B\n(Expected for subset)', xy=(2.5, 10), xytext=(2.5, 40),
                 arrowprops=dict(facecolor='black', shrink=0.05, alpha=0.5),
                 ha='center', fontsize=10, style='italic', bbox=dict(boxstyle="round", fc="w", alpha=0.8))
    
    plt.tight_layout()
    plt.savefig('Example1_A_vs_B.png')
    print("Saved Example1_A_vs_B.png")
    plt.close()


def plot_example_2_a_vs_c():
    """Example 2: A vs C (Intensity Mismatch)"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    # Only showing matched gammas of A in detail would be misleading? 
    # User said "I said 5 gammas because dataset A has 5 gammas".
    # So we must show all 5 gammas of A, and show C matched against the relevant ones.
    
    # Dataset A Full Context
    labels = ['γ1\n2000', 'γ2\n1400', 'γ3\n900', 'γ4\n600', 'γ5\n300']
    A_vals = [100, 80, 25, 5, 5]
    
    # Dataset C: Matches only γ3 (idx 2) and γ4 (idx 3)
    # C values at relevant indices: [0, 0, 65, 100, 0]
    C_vals = [0, 0, 65, 100, 0]
    
    x = np.arange(len(labels))
    width = 0.35
    
    ax.bar(x - width/2, A_vals, width, label='Dataset A (Complete)', color='#4472C4', alpha=0.9, edgecolor='black')
    ax.bar(x + width/2, C_vals, width, label='Dataset C', color='#A5A5A5', alpha=0.9, edgecolor='black')
    
    # Mismatch Logic Visualization
    # Matched indices: 2 (900 vs 899) and 3 (600 vs 598)
    match_indices = [2, 3]
    overlaps = [25, 5] # min(25, 65), min(5, 100)
    
    for i, idx in enumerate(match_indices):
        overlap = overlaps[i]
        # Connector showing comparison
        ax.plot([idx - width/2, idx + width/2], [overlap, overlap], 'r--', linewidth=4, zorder=10)
        ax.text(idx, overlap + 5, f'{overlap}', ha='center', color='red', fontweight='bold', fontsize=12)
        ax.text(idx, C_vals[idx] + 5, 'Intensity\nMismatch', ha='center', color='red', fontsize=9)

    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=11)
    ax.set_ylabel('Normalized Intensity', fontsize=12)
    ax.set_title(f'Example 2: A vs C (Intensity Mismatch)\nScore: {25+5}/165 = 0.182', 
                  fontsize=14, fontweight='bold', pad=15, color='#C00000')
    ax.set_ylim(0, 125)
    ax.legend(loc='upper left')
    ax.grid(axis='y', alpha=0.3)
    
    plt.tight_layout()
    plt.savefig('Example2_A_vs_C.png')
    print("Saved Example2_A_vs_C.png")
    plt.close()


def plot_example_3_a_vs_d():
    """Example 3: A vs D (Binary Mode - Energy Only)"""
    fig, ax = plt.subplots(figsize=(10, 7))
    
    # Dataset A Full Context
    labels = ['γ1\n2000', 'γ2\n1400', 'γ3\n900', 'γ4\n600', 'γ5\n300']
    
    # D Matches γ2 (1400) and γ3 (900)
    # Binary match status: 1 if matched, 0 if not (relative to A's slots)
    match_status = [0, 1, 1, 0, 0] # indices 1 (1400) and 2 (900) match
    
    x = np.arange(len(labels))
    
    # Plot as specific status bars
    # We color matched bars differently to highlight them within the context of A
    colors = ['#E0E0E0' if s == 0 else '#70AD47' for s in match_status]
    edgecolors = ['gray' if s == 0 else 'black' for s in match_status]
    
    bars = ax.bar(x, match_status, 0.6, color=colors, edgecolor=edgecolors, label='Match Status')
    
    # Add checkmarks
    for i, rect in enumerate(bars):
        if match_status[i] == 1:
            ax.text(rect.get_x() + rect.get_width()/2, 0.5, '✓ MATCH', 
                    ha='center', va='center', color='white', fontweight='bold', fontsize=12)
        else:
             ax.text(rect.get_x() + rect.get_width()/2, 0.1, 'No Match', 
                    ha='center', va='bottom', color='gray', fontsize=10)

    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=11)
    ax.set_ylabel('Binary Match Status', fontsize=12)
    ax.set_title(f'Example 3: A vs D (Binary Mode)\nScore: 2 Matches / 2 Total = 1.00', 
                  fontsize=14, fontweight='bold', pad=15, color='#548235')
    
    ax.set_ylim(0, 1.2)
    ax.set_yticks([0, 1])
    ax.set_yticklabels(['No', 'Yes'])
    ax.grid(axis='y', alpha=0.3)
    
    # Legend manually constructed implies Green = Matched
    from matplotlib.patches import Patch
    legend_elements = [Patch(facecolor='#70AD47', edgecolor='black', label='Energy Match Found'),
                       Patch(facecolor='#E0E0E0', edgecolor='gray', label='No Energy Match')]
    ax.legend(handles=legend_elements, loc='upper right')
    
    # Text box explaining binary mode
    props = dict(boxstyle='round', facecolor='wheat', alpha=0.5)
    ax.text(0.5, 0.85, "No Intensity Data in D\nAlgorithm counts energy matches\nagainst Reference A", 
             transform=ax.transAxes, fontsize=11, ha='center', bbox=props)

    plt.tight_layout()
    plt.savefig('Example3_A_vs_D.png')
    print("Saved Example3_A_vs_D.png")
    plt.close()

if __name__ == "__main__":
    plot_example_1_a_vs_b()
    plot_example_2_a_vs_c()
    plot_example_3_a_vs_d()
