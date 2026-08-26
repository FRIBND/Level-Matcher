# Level-Matcher

Part of the AI/ML Technical Innovation at the Nuclear Data Group at the Facility for Rare Isotope Beams (nucleardata@frib.msu.edu).

Supported by the U.S. Department of Energy, Office of Science, Office of Nuclear Physics under Award Number DE-SC0016948.

Open-source release: [github.com/FRIBND/Level-Matcher](https://github.com/FRIBND/Level-Matcher)

## Overview

The first Physics-informed Machine Learning nuclear level matching tool designed for Evaluated Nuclear Structure Data File (ENSDF) workflows.

Developed and refined through daily evaluation tasks at the Nuclear Data Group at the Facility for Rare Isotope Beams (FRIB).

Built on the open-source XGBoost (Extreme Gradient Boosting) framework, Level-Matcher employs physics-informed feature engineering, gradient boosting model training, and pairwise inference to reconcile nuclear level schemes across multiple datasets. 

Integration roadmap: A production version will be incorporated into the Consistency Check and Evaluation Toolkit Java codes as part of the NSDD ENSDF Analysis and Utility Programs.

## Development Timeline

- 2026-04-19: Tested and refined on real-world ENSDF datasets $^{34}\text{Cl}$.
- 2026-04-17: Introduced configurable parameters for feature correlation, model training diagnostics, and validation metrics.
- 2026-01-05: End-to-end pipeline integration with five core components: dataset parsing, physics-informed feature extraction, synthetic feature-label generation, XGBoost model training and pairwise inference, constrained graph partitioning, and level-scheme visualization.
- 2025-10-20: Foundational prototype: decision tree-based ranking framework (LightGBM) at [github.com/sunlijie-msu/Level-Matcher](https://github.com/sunlijie-msu/Level-Matcher).

## High-Level Structure and Workflow Explanation

Inference-first pipeline: raw ENSDF records → standardized JSON → synthetic-trained XGBoost → pairwise probabilities → clique-constrained unified level scheme.

```text
[Raw ENSDF] --> [Dataset_Parser] --> [data/json/]
                                         |
                                         v
[Feature_Engineer] --> [5-D Feature Extraction, Scoring_Config, Label Logic]
        |                            |
        v                            v
[Synthetic Training Data]    [Pairwise Inference]
        |                            |
        v                            v
[XGBoost Training] <--- [Level_Matcher] --> [outputs/pairwise/]
        |                            |
        v                            v
[Training_Metrics_Visualizer]  [Level_Clusterer] --> [outputs/clustering/]
                                        |
                                        v
                    [Combined_Visualizer] --> [Level Scheme Plots]
```

---

## 1. Core Technology Stack

### Level 1: Decision Trees
Decision trees are non-parametric supervised learning methods used for classification and regression. The primary objective is to create a model that predicts the target variable value by learning simple decision rules inferred from data features.

#### 1.1 Core Components
- **Root Node**: The initial starting point containing the complete dataset.
- **Decision Node (Internal Node)**: Points where data is partitioned based on a feature value.
- **Branches**: Vectors connecting nodes representing decision outcomes.
- **Leaf Node (Terminal Node)**: Final outcomes or specific predictions.
- **Splitting**: The process of dividing a node into multiple sub-nodes.

#### 1.2 Training Methodology
- **Classification and Regression Trees (CART)**: Builds trees using binary splits to minimize impurity (Gini impurity for classification or Mean Square Error for regression).
- **Inherent Traits**: High variance and low bias. Deep trees are sensitive to minor data fluctuations and prone to overfitting.
- **Project Role**: Functions as the base estimator for ensemble methods.

### Level 2: Ensemble Learning
Ensemble methods combine multiple models into a single, more robust predictive model.

#### 2.1 Bagging (Bootstrap Aggregating)
Bagging reduces variance by training multiple versions of the same model on different random subsets (with replacement) of the training data.
- **Mechanism**: Generates $M$ datasets via bootstrap sampling and trains $M$ independent trees in parallel. Final predictions result from averaging (regression) or majority voting (classification).
- **Key Algorithm**: Random Forest (Breiman, 2001).
- **Physics Limitation**: Averaging fails for "Hard Vetoes." If one tree detects a fatal Spin-Parity mismatch (Probability $\approx 0$) but 99 trees observe an energy match (Probability $\approx 1$), the average remains high ($\approx 0.99$). Physics requires a single veto to drive the final probability to zero.

#### 2.2 Boosting
Boosting is a sequential method where each new model attempts to correct the errors of its predecessor.
- **Mechanism**: Iterative improvement. Tree $m$ is trained to minimize the loss (errors) of the existing ensemble $F_{m-1}$.
- **Physics Strength**: Mimics a logical "Veto" system. A subsequent tree can detect a specific violation (e.g., Parity mismatch) and output a large negative correction, effectively suppressing the match probability regardless of other indicators.

### Level 3: Gradient Boosting Algorithm

#### 3.1 Adaptive Boosting (AdaBoost)
- **Mechanism**: Sample re-weighting. At each step, it increases the weights of misclassified observations.
- **Verdict**: **Rejected**. Sensitive to noisy data and experimental outliers. In nuclear data, anomalies receive exponential weight, causing the model to fixate on experimental errors rather than general trends. Reference: Freund and Schapire (1997).

#### 3.2 Gradient Boosting Machine (GBM)
- **Mechanism**: Functional Gradient Descent. A new tree is trained to predict the negative gradient (pseudo-residuals) of the loss function, fitting the error rather than the raw data.
- **Verdict**: **Superior**. Optimizing differentiable loss functions (e.g., Logarithmic Loss) is more robust to outliers than the exponential loss used in AdaBoost. Reference: Friedman (2001).

### Level 4: Software Implementation

| Package | NaN Handling | Growth Strategy | Best Data Scale | Weakness for Physics | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Scikit-Learn GradientBoosting** | Fails (Crashes) | Level-wise | Small | Requires imputation (bias risk); slow; lacks regularization. | **Reject** |
| **LightGBM** | Native (Safe) | Leaf-wise | Huge ($>10^5$) | "Greedy" growth overfits small data; creates unbalanced trees. | **Reject** |
| **Scikit-Learn HistGradientBoosting** | Native (Safe) | Leaf-wise | Medium/Large | Less tunable regularization than XGBoost; defaults to greedy growth. | **Acceptable** |
| **XGBoost** | Native (Safe) | Level-wise | Any | Minimal. Level-wise growth and $L_1$/$L_2$ regularization ideal for stability. | **Best** |
| **CatBoost** | Native (Safe) | Symmetric | Medium/Large | Slower training for pure numerical tasks; heavier dependency. | **Alternative** |

#### 4.1 Growth Strategies
- **Leaf-wise Growth (LightGBM)**: Splits the leaf with the highest error. While efficient for large data, it can grow deep, lopsided trees that "memorize" outliers in small datasets.
- **Level-wise Growth (XGBoost)**: Grows the tree layer-by-layer. This produces balanced trees, acting as a natural regularizer against experimental noise in nuclear physics.

#### 4.2 Academic References
- **XGBoost**: Tianqi Chen and Carlos Guestrin. "XGBoost: A Scalable Tree Boosting System." *Proceedings of the 22nd ACM SIGKDD International Conference on Knowledge Discovery and Data Mining*, 785 (2016). [DOI: 10.1145/2939672.2939785](https://doi.org/10.1145/2939672.2939785)
- **LightGBM**: Guolin Ke et al. "LightGBM: A Highly Efficient Gradient Boosting Decision Tree." *Advances in Neural Information Processing Systems 30*, 3149 (2017). [Paper Link](https://proceedings.neurips.cc/paper/2017/hash/6449f44a102fde848669bdd9eb6b76fa-Abstract.html)

---

## 2. Why XGBoost for Nuclear Data?

### 2.1 Native Handling of Missing Values (Sparsity Awareness)
Nuclear experimental datasets are inherently sparse. XGBoost utilizes **Sparsity-Aware Split Finding**. Unlike legacy algorithms requiring bias-inducing imputation, it explicitly learns optimal default directions for missing data (`NaN`). It treats "Unknown" as a distinct physical state, preserving experimental ambiguity.

### 2.2 Small Data Regularization
Nuclear level schemes typically contain fewer than 500 levels. Algorithms like LightGBM tend to "memorize" noise in small datasets. XGBoost’s level-wise growth, combined with native **$L_1$ (Lasso) and $L_2$ (Ridge) regularization**, prevents overfitting and captures general physical trends.

### 2.3 Statistical-Logical Integration
- **The Challenge:** Standard models treat selection rules as soft statistical features, allowing a precise energy match to override a fatal physics violation (e.g., $J^\pi = 3^+$ matched to $4^-$).
- **The XGBoost Solution:**
    - **Statistical Compliance (Monotonicity):** Larger energy deviations penalize match probability, reflecting the statistical nature of experimental uncertainties.
    - **Logical Compliance (Hard Vetoes):** As a sequential learner, XGBoost learns that quantum number violations (e.g., parity mismatch) act as absolute vetoes, nullifying match probability regardless of energy agreement.

---

## 3. Implementation Strategy

### 3.1 Data Partitioning Strategy

| Subset | Proportion | Size ($\sim$) | Objective | Model Interaction |
| :--- | :--- | :--- | :--- | :--- |
| **Synthetic Training Set** | 80% | 17,758 | Pattern learning | Weight updates via gradient descent |
| **Synthetic Validation Set** | 20% | 4,440 | Overfitting monitor | Evaluated without weight updates |
| **Inference Datasets** | User-selected | Varies | Real-world level matching | Never used for model optimization; no ground-truth labels assumed |

### 3.2 Diagnostic Metrics

| Metric | Definition | Purpose | Target Range |
| :--- | :--- | :--- | :--- |
| **RMSE** | Root Mean Square Error | Measures average prediction error | $<0.05$ (Excellent), $>0.3$ (Poor) |
| **MAE** | Mean Absolute Error | Robust average absolute deviation | $<0.02$ (Excellent), $>0.2$ (Poor) |
| **LogLoss** | Binary Cross-Entropy | Calibrates match probabilities | $<0.3$ (Excellent), $>1.0$ (Poor) |
| **Feature Gain** | Loss reduction per feature | Identifies physical drivers | Higher = More influential |
| **Stop Round** | Early stopping iteration | Monitors model complexity | $<70\%$ of max estimators |

**LogLoss Deep Dive**: Binary cross-entropy measures how well predicted probabilities match true labels. Lower LogLoss indicates better-calibrated probabilities, essential for ranking match candidates reliably. Implementation uses manual computation with prediction clipping to $[10^{-15}, 1-10^{-15}]$ to avoid numerical errors.

**Feature Importance (Gain) Deep Dive**: XGBoost's Gain metric measures total loss reduction when a feature is used for splits. Measured hierarchy: `Parity_Similarity` > `Spin_Similarity` > `Gamma_Decay_Pattern_Similarity` > `Energy_Similarity` > `Specificity`. Parity and Spin dominate because their hard-veto thresholds create the sharpest decision boundaries in the training data.

### 3.3 Interpreting Diagnostic Results
- **Golden State**: Minimal Train/Validation gap (<0.01 RMSE) and validation LogLoss < 0.3.
- **Red Flag (Overfitting)**: Large gap (>0.05 RMSE) between training and validation.
- **Red Flag (Underfitting)**: High validation RMSE (>0.3) or stopping at iteration 1.

---

## 4. Physics-Informed Feature Engineering

`Feature_Engineer.py` converts every candidate level pair into a 5-D feature vector (ordered: Energy, Spin, Parity, Specificity, Gamma). All scores live in $[0,1]$ and are monotonic (higher = stronger match evidence):

1. **Energy_Similarity**: Gaussian kernel $\exp\left(-\sigma_{\text{scale}} \cdot Z^2\right)$ with $Z = \frac{|E_1 - E_2|}{\sqrt{\sigma_1^2 + \sigma_2^2}}$. `Sigma_Scale` = 0.2 (default: 1σ→82%, 2σ→45%, 3σ→17%). Missing energy → 0.0.
2. **Spin_Similarity**: Optimistic max over all $J$ pairings. $\Delta J = 0$ firm→1.0, $\Delta J = 0$ any tentative→0.8, $\Delta J = 1$ any tentative→0.2, $\Delta J = 1$ firm→0.05, $\Delta J \ge 2$→0.0.
3. **Parity_Similarity**: Optimistic max over all $\pi$ pairings. Same firm→1.0, same tentative→0.8, opposite tentative→0.05, opposite firm→0.0.
4. **Specificity**: Ambiguity penalty $1/f(\text{multiplicity})$, where multiplicity = product of both levels' spin-option counts. Selectable formulas: `sqrt` (default), `linear`, `log`, `tunable`.
5. **Gamma_Decay_Pattern_Similarity**: Greedy one-to-one γ-energy matching within 3σ. Intensity mode: overlap = average when intensities are consistent ($Z \le 3\sigma$), minimum when inconsistent; sum is normalized by the smaller total intensity (subset-robust). Falls back to energy-only match counting when intensities are missing.

Missing spin/parity/gamma data → `Neutral_Score` = 0.5.

### 4.1 Label Logic (Veto, Rescue, and Final Formula)

`calculate_label()` maps the 5 raw scores to a match probability via three adjustments plus a final product:

- **Neutral Remap**: neutral spin/parity/gamma 0.5 → 0.85 (`Neutral_Remap_Factor`), preventing collapse $0.5^3 = 0.125$; energy and specificity untouched.
- **Physics Veto**: spin $\le$ 0.04 or parity $\le$ 0.04 (definitive quantum mismatch, `Spin_Veto_Max`/`Parity_Veto_Max`) → label 0.0.
- **Physics Rescue**: (spin ≥ 0.86 and parity ≥ 0.86) or gamma ≥ 0.86 → energy similarity$^{0.5}$ (calibration-offset rescue). Raw scores tested, so "unknown" never triggers.
- **Final Formula**: $\text{label} = \text{clamp}\left(E_{\text{eff}} \cdot \sqrt{S_{\text{eff}} \cdot P_{\text{eff}} \cdot G_{\text{eff}}} \cdot \text{Specificity},\ 0,\ 0.999\right)$

---

## 5. Graph-Based Clustering Algorithm

`Level_Clusterer.py` runs deterministic constrained graph partitioning: candidate pairs with probability ≥ 0.15 are processed greedily in descending order.
- **Dataset Uniqueness**: Each cluster holds at most one level per dataset; ambiguous levels may join multiple clusters.
- **Mutual Consistency (Clique)**: Clusters merge only when every cross-member pair clears the threshold — blocks weak "chain" merges.
- **Anchor Selection**: Cluster anchor = member with smallest $\sigma_E$; clusters are sorted by average energy and written to `outputs/clustering/`.

---

## 6. Project Structure

```text
Level-Matcher/
├── Level_Matcher.py           # Main orchestration (Training, Inference, Clustering)
├── Feature_Engineer.py        # Physics engine (Feature extraction, Label logic)
├── Level_Clusterer.py         # Deterministic clique-constrained graph partitioning
├── Dataset_Parser.py          # Regex-based ENSDF log to JSON converter
├── Training_Metrics_Visualizer.py # Diagnostic suite (5-panel metrics plots)
├── Combined_Visualizer.py     # Visualization suite (High-res level scheme plots)
├── Run_Pipeline.ps1           # One-command wrapper; detached ML process for VS Code safety
├── data/
│   ├── raw/                   # ENSDF source files and XREF labels
│   └── json/                  # Standardized JSON datasets consumed by the pipeline
├── outputs/
│   ├── figures/               # PNG outputs (Diagnostics, Level Schemes)
│   ├── clustering/            # Final cluster memberships (Text reports)
│   └── pairwise/              # Pairwise match probabilities
├── docs/                      # Documentation and technical reports
├── scripts/
│   ├── hyperparameter_tuning/ # Optimization and validation utilities
│   └── legacy/                # Experimental/archive scripts
└── README.md                  # This file
```

---

## 7. Workflow & Usage

1. **Run full pipeline**: `.\Run_Pipeline.ps1` executes Dataset Parser → Level Matcher (detached subprocess; logs stream to `outputs/run_log.txt`) → Combined Visualizer. Detached spawn prevents VS Code freezes.
2. **Parse raw ENSDF files**: `Dataset_Parser.py` scans all `.ens` files in `data/raw/` and writes standardized JSON datasets to `data/json/`.
3. **Select inference targets**: In `Level_Matcher.py`, set `inference_dataset_labels` (default `['K', 'L']`).
4. **Train on synthetic physics data**: `Level_Matcher.py` generates synthetic labeled examples and trains XGBoost with early stopping and monotonic constraints.
5. **Run real-world inference**: All cross-dataset level pairs for the selected datasets are scored; pairs above the output threshold are written to `outputs/pairwise/`.
6. **Cluster matched levels**: `Level_Clusterer.py` consolidates pairwise matches into clique-consistent clusters written to `outputs/clustering/`.
7. **Review outputs**: `Combined_Visualizer.py` renders level schemes to `outputs/figures/` for visual audit.

---

## 8. Development & Testing Standards

### 8.1 Mandatory Runtime Validation
After any code modification:
1.  Execute `Level_Matcher.py` to verify the training pipeline (Exit Code 0).
2.  Check `outputs/figures/Training_Metrics_Diagnostic.png` for overfitting flags.
3.  Run `Combined_Visualizer.py` to ensure visual output integrity.

### 8.2 Coding Protocols
- **No Acronyms**: Variables must be fully spelled out (e.g., `energy_uncertainty` not `ener_uncert`).
- **No ALL CAPS**: Use `Title_Case` or `snake_case` for all variables and constants.
- **Header Requirement**: Every script must lead with a High-level Structure and Workflow Explanation.

---
**Maintained by**: FRIB Nuclear Data Group  
**Status**: [In Development]
**Version**: 0.3.2 (XGBoost Architecture)
