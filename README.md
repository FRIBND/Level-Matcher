**Level Matcher**: Part of the AI/ML Technical Innovation at the FRIB Nuclear Data Group (nucleardata@frib.msu.edu).

## Overview

Level Matcher is a physics-informed nuclear level matching system for Evaluated Nuclear Structure Data File (ENSDF) workflows. It is designed to parse raw ENSDF files, build standardized JSON datasets, and perform pairwise and clustered inference on real-world nuclear level schemes that do not have ground-truth labels. Model training is performed only on synthetic, physics-generated examples so that real evaluation datasets remain reserved for inference.

Developed and refined through daily evaluation tasks at the Nuclear Data Group at the Facility for Rare Isotope Beams (FRIB), Michigan State University.

- **Open-source prototype**: [github.com/FRIBND/Level-Matcher](https://github.com/FRIBND/Level-Matcher)
- **Integration roadmap**: A production version will be incorporated into the Consistency Check and Evaluation Toolkit Java codes as part of the NSDD ENSDF Analysis and Utility Programs.

## Development Timeline

- **2026-04-19** — Adapted Level-Matcher for robust, operational inference on real-world nuclear datasets by improving ENSDF parsing accuracy, physics-informed scoring logic and feature engineering, and inference workflow.
  - **`Dataset_Parser.py`**: Corrected relative-intensity uncertainty handling, refined parsing and energy matching, and added $^{34}$Cl real ENS data files for parser and inference development.
  - **`Feature_Engineer.py`**: Reorganized label generation, clarified veto and rescue logic, and moved scoring and synthetic-data behavior fully under `Scoring_Config`.
  - **`Level_Matcher.py`**: Added user-facing inference dataset selection, tuned XGBoost on synthetic data only, and updated the documentation to describe the inference-first workflow.

- **2026-04-17** — Applied to the first real-world evaluation task for $^{34}\text{Cl}$.
  - Pairwise inference across 27,615 candidate pairs from $^{32}\text{S}(p,\gamma)^{34}\text{Cl}$ and $^{33}\text{S}(p,p)$ resonance datasets.
  - Detached subprocess architecture adopted to isolate gradient boosting training from the IDE event loop, eliminating VS Code UI freeze events.

- **2026-01-24** — Refined for diagnostic rigor and modularized for public release.
  - 80/20 training-validation split with early stopping (patience = 50 rounds).
  - Five-panel diagnostic visualization: RMSE, MAE, and LogLoss learning curves; feature importance (Gain); overfitting analysis (train-validation RMSE gap).
  - Schema standardization across all ingested datasets.
  - Public-facing consistency reporting module integrated.

- **2026-01-05** — Expanded into a full physics-informed matching pipeline.
  - Regex-based ENSDF-to-JSON ingestion with schema validation.
  - Physics-informed feature engineering: five nuclear descriptors with hard-veto logic for quantum number violations.
  - Dual matching engine: XGBoost and LightGBM gradient-boosted ensembles for pairwise inference.
  - Graph-based, clique-constrained clustering for mutually consistent level groupings.
  - High-resolution level scheme visualization for visual audit.

- **2025-10-20** — Initial prototype as a LightGBM ranking model.
  - Repository: [github.com/sunlijie-msu/Level-Matcher](https://github.com/sunlijie-msu/Level-Matcher).


---

## High-Level Structure and Workflow Explanation

The system operates as an inference-first pipeline that transforms raw ENSDF records into a unified, clique-constrained nuclear level scheme.

```text
[Raw ENSDF Logs] --> [Dataset_Parser] --> [JSON Datasets]
                                               |
                                               v
[Physics Constraints] --> [Feature_Engineer] --+--> [Synthetic Training Data]
                                               |
                                               v
[Training] <----------- [Level_Matcher] <------+--> [XGBoost Inference Model]
    |                                          |
    v                                          v
[Diagnostic Visuals] <--- [Metrics_Visualizer] [Pairwise Inference]
                                               |
                                               v
[Graph Clustering] <----- [Clique Algorithm] --+--> [Mutual Consistency Check]
                                               |
                                               v
[Final Scheme] <--------- [Combined_Visualizer] --> [High-Res Level Scheme Plots]
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

The model processes five primary features, transforming raw experimental data into nuclear physics descriptors:

1.  **Energy_Similarity**: Calculated using a Gaussian kernel based on the experimental $Z$-score:
    $$\text{Energy Similarity} = \exp\left(-\sigma_{\text{scale}} \cdot Z^2\right)$$
    where $Z = \frac{|E_1 - E_2|}{\sqrt{\sigma_1^2 + \sigma_2^2}}$.
2.  **Spin_Similarity**: Encodes nuclear selection rules. Forbidden transitions ($|\Delta J| \ge 2$) receive an absolute zero veto.
3.  **Parity_Similarity**: Validates parity conservation. Definite mismatches trigger a zero veto.
4.  **Gamma_Decay_Pattern_Similarity**: Computes the cosine similarity of Gaussian-broadened gamma-ray spectra.
5.  **Specificity**: Measures the uniqueness of a level's assignment to penalize high-multiplicity ambiguity ($\text{Specificity} = 1/\sqrt{N}$).

### 4.1 Physics Rescue Mechanism
When quantum numbers or gamma patterns show exceptional agreement (Similarity $\ge 0.86$), the system activates a rescue protocol:
- **Formula**: $\text{Effective Energy} = (\text{Energy Similarity})^{0.5}$
- **Rationale**: Prevents rejection of valid matches where energy calibration differs but internal structure is identical.

---

## 5. Graph-Based Clustering Algorithm

The system merges pairwise inferences into physical clusters using a clique-constrained graph algorithm:
- **Dataset Uniqueness**: Strictly enforces a maximum of one level per dataset per cluster.
- **Mutual Consistency**: All members must be pairwise compatible (forming a clique).
- **Anchor Selection**: The level with the smallest experimental energy uncertainty ($\sigma_E$) is selected as the cluster reference.
- **Ambiguity Support**: Levels with high uncertainty can maintain membership in multiple clusters until resolved.

---

## 6. Project Structure

```text
Level-Matcher/
├── Level_Matcher.py           # Main orchestration (Training, Inference, Clustering)
├── Feature_Engineer.py        # Physics engine (Feature extraction, Rescue mechanism)
├── Dataset_Parser.py          # Regex-based ENSDF log to JSON converter
├── Training_Metrics_Visualizer.py # Diagnostic suite (5-panel metrics plots)
├── Combined_Visualizer.py     # Visualization suite (High-res level scheme plots)
├── data/
�?  ├── raw/                   # ENSDF source files, XREF labels, and legacy raw assets
�?  └── json/                  # Standardized JSON datasets consumed by the pipeline
├── outputs/
�?  ├── figures/               # PNG outputs (Diagnostics, Level Schemes)
�?  ├── clustering/            # Final cluster memberships (Text reports)
�?  └── pairwise/              # Pairwise match probabilities
├── docs/                      # Documentation and technical reports
├── scripts/
�?  ├── hyperparameter_tuning/ # Optimization and validation utilities
�?  └── legacy/                # Experimental/archive scripts
└── README.md                  # This file
```

---

## 7. Workflow & Usage

1. **Parse raw ENSDF files**: Run `Dataset_Parser.py` to scan all `.ens` files in `data/raw/` and write standardized JSON datasets to `data/json/`.
2. **Select inference targets**: In `Level_Matcher.py`, set `inference_dataset_labels` to the dataset letters you want to match.
3. **Train on synthetic physics data**: `Level_Matcher.py` generates synthetic labeled examples and trains XGBoost with early stopping and monotonic constraints.
4. **Run real-world inference**: The trained model evaluates all cross-dataset level pairs for the selected real datasets and writes probabilities to `outputs/pairwise/`.
5. **Cluster matched levels**: The graph algorithm consolidates pairwise matches into physically consistent level groups.
6. **Review outputs**: `Combined_Visualizer.py` renders the standardized JSON datasets and clustering results for visual audit.

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
**Version**: 2.1 (Dual Gradient Boosting Architecture)
