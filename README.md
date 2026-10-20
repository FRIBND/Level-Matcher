# Level-Matcher

Part of the AI/ML Technical Innovation at the Nuclear Data Group at the Facility for Rare Isotope Beams (nucleardata@frib.msu.edu).

Supported by the U.S. Department of Energy, Office of Science, Office of Nuclear Physics under Award Number DE-SC0016948.

Open-source release: [github.com/FRIBND/Level-Matcher](https://github.com/FRIBND/Level-Matcher)

## Overview

The first Physics-informed Machine Learning nuclear level matching tool designed for Evaluated Nuclear Structure Data File (ENSDF) workflows.

Developed and refined through daily evaluation tasks at the Nuclear Data Group at the Facility for Rare Isotope Beams (FRIB).

Built on the open-source XGBoost (Extreme Gradient Boosting) framework, **Level-Matcher** employs physics-informed feature engineering, gradient boosting model training, and pairwise inference to reconcile nuclear level schemes across multiple datasets.

Integration roadmap: A production version will be incorporated into the Consistency Check and Evaluation Toolkit Java codes as part of the NSDD ENSDF Analysis and Utility Programs.

## Requirements and Installation

Level-Matcher runs on Python 3.11 (64-bit). The graphical interface itself needs only the Python standard library; the programs it starts in `engine/` need the scientific stack, including **XGBoost**. It has been tested on Windows 10/11; the programs in `engine/` are plain Python and also run on Linux and macOS from the command line (untested).

1. Install Python 3.11 (64-bit) from [python.org](https://www.python.org/downloads/) and confirm that `python` and `pip` work:
   ```bash
   python --version
   pip --version
   ```
2. Install the runtime dependencies (XGBoost is required for the matcher):
   ```bash
   pip install xgboost pandas numpy scikit-learn matplotlib
   ```
   or install from the bundled list, which holds the same runtime packages plus the developer extras:
   ```bash
   pip install -r requirements.txt
   ```
3. Only for the developer tools in `developer/` (schema validation):
   ```bash
   pip install jsonschema referencing
   ```
4. Check the installation:
   ```bash
   python -c "import pandas, numpy, xgboost, sklearn, matplotlib; print(xgboost.__version__)"
   ```

Tested versions: pandas 2.3.3, numpy 2.2.6, xgboost 3.1.2, scikit-learn 1.8.0, matplotlib 3.10.3, jsonschema 4.25.1, referencing 0.36.2.

Without XGBoost the matcher stops with `ModuleNotFoundError: No module named 'xgboost'`, which appears in the log panel.

## Quick Start (users)

Level-Matcher ships as one program that users run: `Level_Matcher_GUI.py`. The programs in `engine/` are started by that window and need no attention.

1. Install Python 3.11 and the dependencies first (see **Requirements and Installation** above).
2. Start the interface: `python Level_Matcher_GUI.py` (from this folder), or create a shortcut that runs that command.
3. **Browse** to the folder that holds your `.ens` files. It must contain the adopted file: its X records provide the dataset letters.
4. Select two or more datasets in the table (Ctrl or Shift for several), optionally adjust **Parameters**, then click **Run Level Matcher**.

Everything the tool writes - parsed JSON datasets, the pair report, the cluster report, `parameters.json` and the optional PNG figures - goes into one flat folder `level_matcher_files` inside the `raw` folder beside the folder you browsed to (browsing `S34/new` writes to `S34/raw/level_matcher_files`). Nothing is ever created or changed inside the folder holding your `.ens` files.

## Development Timeline

- 2026-04-19: Tested and refined on real-world ENSDF datasets <sup>34</sup>Cl, <sup>34</sup>S.
- 2026-04-17: Introduced configurable parameters for feature correlation, model training diagnostics, and validation metrics.
- 2026-01-05: End-to-end pipeline integration with five core components: dataset parsing, physics-informed feature extraction, synthetic feature-label generation, XGBoost model training and pairwise inference, constrained graph partitioning, and level-scheme visualization.
- 2025-10-20: Foundational prototype: decision tree-based ranking framework (LightGBM) at [github.com/sunlijie-msu/Level-Matcher](https://github.com/sunlijie-msu/Level-Matcher).

## High-Level Structure and Workflow Explanation

Inference-first pipeline: raw ENSDF records → standardized JSON → synthetic-trained XGBoost → pairwise probabilities → clique-constrained unified level scheme.

```text
[Raw ENSDF] --> [Dataset_Parser] --> [parsed JSON datasets]
                                         |
                                         v
[Feature_Engineer] --> [5-D Feature Extraction, Scoring_Config, Label Logic]
        |                            |
        v                            v
[Synthetic Training Data]    [Pairwise Inference]
        |                            |
        v                            v
[XGBoost Training] <--- [Level_Matcher] --> [level_matcher_files/]
        |                            |
        v                            v
[Training_Metrics_Visualizer]  [Level_Clusterer] --> [level_matcher_files/]
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

### 3.4 Training Configuration

`engine/Level_Matcher.py` trains one XGBoost regressor with `objective='binary:logistic'` and monotone-increasing constraints on all five features (`monotone_constraints='(1, 1, 1, 1, 1)'`), so a higher feature score can never lower the predicted probability.

| Setting | Value | Purpose |
| :--- | :--- | :--- |
| `n_estimators` | 2000 | upper bound; early stopping decides the actual number of trees |
| `max_depth` | 5 | captures veto, rescue and specificity interactions |
| `learning_rate` | 0.02 | shrinkage per tree |
| `reg_lambda` | 3.0 | L2 regularization on leaf weights |
| `subsample` | 0.8 | row sampling per tree |
| `min_child_weight` | 5 | minimum leaf sample weight |
| `early_stopping_rounds` | 50 | stops when validation RMSE stops improving |
| `random_state` | 42 | reproducible split and training |
| `test_size` | 0.2 | validation share of the synthetic set |

The training diagnostic figure (RMSE, MAE and LogLoss comparison, feature gain, overfit gap) is written only when **Generate figures** is enabled.

---

## 4. Physics-Informed Feature Engineering

`Feature_Engineer.py` converts every candidate level pair into a 5-D feature vector (ordered: Energy, Spin, Parity, Specificity, Gamma). All scores live in $[0,1]$ and are monotonic (higher = stronger match evidence):

1. **Energy_Similarity**: Gaussian kernel $\exp\left(-\sigma_{\text{scale}} \cdot Z^2\right)$ with $Z = \frac{|E_1 - E_2|}{\sqrt{\sigma_1^2 + \sigma_2^2}}$. `Sigma_Scale` = 0.5 (default: 1σ→60.7%, 2σ→13.5%, 3σ→1.1%). Missing energy → 0.0.
2. **Spin_Similarity**: Optimistic max over all spin pairings. Same spin, both firm→1.0, same spin, any tentative→0.8, spin differs by 1, any tentative→0.2, spin differs by 1, both firm→0.05, spin differs by 2 or more→0.0.
3. **Parity_Similarity**: Optimistic max over all parity pairings. Same firm→1.0, same tentative→0.8, opposite tentative→0.05, opposite firm→0.0.
4. **Specificity**: Ambiguity penalty $1/f(\text{multiplicity})$, where multiplicity = product of both levels' spin-option counts. Selectable formulas: `sqrt` (default), `linear`, `log`, `tunable`.
5. **Gamma_Decay_Pattern_Similarity**: Greedy one-to-one γ-energy matching within 3σ. Intensity mode: overlap = average when intensities are consistent ($Z \le 3\sigma$), minimum when inconsistent; sum is normalized by the smaller total intensity (subset-robust). Falls back to energy-only match counting when intensities are missing.

Missing spin/parity/gamma data → `Neutral_Score` = 0.5.

### 4.1 Label Logic (Veto, Rescue, and Final Formula)

`calculate_label()` maps the 5 raw scores to a match probability via three adjustments plus a final product:

- **Neutral Remap**: neutral spin/parity/gamma 0.5 → 0.85 (`Neutral_Remap_Factor`), preventing collapse $0.5^3 = 0.125$; energy and specificity untouched.
- **Physics Veto**: spin $\le$ 0.04 or parity $\le$ 0.04 (definitive quantum mismatch, `Spin_Veto_Max`/`Parity_Veto_Max`) → label 0.0.
- **Physics Rescue**: (spin ≥ 0.86 and parity ≥ 0.86) or gamma ≥ 0.86 → energy similarity$^{0.5}$ (calibration-offset rescue). Raw scores tested, so "unknown" never triggers.
- **Final Formula**: $\text{label} = \text{clamp}\left(E_{\text{eff}} \cdot \sqrt{S_{\text{eff}} \cdot P_{\text{eff}} \cdot G_{\text{eff}}} \cdot \text{Specificity},\ 0,\ 0.999\right)$

### 4.2 Parameter Defaults

Every value below is editable in the **Parameters** dialog. The defaults are read from the top of `engine/Feature_Engineer.py` and `engine/Level_Matcher.py`; the dialog checks the range and stores only values changed from the defaults in `%APPDATA%\Level_Matcher\settings.json`.

| Section | Parameter (dialog label) | Key | Default | Range |
| :--- | :--- | :--- | :--- | :--- |
| Feature Engineering | Energy kernel width (larger = stricter) | `Scoring_Config.Energy.Sigma_Scale` | 0.5 | 0.001 to 10 |
| | Same spin, both firm | `Scoring_Config.Spin.Match_Firm` | 1.0 | 0 to 1 |
| | Same spin, any tentative | `Scoring_Config.Spin.Match_Strong` | 0.8 | 0 to 1 |
| | Spin differs by 1, any tentative | `Scoring_Config.Spin.Mismatch_Weak` | 0.2 | 0 to 1 |
| | Spin differs by 1, both firm | `Scoring_Config.Spin.Mismatch_Strong` | 0.05 | 0 to 1 |
| | Spin differs by 2 or more | `Scoring_Config.Spin.Mismatch_Firm` | 0.0 | 0 to 1 |
| | Same parity, both firm | `Scoring_Config.Parity.Match_Firm` | 1.0 | 0 to 1 |
| | Same parity, any tentative | `Scoring_Config.Parity.Match_Strong` | 0.8 | 0 to 1 |
| | Opposite parity, any tentative | `Scoring_Config.Parity.Mismatch_Weak` | 0.05 | 0 to 1 |
| | Opposite parity, both firm | `Scoring_Config.Parity.Mismatch_Firm` | 0.0 | 0 to 1 |
| | Formula | `Scoring_Config.Specificity.Formula` | sqrt | sqrt, linear, log, tunable |
| | Alpha (tunable formula only) | `Scoring_Config.Specificity.Alpha` | 0.5 | 0 to 100 |
| | Missing-data score (0.5 to 1) | `Scoring_Config.General.Neutral_Remap_Factor` | 0.85 | 0.5 to 1 |
| | Spin veto (reject if score is at most) | `Scoring_Config.Label.Spin_Veto_Max` | 0.04 | 0 to 1 |
| | Parity veto (reject if score is at most) | `Scoring_Config.Label.Parity_Veto_Max` | 0.04 | 0 to 1 |
| | Enabled | `Scoring_Config.Feature_Correlation.Enabled` | on | on or off |
| | Score that triggers rescue | `Scoring_Config.Feature_Correlation.Rescue_Threshold` | 0.86 | 0 to 1 |
| | Energy boost exponent (1 = none) | `Scoring_Config.Feature_Correlation.Rescue_Exponent` | 0.5 | 0.1 to 1 |
| Level Matcher | Pair report minimum probability | `pairwise_output_threshold` | 0.01 | 0 to 1 |
| | Cluster merge minimum probability | `clustering_merge_threshold` | 0.15 | 0 to 1 |
| | Generate figures | `generate_figures` | on | on or off |

---

## 5. Graph-Based Clustering Algorithm

`Level_Clusterer.py` runs deterministic constrained graph partitioning: candidate pairs with probability at or above the clustering merge threshold (default 0.15, set under Parameters) are processed greedily in descending order.
- **Dataset Uniqueness**: Each cluster holds at most one level per dataset; ambiguous levels may join multiple clusters.
- **Mutual Consistency (Clique)**: Clusters merge only when every cross-member pair clears the threshold — blocks weak "chain" merges.
- **Anchor Selection**: Cluster anchor = member with smallest $\sigma_E$; clusters are sorted by average energy and written to `level_matcher_files/`.

---

## 6. Project Structure

```text
Level-Matcher/
├── Level_Matcher_GUI.py       # The only program users run (graphical interface)
├── requirements.txt           # Python dependencies
├── engine/                    # Programs the interface runs; not edited by users
│   ├── Dataset_Parser.py      # ENSDF .ens to JSON converter
│   ├── Feature_Engineer.py    # Physics engine (feature extraction, label logic, Scoring_Config)
│   ├── Level_Matcher.py       # Main orchestration (training, inference, clustering)
│   ├── Level_Clusterer.py     # Deterministic clique-constrained graph partitioning
│   ├── Combined_Visualizer.py # Level scheme and cluster table figures
│   └── Training_Metrics_Visualizer.py  # Training diagnostics figure
├── assets/                    # Window icon and logo
├── docs/                      # User and technical documentation
├── developer/                 # Developer material, not needed to use the tool
│   ├── Run_Pipeline.ps1       # Sample-data pipeline; detached matcher for VS Code safety
│   ├── validate_schema.py     # Checks parser output against ENSDF JSON Schema 1.0.0
│   ├── angular_momentum_coupling.py      # Allowed spin-parity calculator (standalone helper)
│   ├── hyperparameter_tuning/ # Tuner, visualizer and their result files
│   ├── legacy/                # Archived models and experiments
│   ├── examples/              # Illustrative figures
│   ├── reference/consistency/ # Java Consistency Check sources (integration roadmap)
│   ├── reports/               # Development reports
│   └── data/                  # Sample data: raw/ (.ens and XREF.txt), json/, ensdf_schema_1.0.0/
├── level_matcher_files/       # Created by developer/Run_Pipeline.ps1 (generated, not tracked)
├── README.md                  # This file
└── LICENSE
```

`level_matcher_files/` and `developer/data/json/` are generated at run time and are ignored by git.

---

## 7. Workflow & Usage

### 7.1 Users (graphical interface only)

`python Level_Matcher_GUI.py`. Browse to any folder holding `.ens` files; the folder must include the adopted file, whose X records provide the dataset letters (a folder without it is refused). Then select two or more datasets, optionally change the feature engineering (scoring) and level matcher (threshold) parameters under **Parameters** (the defaults are the values at the top of `engine/Feature_Engineer.py` and `engine/Level_Matcher.py`; figure layout and fonts are set at the top of `engine/Combined_Visualizer.py`), then click Run Level Matcher. Everything the tool writes goes into one folder, `raw/level_matcher_files/`, created beside the folder you browsed to (for `S34/new` it is `S34/raw/level_matcher_files/`, no subfolders; nothing is ever created or changed inside the folder you browsed to): the parsed JSON datasets, the pair report, the cluster report and the parameters used (`parameters.json`). The PNG figures (large files) are written unless **Generate figures** is unticked under **Parameters** (on by default; the choice is remembered between sessions).

### 7.2 Developers (command line)

1. **Run full pipeline (sample data)**: `.\developer\Run_Pipeline.ps1 -Labels L,Q,F` executes `engine/Dataset_Parser.py` → `engine/Level_Matcher.py` (detached subprocess; logs stream to `level_matcher_files/run_log.txt`) → `engine/Combined_Visualizer.py`. Results go to `level_matcher_files/` in the repository root (the graphical interface writes to `raw/level_matcher_files/` beside the folder you browsed to). Detached spawn prevents VS Code freezes.
2. **Parse raw ENSDF files**: `python engine/Dataset_Parser.py` scans all `.ens` files in `developer/data/raw/` and writes standardized JSON datasets to `developer/data/json/`.
3. **Select inference targets**: pass the XREF letters of the datasets with `--labels` (the graphical interface does this from your selection; `developer/Run_Pipeline.ps1` takes `-Labels L,Q,F`).
4. **Train on synthetic physics data**: `engine/Level_Matcher.py` generates synthetic labeled examples and trains XGBoost with early stopping and monotonic constraints.
5. **Run real-world inference**: All cross-dataset level pairs for the selected datasets are scored; pairs at or above the output threshold are written to `level_matcher_files/Output_Level_Pairwise_Inference.txt`.
6. **Cluster matched levels**: `engine/Level_Clusterer.py` consolidates pairwise matches into clique-consistent clusters written to `level_matcher_files/Output_Clustering_Results_XGBoost.txt`.
7. **Review outputs**: `engine/Combined_Visualizer.py` renders level schemes and the cluster table to `level_matcher_files/` for visual audit (graphical interface: unless **Generate figures** is unticked under **Parameters**).
8. **Validate parser output**: `python developer/validate_schema.py --json_dir <folder>` checks every dataset JSON file against ENSDF JSON Schema 1.0.0.
9. **Developer dependencies**: schema validation needs `pip install jsonschema referencing`; nothing else beyond the runtime list is required.

### 7.3 Inputs

- One folder holding ENSDF `.ens` files, including the **adopted file**. The dataset letters (XREF) are read from the adopted file's X records; the graphical interface refuses a folder without it.
- Each `.ens` file is matched to its XREF letter through the DSID field of its identification record. Files whose DSID matches no XREF entry are skipped and listed in the log; the adopted file itself is never matched.
- L records become levels, G records become gamma transitions. Uncertainties are read from the ENSDF digit notation, relative intensities are normalized to 100, and `LT`/`GT` intensity limits are kept as limits.
- At least two datasets must be selected for cross-dataset matching.

### 7.4 Outputs and Report Formats

The tool writes into one flat folder, `level_matcher_files`, inside the `raw` folder beside the browsed folder (browsed `S34/new` gives `S34/raw/level_matcher_files`). No subfolders are created and nothing is written into the browsed folder.

| File | Content |
| :--- | :--- |
| `{nuclide}_{XREF text}.json` | Parsed datasets in ENSDF JSON Schema 1.0.0 format, one file per `.ens` file |
| `Output_Level_Pairwise_Inference.txt` | Every level pair at or above the pair report threshold, one row per pair |
| `Output_Clustering_Results_XGBoost.txt` | Clusters with anchor, members and match probabilities |
| `parameters.json` | Parameter values used by the run (nested like the script variables) |
| `Training_Metrics_Diagnostic.png` | Training diagnostics (only with **Generate figures**) |
| `Input_Level_Scheme.png` | Level scheme of the selected datasets (only with **Generate figures**) |
| `Output_Cluster_Scheme_XGBoost.png` | Cluster table figure (only with **Generate figures**) |

The pair report lists one pair per row, a blank line between pairs:

```text
=== PAIRWISE INFERENCE RESULTS (>=1.0%) ===
Model: XGBoost
Total Level Pairs Found: 3130

L_11545 <-> Q_11541 | Match Prob.: 84.0% | Features: Energy_Similarity=0.85, Spin_Similarity=1.00, Parity_Similarity=1.00, Specificity=1.00, Gamma_Pattern_Similarity=0.50
```

The cluster report shows the merge threshold in its header, then one block per cluster:

```text
=== FINAL CLUSTERING RESULTS: XGBoost (Merge Threshold: >=15%) ===
Total Clusters: 125

Cluster 1:
  Anchor: L_2805 | E=2805.0±3.0 keV | Spin-Parity=2+
  Members:
    [L] L_2805: E=2805.0±3.0 keV, Spin-Parity=2+ (Anchor)
    [Q] Q_2806: E=2806.3±0.9 keV, Spin-Parity=2+ (Match Probability: 97.4%)
```

The anchor is the member with the smallest energy uncertainty; members other than the anchor show the predicted match probability to the anchor.

### 7.5 Settings File

The graphical interface remembers the last folder and the parameter values changed from the defaults in `%APPDATA%\Level_Matcher\settings.json` (`last_workspace`, and a `parameters` object that also holds `generate_figures`). Delete that file, or press **Reset to Defaults** in the dialog, to return to the defaults.

---

## 8. Development & Testing Standards

### 8.1 Mandatory Runtime Validation
After any code modification:
1.  Run `.\developer\Run_Pipeline.ps1 -Labels L,Q` (parser, detached matcher, visualizer) and confirm it finishes without errors; do not run `engine/Level_Matcher.py` directly in the VS Code terminal.
2.  Check `level_matcher_files/Training_Metrics_Diagnostic.png` for overfitting flags.
3.  Check the other figures in `level_matcher_files/` for visual output integrity.
4.  Run the graphical interface on a temporary copy of a dataset folder to confirm the user workflow (scan, run, figures on and off).

### 8.2 Coding Protocols
- **No Acronyms**: Variables must be fully spelled out (e.g., `energy_uncertainty` not `ener_uncert`).
- **No ALL CAPS**: Use `Title_Case` or `snake_case` for all variables and constants.
- **Header Requirement**: Every script must lead with a High-level Structure and Workflow Explanation.

---
**Maintained by**: FRIB Nuclear Data Group  
**Status**: [In Development]
**Version**: 0.3.2 (XGBoost Architecture)
