"""
Explanation of Code Structure:

1. High-Level Strategy: This script provides a visual comparison between traditional vector-based cosine similarity and the newly implemented subset-robust statistical compatibility logic for nuclear level gamma matching.
2. Mathematical Visualization: The first plot demonstrates how different gamma intensity distributions (Datasets A, B, C) are represented as vectors in energy space, where physics similarity correlates with angular proximity.
3. Edge Case Handling: The second plot illustrates the failure mode of cosine similarity when dealing with incomplete data (subsets), motivating the transition to a more robust statistical approach.
4. Logic Implementation: Generates two side-by-side subplots using the Matplotlib library to contrast angular distance vs. subset overlap.
"""

import matplotlib.pyplot as plt
import numpy as np


def plot_datasets(save_path=None):
    """Plot the three example datasets A, B, C and optionally save as PNG."""
    fig, ax = plt.subplots(1, 1, figsize=(8, 7))

    vec_A = np.array([100, 23])
    vec_B = np.array([100, 18])
    vec_C = np.array([8, 100])

    origin = np.array([0, 0])

    ax.quiver(*origin, *vec_A, color='blue', scale=1, scale_units='xy', angles='xy', label='Dataset A (100, 23)', width=0.012)
    ax.quiver(*origin, *vec_B, color='green', scale=1, scale_units='xy', angles='xy', label='Dataset B (100, 18)', width=0.012, alpha=0.7)
    ax.quiver(*origin, *vec_C, color='red', scale=1, scale_units='xy', angles='xy', label='Dataset C (8, 100)', width=0.012)

    ax.set_xlim(0, 120)
    ax.set_ylim(0, 120)
    ax.set_xlabel('Intensity of ~1400 keV Gamma')
    ax.set_ylabel('Intensity of ~2000 keV Gamma')
    ax.set_title('Visualization of Datasets A, B, C\n(Angle = Physics Difference)')
    ax.grid(True, linestyle='--', alpha=0.6)
    ax.legend()
    ax.set_aspect('equal')

    ax.text(80, 35, "A & B are close\n(High Similarity)", color='blue', fontsize=10)
    ax.text(10, 105, "C is far away\n(Different Level)", color='red', fontsize=10)

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=200)
        plt.close(fig)
    else:
        plt.show()


def plot_subset_problem(save_path=None):
    """Plot the subset-problem example and optionally save as PNG."""
    fig, ax = plt.subplots(1, 1, figsize=(8, 7))

    origin = np.array([0, 0])
    vec_Full = np.array([100, 50])
    vec_Incomplete = np.array([100, 0])

    ax.quiver(*origin, *vec_Full, color='green', scale=1, scale_units='xy', angles='xy', label='High-Stat Experiment (100, 50)', width=0.015)
    ax.quiver(*origin, *vec_Incomplete, color='orange', scale=1, scale_units='xy', angles='xy', label='Low-Stat Experiment (100, 0)', width=0.015)

    ax.plot([0, 100], [0, 50], 'g--')
    ax.plot([0, 100], [0, 0], 'orange', linestyle='--')

    ax.set_xlim(0, 120)
    ax.set_ylim(0, 120)
    ax.set_xlabel('Intensity of Gamma 1')
    ax.set_ylabel('Intensity of Gamma 2')
    ax.set_title('Why Vector Cosine Fails on Incomplete Data\n(The "Subset" Problem)')
    ax.grid(True, linestyle='--', alpha=0.6)
    ax.legend(loc='upper left')
    ax.set_aspect('equal')

    ax.text(30, 45, "Angle = 26.6 degrees\nCosine Score = 0.894 (Low!)", color='red', fontweight='bold')
    ax.text(30, 60, "Vector method says:\n'These are different.'", color='black')
    ax.text(35, 5, "Physics Reality:\n'Orange is just a subset of Green.'\n(Should be Score 1.0)", color='green', fontweight='bold')

    plt.tight_layout()
    if save_path:
        plt.savefig(save_path, dpi=200)
        plt.close(fig)
    else:
        plt.show()


if __name__ == "__main__":
    # Save two separate PNGs in the repository root
    plot_datasets(save_path='vector_datasets.png')
    plot_subset_problem(save_path='vector_subset_problem.png')