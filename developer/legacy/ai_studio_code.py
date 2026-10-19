import numpy as np

# Configuration: Default uncertainties if none provided in dataset
DEFAULT_ENERGY_UNC = 1.0  # keV
DEFAULT_INTENSITY_REL_UNC = 0.10  # 10% relative uncertainty if missing

def calculate_gamma_decay_pattern_similarity(gamma_decays_1, gamma_decays_2):
    """
    Calculates gamma decay pattern similarity using Subset-Robust Statistical Compatibility.
    
    Addresses specific nuclear physics constraints:
    1. Subset Matching: Handles "3 gammas vs 20 gammas" cases. If the smaller set 
       matches the larger set, score is 1.0.
    2. Swapped Intensities: Uses statistical Z-tests. If Intensity A (80±10) and 
       Intensity B (100±10) are consistent (Z<2), they count as a full match despite 
       rank swapping.
    3. Missing Data: Automatically switches to Binary Mode (Energy only) if intensities 
       are absent.

    Input Format: List of dicts
    [{'energy': 1000.0, 'energy_uncertainty': 1.0, 'intensity': 100.0, 'intensity_uncertainty': 5.0}, ...]
    """
    
    # --- Guard Clause: Missing Data ---
    if not gamma_decays_1 or not gamma_decays_2:
        return Scoring_Config['General']['Neutral_Score'] if 'Scoring_Config' in globals() else 0.5

    # =========================================================================
    # Phase 1: Data Standardization & Normalization
    # =========================================================================
    def process_spectrum(raw_decays):
        clean_list = []
        has_intensity = False
        max_intensity = 0.0
        
        for g in raw_decays:
            # 1. Extract Energy & Uncertainty
            e = float(g.get('energy', 0))
            if e <= 0: continue
            
            # Default Energy Uncertainty: 1.0 keV if missing/zero
            dE = float(g.get('energy_uncertainty', 0))
            if dE <= 0: dE = DEFAULT_ENERGY_UNC
            
            # 2. Extract Intensity & Uncertainty
            i = float(g.get('intensity', 0))
            dI = float(g.get('intensity_uncertainty', 0))
            
            if i > 0:
                has_intensity = True
                # Default Intensity Uncertainty: 10% of value if missing
                if dI <= 0: dI = max(0.5, i * DEFAULT_INTENSITY_REL_UNC)
                if i > max_intensity: max_intensity = i
            
            clean_list.append({'E': e, 'dE': dE, 'I': i, 'dI': dI})
            
        # 3. Normalize Strongest to 100.0 (Preserving relative errors)
        if has_intensity and max_intensity > 0:
            scale = 100.0 / max_intensity
            for g in clean_list:
                g['I'] *= scale
                g['dI'] *= scale # Scale error linearly
                
        return clean_list, has_intensity

    list_A, has_int_A = process_spectrum(gamma_decays_1)
    list_B, has_int_B = process_spectrum(gamma_decays_2)
    
    if not list_A or not list_B:
        return Scoring_Config['General']['Neutral_Score'] if 'Scoring_Config' in globals() else 0.5

    # =========================================================================
    # Phase 2: Helper Math Functions
    # =========================================================================
    def get_z_score(val1, err1, val2, err2):
        """Calculates statistical separation (sigma distance)."""
        sigma = np.sqrt(err1**2 + err2**2)
        if sigma == 0: sigma = 1.0
        return abs(val1 - val2) / sigma

    # Thresholds
    Z_ENERGY_MATCH = 3.0       # Energy match allowed up to 3 sigma
    Z_INTENSITY_CONSISTENT = 2.0 # Intensities considered "Same" if < 2 sigma

    # =========================================================================
    # Phase 3: Mode Selection & Calculation
    # =========================================================================
    
    # -------------------------------------------------------------------------
    # MODE A: INTENSITY MODE (Subset-Robust Bray-Curtis)
    # Used when both datasets have intensity data.
    # -------------------------------------------------------------------------
    if has_int_A and has_int_B:
        intersection_sum = 0.0
        matched_indices_B = set()
        
        for gA in list_A:
            best_match_idx = -1
            best_z_energy = 999.0
            
            # Greedy Search: Find best statistical energy match in B
            for idx, gB in enumerate(list_B):
                if idx in matched_indices_B: continue
                
                z_E = get_z_score(gA['E'], gA['dE'], gB['E'], gB['dE'])
                
                if z_E <= Z_ENERGY_MATCH:
                    if z_E < best_z_energy:
                        best_z_energy = z_E
                        best_match_idx = idx
            
            # Process Match
            if best_match_idx != -1:
                gB = list_B[best_match_idx]
                matched_indices_B.add(best_match_idx)
                
                # --- STATISTICAL INTENSITY LOGIC ---
                # Check if intensities are consistent within errors
                z_I = get_z_score(gA['I'], gA['dI'], gB['I'], gB['dI'])
                
                if z_I <= Z_INTENSITY_CONSISTENT:
                    # Case: Swapped Intensities / Fluctuations (e.g. 80±10 vs 100±10)
                    # Physics: They are statistically identical. 
                    # Action: Use the AVERAGE to represent the "True" shared intensity.
                    # This yields high overlap (~90-100) instead of penalizing min (~80).
                    overlap = (gA['I'] + gB['I']) / 2.0
                else:
                    # Case: Real Mismatch (e.g. 100 vs 5)
                    # Physics: Disagreement. 
                    # Action: Use MINIMUM to capture only the undisputed signal.
                    overlap = min(gA['I'], gB['I'])
                
                intersection_sum += overlap

        # --- SUBSET SCORING FORMULA ---
        # Instead of dividing by (Sum_A + Sum_B), we divide by 2 * Min(Sum_A, Sum_B).
        # Why? If A has total intensity 180, and B has 2000 (many extra gammas),
        # matching 180 means A is perfectly contained in B. Score should be 1.0.
        
        sum_A = sum(g['I'] for g in list_A)
        sum_B = sum(g['I'] for g in list_B)
        
        if min(sum_A, sum_B) == 0: return 0.0
        
        # Cap at 1.0 (in case average logic slightly exceeds min sum due to noise)
        score = intersection_sum / min(sum_A, sum_B)
        return min(score, 1.0)

    # -------------------------------------------------------------------------
    # MODE B: BINARY MODE (Statistical Overlap Coefficient)
    # Used when energies are known but intensities are missing/unreliable.
    # -------------------------------------------------------------------------
    else:
        matches_count = 0
        matched_indices_B = set()
        
        for gA in list_A:
            for idx, gB in enumerate(list_B):
                if idx in matched_indices_B: continue
                
                z_E = get_z_score(gA['E'], gA['dE'], gB['E'], gB['dE'])
                
                if z_E <= Z_ENERGY_MATCH:
                    matches_count += 1
                    matched_indices_B.add(idx)
                    break
        
        # Subset Logic: Match Count / Size of Smaller Dataset
        min_len = min(len(list_A), len(list_B))
        if min_len == 0: return 0.0
        
        return float(matches_count) / float(min_len)