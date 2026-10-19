# Machine Learning Hierarchy and Implementation Strategy for Nuclear Level Matching

## Level 1: The Foundation
### Decision Tree

- **Concept:** A non-parametric supervised method that predicts targets by learning simple if-then-else rules from features.

- **Core Components:**
  - **Root Node:** The starting point containing all data.
  - **Decision Node (Internal Node):** Where data is split based on a feature's value.
  - **Branches:** Connect nodes and represent decision outcomes.
  - **Leaf Node (Terminal Node):** Final outcomes or predictions.
  - **Splitting:** The process of dividing a node into two sub-nodes.

- **CART (Classification and Regression Trees):** Algorithm that builds trees using binary splits to minimize impurity (Gini impurity for classification, mean squared error for regression).

- **Characteristics:** Low bias and high variance. Deep trees are sensitive to small changes and can overfit.

- **Role:** The base estimator in ensemble methods.


## Level 2: The Strategy

Ensemble Learning: Reduce variance (Bagging) or bias (Boosting) by combining multiple weak learners.

### Strategy A: Bagging (Bootstrap Aggregating)

- **Logic:** Parallel execution to reduce variance.
- **Mechanism:** Generates M datasets via random sampling with replacement (bootstrap). Trains M independent trees. Final prediction is the average (regression) or majority vote (classification).
- **Key Algorithm:** Random Forest (Breiman, 2001).
- **Physics Limitation:** Averaging fails for "Hard Vetoes." If 1 tree detects a fatal Spin Mismatch (Probability approximately 0) but 99 trees see an Energy Match (Probability approximately 1), the average remains high (approximately 0.99). Physics requires the single veto to drive probability to 0.

### Strategy B: Boosting

- **Logic:** Sequential execution to reduce bias.
- **Mechanism:** Iterative improvement. Tree $m$ is trained to minimize errors (loss) of ensemble $F_{m-1}$.
- **Strength for Physics:** Mimics a "Veto" system. If Tree 1 predicts a match, Tree 2 can detect a specific violation (e.g., Parity Mismatch) and output a large negative correction, effectively suppressing match probability.

## Level 3: The Algorithm

Mathematical frameworks for implementing Boosting.

### Algorithm A: AdaBoost (Adaptive Boosting)

- **Mechanism:** Sample Reweighting. At step $m$, it increases the weights of misclassified observations.
- **Reference:** Freund and Schapire (1997).
- **Verdict:** Reject. Sensitive to noisy data both theoretically and empirically. In nuclear data, experimental outliers (large errors) receive exponential weight, causing the model to fixate on anomalies rather than general trends.

### Algorithm B: GBM (Gradient Boosting Machine)

- **Mechanism:** Functional Gradient Descent. At step $m$, a new tree is trained to predict the negative gradient (pseudo-residuals) of the loss function. It fits the error, not the data.
- **Reference:** Friedman (2001).
- **Verdict:** Superior. Optimizing differentiable loss functions (e.g., Log-Loss) makes it more robust to outliers than the exponential loss used in AdaBoost.

## Level 4: The Package (Software Libraries)

Major libraries implementing Gradient Boosting.

| Package | NaN Handling | Growth Strategy | Best Data Scale | Weakness for Physics | Verdict |
|---------|--------------|-----------------|-----------------|----------------------|---------|
| Scikit-learn GradientBoosting | Fails (Crashes) | Level-wise | Small | Requires imputation (bias risk); slow; lacks regularization. | Reject |
| LightGBM | Native (Safe) | Leaf-wise | Huge (>100k) | "Greedy" growth overfits small data; creates unbalanced trees. | Reject |
| Scikit-learn HistGradientBoosting | Native (Safe) | Leaf-wise | Medium/Large | Less tunable regularization than XGBoost; defaults to greedy growth. | Acceptable |
| XGBoost | Native (Safe) | Level-wise | Any | Minimal. Level-wise growth and L1/L2 regularization ideal for stability. | Best |
| CatBoost | Native (Safe) | Symmetric | Medium/Large | Slower training for pure numerical tasks; heavier dependency. | Alternative |


Guolin Ke et al., LightGBM: A Highly Efficient Gradient Boosting Decision Tree,
Advances in Neural Information Processing Systems 30, 3149 (2017).
https://proceedings.neurips.cc/paper/2017/hash/6449f44a102fde848669bdd9eb6b76fa-Abstract.html

Tianqi Chen and Carlos Guestrin, XGBoost: A Scalable Tree Boosting System
Proceedings of the 22nd ACM SIGKDD International Conference on Knowledge Discovery and Data Mining, 785 (2016).
https://doi.org/10.1145/2939672.2939785


#### Growth Strategies

- **Leaf-wise Growth (LightGBM / HistGBDT):** Splits the single leaf with the highest error, regardless of tree balance. Can grow deep, lopsided, overfitted trees that "memorize" outliers in small datasets.
- **Level-wise Growth (XGBoost / Legacy GBDT):** Grows the tree layer-by-layer. Produces balanced trees, acting as a natural regularizer against experimental noise.


*   **The Challenge:** Nuclear datasets are inherently sparse; Level scheme is always incomplete and measured quantities sometimes have large uncertainties. Spin ($J$) and Parity ($\pi$) assignments are frequently unknown or tentative or many possibilities.

### 1. Native Handling of Missing Values (Sparsity Awareness)
*   **The XGBoost Solution:** XGBoost utilizes **Sparsity-Aware Split Finding**. Unlike legacy algorithms that fail on `NaN` or require bias-inducing imputation (guessing), XGBoost explicitly learns optimal default directions at tree split for `NaN ` based on other training labels. It treats "Unknown" as a distinct state, preserving the ambiguity inherent in experimental data.

### 2. Prevention of Overfitting on Small Datasets (Regularization)
*   **The Challenge:** Nuclear level schemes represent "small data" (typically $N < 500$ levels). Algorithms like LightGBM, designed for massive datasets, tend to grow deep, greedy trees that "memorize" experimental noise.
*   **The XGBoost Solution:** XGBoost defaults to **Level-wise growth**, constructing balanced and conservative trees. Combined with native **L1 (Lasso) and L2 (Ridge) regularization**, this architecture prevents overfitting and ensures the model captures general physics trends rather than statistical fluctuations.

### 3. Integration of Physical Logic with Statistical Matching

*   **The Challenge:** Nuclear level matching is not merely a numerical optimization problem; it is constrained by strict physical laws. Standard models often treat logical constraints (e.g., selection rules) as "soft" statistical features, potentially allowing a precise energy match to override a fatal physics violation (e.g., matching $J^\pi = 3^+$ with $4^-$).
*   **The XGBoost Solution:**
    *   **Statistical Compliance (Monotonicity):** A larger energy deviation penalizes the match probability, reflecting the statistical nature of experimental uncertainties.
    *   **Logical Compliance (Hard Vetoes):** As a sequential learner, XGBoost creates a hierarchy of decisions. It can learn that specific physical violations (like Parity Mismatch) act as absolute vetoes that nullify the probability, regardless of how perfect the energy agreement is.


## Level 5: The Implementation

### Data Partitioning Strategy

The training pipeline uses three distinct datasets to ensure robust model development:

| Dataset | Size | Purpose | Model Interaction |
|---------|------|---------|-------------------|
| **Training Set** | ~17,758 (80%) | Model **learns** from these samples | Weights updated via gradient descent |
| **Validation Set** | ~4,440 (20%) | Monitor generalization **during training** | Evaluated but weights NOT updated |
| **Test Sets (A/B/C)** | ~30 real levels | Final inference (production use) | Never seen during any training phase |

**Critical Distinction**: The validation set is a held-out portion of synthetic training data used to monitor overfitting during model training via early stopping. The test sets (A/B/C) contain real experimental nuclear data and are used exclusively for production inference after training completes.

**Training Workflow**:
1. Synthetic data generation produces 22,198 labeled examples
2. Random 80/20 split creates training and validation subsets
3. Model trains on 80%, evaluates on 20% each iteration
4. Early stopping triggered when validation error stops improving
5. Final model deployed on real test datasets (A/B/C)

### Diagnostic Metrics

Model training quality is assessed through the following metrics:

| Metric | Formula | Purpose | Target Range |
|--------|---------|---------|-------------|
| **RMSE** | $\sqrt{\frac{1}{n}\sum(y_{pred} - y_{true})^2}$ | Measures average prediction error (penalizes outliers) | <0.05 excellent, >0.3 poor |
| **MAE** | $\frac{1}{n}\sum\|y_{pred} - y_{true}\|$ | Average absolute deviation (robust metric) | <0.02 excellent, >0.2 poor |
| **LogLoss** | $-\frac{1}{n}\sum[y_{true} \log(y_{pred}) + (1-y_{true}) \log(1-y_{pred})]$ | Binary cross-entropy (primary calibration metric) | <0.3 excellent, >1.0 poor |
| **Feature Importance (Gain)** | $\sum_{splits} \Delta Loss$ | Average loss reduction per feature across all splits | Higher = more influential |
| **Iteration Count** | Training rounds before early stopping | Model complexity indicator | <70% of max = good generalization |

**Interpreting Results**:

```
XGBoost Training Complete (stopped at iteration 540/1000)
  Train RMSE: 0.0181 | Validation RMSE: 0.0192
  Train MAE:  0.0084 | Validation MAE:  0.0089
  Train LogLoss: 0.2015 | Validation LogLoss: 0.1986
```

**Analysis**:
- Train/Validation gap (0.0011 RMSE, 0.0005 MAE) is minimal → No overfitting
- Validation RMSE (0.0192) is excellent → Strong generalization
- Validation LogLoss (0.1986) < Training LogLoss (0.2015) → Exceptional generalization (rare but ideal)
- Stopped at 54% of max iterations → Optimal complexity without memorization

**Red Flags**:
- Large train/validation gap (>0.05): Model overfitting training noise
- High validation RMSE (>0.3): Poor feature engineering or excessive regularization
- High LogLoss (>1.0): Poor probability calibration or class imbalance issues
- Stopping at iteration 1: Regularization too aggressive (LightGBM example: RMSE 0.4958)

**LogLoss Deep Dive**: Binary cross-entropy measures how well predicted probabilities match true labels. Unlike RMSE which treats all errors equally, LogLoss severely penalizes confident wrong predictions (e.g., predicting 99% when truth is 0%). Lower LogLoss indicates better-calibrated probabilities, essential for ranking match candidates reliably. Implementation uses manual computation with prediction clipping to [1e-15, 1-1e-15] to avoid log(0) numerical errors.

**Feature Importance Deep Dive**: XGBoost's Gain metric measures total loss reduction when a feature is used for splits across all trees. Higher Gain means the feature provides more discriminative power. For nuclear data matching, expected hierarchy: Spin_Similarity (highest Gain) > Parity_Similarity > Energy_Similarity > Gamma_Decay_Pattern_Similarity > Specificity (lowest Gain), reflecting physics constraints where quantum numbers are more definitive than energies.

**Automated Visualization**: The engine/Training_Metrics_Visualizer.py module generates comprehensive diagnostic plots:
- 5-panel layout: RMSE comparison, MAE comparison, LogLoss comparison, Feature Importance (Gain), Overfitting Gap Analysis
- Color-coded quality indicators (green/yellow/red thresholds)
- Iteration count annotations showing convergence efficiency
- Reference lines for excellent (<0.05 RMSE, <0.3 LogLoss) and poor (>0.3 RMSE, >1.0 LogLoss) performance
- Feature Importance horizontal bar chart with Gain values sorted descending
- Output: `level_matcher_files/Training_Metrics_Diagnostic.png` (300 DPI publication quality)

### Feature Engineering

Feature engineering is the process of transforming raw data into meaningful input features that enable the machine learning model to detect patterns, relationships, and interactions. Feature engineering is one of the most critical steps in building high-performance tree-based models.

Well-engineered features can significantly boost model performance, leading to improved accuracy and predictive power. XGBoost is capable of handling complex data relationships.

engine/Feature_Engineer.py

### Model Configuration

engine/Level_Matcher.py