import matplotlib.pyplot as plt
import numpy as np

def plot_bray_curtis_user_data():
    # Style settings for cleaner look
    plt.rcParams['figure.figsize'] = (18, 6)
    plt.rcParams['axes.grid'] = True
    plt.rcParams['grid.alpha'] = 0.3
    
    # We'll create three separate figures (one per scenario) and save each to PNG.
    # This avoids layout/axis-size issues and makes each plot directly reusable.

    def plot_comparison(ax, title, labels, vals_1, vals_2, label_1, label_2):
        x = np.arange(len(labels))
        
        # Calculate Overlap
        intersection = np.minimum(vals_1, vals_2)
        
        # Calculate Score
        sum_intersection = np.sum(intersection)
        sum_total = np.sum(vals_1) + np.sum(vals_2)
        score = (2.0 * sum_intersection) / sum_total

        # 1. Plot Dataset 1 (Blue Background)
        ax.bar(x, vals_1, width=0.6, label=label_1, color='blue', alpha=0.3, edgecolor='blue', linewidth=1)
        
        # 2. Plot Dataset 2 (Red Background)
        ax.bar(x, vals_2, width=0.6, label=label_2, color='red', alpha=0.3, edgecolor='red', linewidth=1)
        
        # 3. Plot Intersection (Green, Explicitly visible)
        # This represents "min(Ia, Ib)"
        ax.bar(x, intersection, width=0.6, label='Shared (Overlap)', color='#2ca02c', alpha=0.8, edgecolor='black', hatch='//')

        # Formatting
        ax.set_title(f"{title}\nBC Score: {score:.3f}", fontsize=12, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels(labels, rotation=15)
        ax.set_ylabel('Normalized Intensity')
        ax.set_ylim(0, 110)
        ax.legend(loc='upper right', fontsize=9)
        
        return score

    # --- PLOT 1: Dataset A vs Dataset B (The Match) ---
    # A: 1400(100), 2000(23)
    # B: 1405(100), 2008(18)
    # Note: Energies matched within tolerance
    labels_1 = ['~1400 keV', '~2000 keV']
    A_1 = np.array([100, 23])
    B_1 = np.array([100, 18])
    
    # Scenario 1: create its own figure and save
    fig1, ax1 = plt.subplots(1, 1, figsize=(8, 6))
    score1 = plot_comparison(ax1, "Scenario 1: Good Match (A vs B)\nHigh Intensity Overlap", 
                             labels_1, A_1, B_1, "Dataset A", "Dataset B")
    ax1.text(0.5, 60, "Green Area is Dominant\n(Good Score)", ha='center', color='green', fontweight='bold')
    plt.tight_layout()
    fig1.savefig('shared_area_scenario1.png', dpi=200)
    plt.close(fig1)

    # --- PLOT 2: Dataset A vs Dataset C (The Swap) ---
    # A: 1400(100), 2000(23)
    # C: 1399(8),   1999(100) -> Normalized to strong peak
    labels_2 = ['~1400 keV', '~2000 keV']
    A_2 = np.array([100, 23])
    C_2 = np.array([8, 100])
    
    # Scenario 2: create its own figure and save
    fig2, ax2 = plt.subplots(1, 1, figsize=(8, 6))
    score2 = plot_comparison(ax2, "Scenario 2: Intensity Mismatch (A vs C)\nRank Swap Penalty", 
                             labels_2, A_2, C_2, "Dataset A", "Dataset C")
    ax2.text(0.5, 60, "Green Area is Tiny\n(Low Score)", ha='center', color='red', fontweight='bold')
    plt.tight_layout()
    fig2.savefig('shared_area_scenario2.png', dpi=200)
    plt.close(fig2)

    # --- PLOT 3: The Subset Problem (Why Standard BC Fails) ---
    # --- PLOT 3: The Subset Problem (Why Standard BC Fails) ---
    # Simulating 2 vs 6 gammas logic using A as a subset (A misses weak gammas present in X)
    # Dataset A (Subset): 1400(100), 2000(59)
    # Dataset X (High Res): 1400(100), 2000(63), + 4 weak gammas A missed
    labels_3 = ['~1400', '~2000', 'Weak 1', 'Weak 2', 'Weak 3', 'Weak 4']
    A_3 = np.array([100, 59, 0, 0, 0, 0])
    X_3 = np.array([100, 63, 40, 25, 15, 10])
    
    # Scenario 3: create its own figure and save
    fig3, ax3 = plt.subplots(1, 1, figsize=(10, 6))
    score3 = plot_comparison(ax3, "Scenario 3: Subset Failure\n(A is missing weak gammas)", 
                             labels_3, A_3, X_3, "Dataset A (Low Res)", "Dataset X (High Res)")
    ax3.annotate('Red Bars (Excess)\ndilute the score', xy=(2, 20), xytext=(4, 80),
                  arrowprops=dict(facecolor='red', shrink=0.05), ha='center', color='red', fontweight='bold')
    ax3.text(3.5, 50, f"Standard BC: {score3:.2f}\nOur New Method: 1.0", ha='center', color='blue', style='italic',
                 bbox=dict(facecolor='white', edgecolor='blue', alpha=0.8))
    plt.tight_layout()
    fig3.savefig('shared_area_scenario3.png', dpi=200)
    plt.close(fig3)

    # Also print filenames so users running headless can see outputs
    print('Saved: shared_area_scenario1.png, shared_area_scenario2.png, shared_area_scenario3.png')

if __name__ == "__main__":
    plot_bray_curtis_user_data()