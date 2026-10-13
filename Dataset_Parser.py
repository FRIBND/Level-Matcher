"""
Dataset Parser for Nuclear Level Matcher
=========================================

# High-level Structure and Workflow Explanation:
======================================

Workflow Diagram:

  [data/raw/*.ens files]          [data/raw/XREF.txt]
         |                               |
         v                               v
  [Identification Record]     [ENSDF Markup Normalization]
  [DSID extraction]           [Letter -> Reaction Map]
         |                               |
         +---------------+---------------+
                         |
                         v
              [DSID -> Letter Matching]
                         |
              +----------+----------+
              |                     |
              v                     v
       [L Records]            [G Records]
    [Energy Levels]        [Gamma Transitions]
              |                     |
              +----------+----------+
                         |
                         v
          [Fixed-Width Column Slicing]
          [Spin-Parity Parsing]
          [Uncertainty Inference]
                         |
                         v
          [Standardized JSON Schema]
                         |
                         v
          [data/json/test_dataset_{X}.json]

# Technical Steps:
# 1. Read XREF.txt to build a letter-to-reaction mapping; strip ENSDF markup for normalized comparison.
# 2. Scan all .ens files in data/raw/; extract each file's DSID from its identification record (cols 9-38).
# 3. Match each DSID to its XREF letter using normalized string comparison.
# 4. Parse L records (energy levels) and G records (gamma transitions) using fixed-width column slicing;
#    skip continuation/annotation records (e.g., FLAG=H lines) that carry no level data.
# 5. Extract target nucleus ground state spin-parity and infer unspecified level spin-parities from
#    orbital angular momentum transfers (L-values) via quantum angular momentum coupling.
# 6. Infer uncertainties from precision where explicit uncertainty values are absent.
# 7. Output one test_dataset_{letter}.json per .ens file into data/json/ strictly complying with ENSDF JSON Schema 1.0.0
#    (level.json, level-energy.json, spin-parity.json, gamma.json, radiation-energy.json, radiation-intensity.json).
#
# Architecture:
# - `normalize_ensdf_text`: Strips ENSDF markup ({+N}, |g, etc.) for DSID-to-XREF matching.
# - `parse_xref_file`: Reads XREF.txt into a normalized letter-to-description dictionary.
# - `match_dsid_to_letter`: Maps a .ens file's DSID to its XREF dataset letter.
# - `infer_uncertainty_from_precision`: Heuristic engine for precision-based uncertainty estimation.
# - `extract_target_spin_parity`: Identifies target ground state spin-parity from comments or nuclear systematics.
# - `infer_spin_parity_from_angular_momentum_transfer`: Computes allowed level Jπ hypotheses via angular momentum coupling.
# - `parse_spin_parity_range`: Expands range expressions according to ENSDF spin-parity notation standards.
# - `parse_spin_parity`: Normalizes ENSDF Spin-Parity strings into structured spin/parity hypothesis lists complying with spin-parity.json.
# - `parse_g_record`: Slices G records into structured gamma transition data dictionaries.
# - `parse_ensdf_line`: Slices L records into structured level data dictionaries; returns None for annotation/continuation lines.
# - `convert_ens_files_to_datasets`: Main driver that scans .ens files, matches labels, and writes JSON conforming to JSON Schema 1.0.0.
"""

import json
import re
import os
import argparse
from decimal import Decimal, InvalidOperation
from fractions import Fraction

def infer_uncertainty_from_precision(value_string):
    """
    Infers uncertainty from reported precision in Evaluated Nuclear Structure Data File (ENSDF) evaluatorInput string.
    
    Nuclear physics convention: Uncertainty = 0.5 × least_significant_digit_place_value
    
    Examples:
    - "2000" → ±5 keV (integer, precision to 10s place)
    - "2.0E3" → ±500 keV (scientific notation, 1 decimal in mantissa)
    - "2.00E3" → ±50 keV (scientific notation, 2 decimals in mantissa)
    - "1234.5" → ±0.5 keV (1 decimal place)
    - "567.89" → ±0.05 keV (2 decimal places)
    
    Returns: Inferred uncertainty as float, or 5.0 (conservative default) if cannot parse.
    Uses exact base-10 Decimal arithmetic so JSON output remains numerically clean
    (for example, 1.7 instead of 1.7000000000000002).
    """
    if not value_string:
        return 5.0
    
    value_string = value_string.strip()
    
    # Handle scientific notation (e.g., "2.0E3" or "1.5e+02")
    if 'E' in value_string.upper():
        parts = value_string.upper().split('E')
        if len(parts) != 2:
            return 5.0
        
        mantissa = parts[0]
        try:
            exponent = int(parts[1])
        except ValueError:
            return 5.0
        
        # Count decimal places in mantissa (DO NOT strip trailing zeros)
        # Example: "2.0E3" has 1 decimal → ±500 keV
        # Example: "2.00E3" has 2 decimals → ±50 keV
        if '.' in mantissa:
            decimal_part = mantissa.split('.')[1]
            decimal_places = len(decimal_part)

            # Uncertainty: 5 × 10^(-decimal_places) × 10^exponent
            mantissa_uncertainty = Decimal('5').scaleb(-decimal_places)
            return float(mantissa_uncertainty.scaleb(exponent))
        else:
            # Integer mantissa: "2E3" → ±5×10^3 = ±5000
            return float(Decimal('5').scaleb(exponent))
    
    # Handle regular decimal notation
    if '.' in value_string:
        # Count decimal places WITHOUT stripping trailing zeros
        # Example: "2000.0" has 1 decimal place → precision to 0.1 keV → ±0.5 keV
        # Example: "1234.56" has 2 decimal places → precision to 0.01 keV → ±0.05 keV
        decimal_part = value_string.split('.')[1]
        decimal_places = len(decimal_part)
        return float(Decimal('5').scaleb(-decimal_places))
    else:
        # Integer: precision to nearest 10
        return 5.0

def parse_spin_parity_endpoint(endpoint_token_string):
    """
    Parses a single range endpoint token into (spin_fraction, parity_string, is_tentative_spin, is_tentative_parity).
    Handles forms like: "1/2+", "7/2-", "1/2(+)", "(1/2)+", "0", "2+".
    """
    token = endpoint_token_string.strip()
    is_tentative_spin = False
    is_tentative_parity = False
    parity = None

    if token.endswith('(+)'):
        parity = '+'
        is_tentative_parity = True
        token = token[:-3].strip()
    elif token.endswith('(-)'):
        parity = '-'
        is_tentative_parity = True
        token = token[:-3].strip()
    elif token.endswith('+'):
        parity = '+'
        token = token[:-1].strip()
    elif token.endswith('-'):
        parity = '-'
        token = token[:-1].strip()

    if token.startswith('(') and token.endswith(')'):
        is_tentative_spin = True
        token = token[1:-1].strip()

    try:
        spin_fraction = Fraction(token)
        return spin_fraction, parity, is_tentative_spin, is_tentative_parity
    except (ValueError, ZeroDivisionError):
        return None, None, False, False


def parse_spin_parity_range(range_string):
    """
    Parses ENSDF spin-parity range expressions (e.g., '1/2:7/2', '1/2+:7/2-', '(1/2:7/2)+').
    Follows the ENSDF spin-parity interpretation reference rules:
    - Range notation A:B+ or A:B- assigns parity ONLY to endpoint B; intermediate values have both parities (+/-).
    - Range notation (A:B)+ or (A:B)- assigns parity to ALL values A through B.
    - Range notation A+:B+ or A-:B+ assigns explicit parities to endpoints and +/- to intermediate values.
    - Step size in twice-spin (2J) space is 2 (e.g. 1/2, 3/2, 5/2, 7/2 or 0, 1, 2, 3).
    """
    clean = range_string.strip()
    is_globally_tentative = clean.startswith('(') and clean.endswith(')')

    global_parity = None
    global_tentative_parity = False
    if clean.endswith(')(+)'):
        global_parity = '+'
        global_tentative_parity = True
        clean = clean[:-4] + ')'
    elif clean.endswith(')(-)'):
        global_parity = '-'
        global_tentative_parity = True
        clean = clean[:-4] + ')'
    elif clean.endswith(')+'):
        global_parity = '+'
        clean = clean[:-1]
    elif clean.endswith(')-'):
        global_parity = '-'
        clean = clean[:-1]

    if clean.startswith('(') and clean.endswith(')'):
        is_globally_tentative = True
        clean = clean[1:-1].strip()

    parts = clean.split(':')
    if len(parts) != 2:
        return []

    start_spin, start_parity, start_tent_spin, start_tent_par = parse_spin_parity_endpoint(parts[0])
    end_spin, end_parity, end_tent_spin, end_tent_par = parse_spin_parity_endpoint(parts[1])

    if start_spin is None or end_spin is None:
        return []

    start_two_j = int(round(start_spin * 2))
    end_two_j = int(round(end_spin * 2))

    hypotheses = []
    step = 2 if start_two_j <= end_two_j else -2
    for two_j in range(start_two_j, end_two_j + step, step):
        is_endpoint_start = (two_j == start_two_j)
        is_endpoint_end = (two_j == end_two_j)

        tent_spin = is_globally_tentative or (start_tent_spin if is_endpoint_start else False) or (end_tent_spin if is_endpoint_end else False)

        if global_parity:
            h = {'twoTimesSpin': two_j, 'parity': global_parity}
            if tent_spin:
                h['isTentativeSpin'] = True
            if global_tentative_parity:
                h['isTentativeParity'] = True
            hypotheses.append(h)
        else:
            if is_endpoint_start and start_parity:
                parities = [(start_parity, start_tent_par)]
            elif is_endpoint_end and end_parity:
                parities = [(end_parity, end_tent_par)]
            else:
                parities = [('+', False), ('-', False)]

            for p, p_tent in parities:
                h = {'twoTimesSpin': two_j, 'parity': p}
                if tent_spin:
                    h['isTentativeSpin'] = True
                if p_tent:
                    h['isTentativeParity'] = True
                hypotheses.append(h)

    return hypotheses


def parse_spin_parity(spin_parity_string):
    """
    Parses ENSDF Spin-Parity string into a list of structured spin/parity hypotheses.

    Handles the following ENSDF patterns:
    - Simple assignments:   "2-", "3/2+"
    - Tentative single:     "(2)-", "(3/2)+"
    - Tentative lists:      "(2,3)-", "(1,2)+"   [parity outside parentheses]
    - Tentative ranges:     "(1:3)-", "(0:2)+"   [parity outside parentheses]
    - Firm spin, tentative parity: "2(+)", "3/2(-)"   [spin firm, parity in parens]
    - Bare ranges:          "1:3"                [no tentative markers, no parity]
    - Mixed-parity lists:   "1+,2-"
    """
    if not spin_parity_string or spin_parity_string.lower() in ['unknown', 'none']:
        return []

    clean_string = spin_parity_string.strip().rstrip('.')
    if not clean_string:
        return []

    # Delegate range notation (e.g., "1/2+:7/2-", "1/2:7/2", "(1/2:7/2)+") to dedicated range parser
    if ':' in clean_string and ',' not in clean_string:
        return parse_spin_parity_range(clean_string)

    # 1. Extract the global trailing parity sign.
    #    In ENSDF, parity often follows the closing parenthesis: "(2,3)-", "(1:3)+".
    global_parity = None
    working_string = clean_string
    if working_string[-1] in ('+', '-'):
        global_parity = working_string[-1]
        working_string = working_string[:-1].strip()

    if not working_string:
        return []

    # 2. Detect and strip global tentative parentheses enclosing the spin expression.
    #    Examples: "(2,3)" after parity removal, "(1:3)" after parity removal.
    is_globally_tentative = working_string.startswith('(') and working_string.endswith(')')
    if is_globally_tentative:
        working_string = working_string[1:-1]

    if not working_string:
        return []

    # 3. List parsing: "2,3", "1+,2-", or after stripping "2,3" from "(2,3)-".
    results = []
    for part in [part.strip() for part in working_string.split(',')]:
        if not part:
            continue

        # Handle "J(π)" notation: spin is firm, parity is tentative.
        # Example: "2(+)" → J=2 firm, parity '+' tentative.
        # Example: "3/2(-)" → J=3/2 firm, parity '-' tentative.
        # This must be checked before the generic parity-stripping logic below,
        # which would otherwise misidentify the closing ')' as a non-parity character.
        firm_spin_tentative_parity_match = re.match(r'^([0-9/]+)\(([+-])\)$', part)
        if firm_spin_tentative_parity_match:
            spin_raw_firm = firm_spin_tentative_parity_match.group(1)
            tentative_parity = firm_spin_tentative_parity_match.group(2)
            try:
                spin_value_parsed = float(eval(spin_raw_firm))
                two_times_spin_value = int(round(spin_value_parsed * 2))
                hypothesis = {
                    "twoTimesSpin": two_times_spin_value,
                    "parity": tentative_parity,
                    "isTentativeParity": True
                }
                if is_globally_tentative:
                    hypothesis["isTentativeSpin"] = True
                results.append(hypothesis)
            except (ValueError, SyntaxError):
                pass
            continue

        # Extract parity local to this part (e.g. "1+" in the list "1+,2-").
        # Fall back to the global parity if no local parity sign is present.
        part_parity = global_parity
        if part[-1] in ('+', '-'):
            part_parity = part[-1]
            part = part[:-1].strip()

        # Detect local tentative parentheses on this individual spin token.
        is_locally_tentative = '(' in part or ')' in part
        is_tentative = is_globally_tentative or is_locally_tentative

        spin_raw = part.replace('(', '').replace(')', '').strip()
        if not spin_raw:
            continue

        try:
            spin_value_parsed = float(eval(spin_raw))
            two_times_spin_value = int(round(spin_value_parsed * 2))
            # Parity written outside the closing parenthesis is a firm assignment.
            # Parity found inside the tentative block (i.e. not from global_parity)
            # is itself tentative alongside the spin.
            # Example: "(1,2)-" → isTentativeParity is omitted (parity outside parens is firm)
            # Example: "(2+)"   → isTentativeParity = True  (parity inside parens is tentative)
            parity_is_from_outside_parens = (
                (part_parity is not None)
                and (part_parity == global_parity)
                and (global_parity is not None)
            )
            hypothesis = {
                "twoTimesSpin": two_times_spin_value
            }
            if is_tentative:
                hypothesis["isTentativeSpin"] = True
            if part_parity:
                hypothesis["parity"] = part_parity
                if (not parity_is_from_outside_parens) and is_tentative:
                    hypothesis["isTentativeParity"] = True
            results.append(hypothesis)
        except (ValueError, SyntaxError):
            continue

    return results

def calculate_absolute_uncertainty(value_string, uncertainty_string):
    """
    Calculates absolute uncertainty based on Evaluated Nuclear Structure Data File convention.
    123(12) -> 12
    123.4(12) -> 1.2
    0.123(4) -> 0.0004
    """
    if not uncertainty_string:
        return 0.0
    
    value_string = value_string.strip()
    if '.' in value_string:
        decimal_part = value_string.split('.')[1]
        decimals = len(decimal_part)
    else:
        decimals = 0
        
    try:
        return float(Decimal(uncertainty_string).scaleb(-decimals))
    except InvalidOperation:
        return 0.0

def format_evaluator_input(value_string, uncertainty_string):
    if not uncertainty_string:
        return value_string
    return f"{value_string} {uncertainty_string}"

def extract_target_spin_parity(dataset_lines):
    """
    Extracts target nucleus ground state spin-parity from ENSDF comment lines or nuclear systematics.
    Examples of comment patterns:
    - "J|p=3/2+ for {+33}S ground state (2025Ch07)."
    - "The ground states of {+30}Si, |a, and {+34}S all have J|p=0+."
    - Systematic fallback: All even-even ground states have Jπ = 0+.
    """
    for line in dataset_lines:
        if len(line) > 7 and line[6:7] == 'c':
            match_target = re.search(r'J\|?p\s*=\s*([0-9/]+[\+\-])\s*(?:for|\{.*\}\s*ground\s*state|from)', line, re.IGNORECASE)
            if match_target:
                return match_target.group(1)
            match_ground = re.search(r'ground\s+states?\s+of\s+.*have\s+J\|?p\s*=\s*([0-9/]+[\+\-])', line, re.IGNORECASE)
            if match_ground:
                return match_ground.group(1)

    for line in dataset_lines:
        if len(line) > 7 and line[6:7] == 'c':
            match_any = re.search(r'J\|?p\s*=\s*([0-9/]+[\+\-])', line)
            if match_any:
                return match_any.group(1)

    # Nuclear structure systematics: All even-even ground states have Jπ = 0+
    if dataset_lines and len(dataset_lines[0]) > 9:
        dsid = dataset_lines[0][9:39].strip()
        match_reaction = re.match(r'^(\d+)([A-Za-z]+)', dsid)
        if match_reaction:
            mass_number = int(match_reaction.group(1))
            element_symbol = match_reaction.group(2).upper()
            even_z_elements = {
                'HE': 2, 'BE': 4, 'C': 6, 'O': 8, 'NE': 10, 'MG': 12, 'SI': 14,
                'S': 16, 'AR': 18, 'CA': 20, 'TI': 22, 'CR': 24, 'FE': 26, 'NI': 28,
                'ZN': 30, 'GE': 32, 'SE': 34, 'KR': 36, 'SR': 38, 'ZR': 40, 'MO': 42,
                'RU': 44, 'PD': 46, 'CD': 48, 'SN': 50, 'TE': 52, 'XE': 54, 'BA': 56,
                'CE': 58, 'ND': 60, 'SM': 62, 'GD': 64, 'DY': 66, 'ER': 68, 'YB': 70,
                'HF': 72, 'W': 74, 'OS': 76, 'PT': 78, 'HG': 80, 'PB': 82
            }
            atomic_number = even_z_elements.get(element_symbol)
            if atomic_number is not None and atomic_number % 2 == 0 and mass_number % 2 == 0:
                return '0+'
    return None


def infer_spin_parity_from_angular_momentum_transfer(angular_momentum_transfer_string, target_spin_parity_string, transferred_particle_spin_parity_string="1/2+"):
    """
    Calculates allowed final state spin-parity options using quantum angular momentum coupling.
    Selection Rules:
    1. Channel Spin S: |J_target - s_particle| <= S <= J_target + s_particle (step 1)
    2. Final Spin J:   |S - L| <= J <= S + L (step 1)
    3. Final Parity π: π_final = π_target * π_particle * (-1)^L
    """
    if not angular_momentum_transfer_string or not target_spin_parity_string or not transferred_particle_spin_parity_string:
        return None

    clean_target = target_spin_parity_string.strip().strip('()')
    clean_particle = transferred_particle_spin_parity_string.strip().strip('()')
    if not (clean_target.endswith('+') or clean_target.endswith('-')):
        return None
    if not (clean_particle.endswith('+') or clean_particle.endswith('-')):
        return None

    try:
        target_spin = Fraction(clean_target[:-1])
        target_parity = 1 if clean_target.endswith('+') else -1
        particle_spin = Fraction(clean_particle[:-1])
        particle_parity = 1 if clean_particle.endswith('+') else -1
    except (ValueError, ZeroDivisionError):
        return None

    # Channel spins S = |J_target - s_particle| ... J_target + s_particle (step 1)
    minimum_channel_spin = abs(target_spin - particle_spin)
    maximum_channel_spin = target_spin + particle_spin
    channel_spin_values = []
    current_channel_spin = minimum_channel_spin
    while current_channel_spin <= maximum_channel_spin:
        channel_spin_values.append(current_channel_spin)
        current_channel_spin += 1

    clean_l = angular_momentum_transfer_string.strip()
    is_globally_tentative = clean_l.startswith('(') and clean_l.endswith(')')
    inner = clean_l[1:-1] if is_globally_tentative else clean_l

    # Comma-separated entries represent alternative possibilities (UNION)
    group_strings = inner.split(',')
    overall_hypotheses_map = {}  # (two_times_spin, parity_string) -> is_tentative

    for group_str in group_strings:
        group_str = group_str.strip()
        if not group_str:
            continue
        group_tentative = is_globally_tentative or (group_str.startswith('(') and group_str.endswith(')'))
        clean_group = group_str.strip('() ')

        # Plus-separated entries represent mixed L-transfer populating the same level (INTERSECTION)
        component_strings = clean_group.split('+')
        component_states_list = []
        component_tentative_list = []

        for comp_str in component_strings:
            comp_str = comp_str.strip()
            if not comp_str:
                continue
            comp_tentative = group_tentative or '(' in comp_str or ')' in comp_str
            digits = re.sub(r'[^\d]', '', comp_str)
            if not digits:
                continue
            l_val = int(digits)
            final_parity_value = target_parity * particle_parity * ((-1)**l_val)
            parity_string = '+' if final_parity_value > 0 else '-'
            states = set()
            for channel_s in channel_spin_values:
                minimum_final_j = abs(channel_s - l_val)
                maximum_final_j = channel_s + l_val
                current_final_j = minimum_final_j
                while current_final_j <= maximum_final_j:
                    two_times_spin = int(round(float(current_final_j) * 2))
                    states.add((two_times_spin, parity_string))
                    current_final_j += 1
            component_states_list.append(states)
            component_tentative_list.append(comp_tentative)

        if not component_states_list:
            continue

        # Intersect states within the plus-group (e.g. L=0+2 -> L=0 states INTERSECT L=2 states)
        intersected_states = set(component_states_list[0])
        for other_states in component_states_list[1:]:
            intersected_states &= other_states

        # Defensive fallback: if intersection is empty, take union
        if not intersected_states:
            for other_states in component_states_list:
                intersected_states |= other_states

        group_final_tentative = any(component_tentative_list)

        # Union into overall hypotheses
        for state_key in intersected_states:
            if state_key in overall_hypotheses_map:
                overall_hypotheses_map[state_key] = overall_hypotheses_map[state_key] and group_final_tentative
            else:
                overall_hypotheses_map[state_key] = group_final_tentative

    if not overall_hypotheses_map:
        return None

    sorted_keys = sorted(overall_hypotheses_map.keys(), key=lambda k: (k[0], k[1]))
    values_list = []
    firm_display_strings = []
    tentative_display_strings = []
    for two_times_spin, parity_string in sorted_keys:
        is_tentative = overall_hypotheses_map[(two_times_spin, parity_string)]
        state_hypothesis = {
            "twoTimesSpin": two_times_spin,
            "parity": parity_string
        }
        j_display = str(two_times_spin // 2) if two_times_spin % 2 == 0 else f"{two_times_spin}/2"
        if is_tentative:
            state_hypothesis["isTentativeSpin"] = True
            state_hypothesis["isTentativeParity"] = True
            tentative_display_strings.append(f"{j_display}{parity_string}")
        else:
            firm_display_strings.append(f"{j_display}{parity_string}")
        values_list.append(state_hypothesis)

    if is_globally_tentative:
        string_representation = '(' + ','.join(firm_display_strings + tentative_display_strings) + ')'
    elif tentative_display_strings and not firm_display_strings:
        string_representation = '(' + ','.join(tentative_display_strings) + ')'
    elif tentative_display_strings and firm_display_strings:
        string_representation = ','.join(firm_display_strings) + ',(' + ','.join(tentative_display_strings) + ')'
    else:
        string_representation = ','.join(firm_display_strings)

    return {
        "stringRepresentation": string_representation,
        "values": values_list
    }


def parse_ensdf_line(line, target_spin_parity_string=None, transferred_particle_spin_parity_string="1/2+", is_transfer_reaction=False):
    """
    Parses a single ENSDF L record using fixed-width column slicing.
    Applies precision-based uncertainty inference when an explicit uncertainty is absent.
    When level spin-parity is unspecified in a transfer reaction:
    - If orbital angular momentum transfer (L) is given, deduces Jπ via angular momentum coupling.
    - If L-transfer is not given, considers L=0,1,2,3,4,5 possible and assigns all coupled Jπ options.
    For non-transfer datasets without spin-parity, omits spin-parity (empty for energy-based matching).
    Skips non-L records and continuation/annotation lines.
    """
    if len(line) < 8:
        return None, None

    record_type = line[7]
    # Require both: record type 'L' at position 7 AND a blank at position 6.
    # Comment records (cL) also carry 'L' at position 7 but have 'c' at position 6
    # and must be excluded so their text content is never parsed as level data.
    # Continuation/annotation records carry a letter at position 5 instead of a blank
    # (e.g., " 34S F L FLAG=H"); they contain no level data and are skipped.
    # Skipping keeps the previous level active so later G records still attach correctly.
    if record_type != 'L' or line[6] == 'c':
        return None, None
    if line[5] != ' ':
        return None, None
        
    energy_string = line[9:19].strip()
    uncertainty_string = line[19:21].strip()
    
    if not energy_string:
        return None, None
        
    try:
        energy_value = float(energy_string)
    except ValueError:
        # Defensive guard: non-numeric energy content is an annotation, not a physics level
        return None, None
    
    # Calculate uncertainty: explicit if provided, otherwise infer from precision
    if uncertainty_string:
        uncertainty_value = calculate_absolute_uncertainty(energy_string, uncertainty_string)
    elif energy_value == 0.0:
        # Ground state (0.0 keV) is the absolute reference => 0 uncertainty
        uncertainty_value = 0.0
    else:
        uncertainty_value = infer_uncertainty_from_precision(energy_string)
    
    spin_parity_raw = line[22:39].strip()
    energy_uncertainty_object = (
        {"type": "symmetric", "value": uncertainty_value}
        if uncertainty_value > 0
        else {"type": "none"}
    )
    data = {
        "energy": {
            "unit": "keV",
            "value": energy_value,
            "uncertainty": energy_uncertainty_object,
            "evaluatorInput": format_evaluator_input(energy_string, uncertainty_string)
        },
        "gamma_decays": []
    }
    # Stable flag is constrained to boolean const: true by level.json; only present for stable ground state
    if energy_value == 0.0:
        data["isStable"] = True

    spin_parity_values = parse_spin_parity(spin_parity_raw)
    if spin_parity_values:
        data["spinParity"] = {
            "stringRepresentation": spin_parity_raw,
            "values": spin_parity_values
        }
    elif is_transfer_reaction and target_spin_parity_string:
        angular_momentum_transfer_string = line[55:64].strip() if len(line) >= 56 else ""
        # If L-transfer is not given, consider L=0,1,2,3,4,5 possible
        effective_l_string = angular_momentum_transfer_string if angular_momentum_transfer_string else "0,1,2,3,4,5"
        inferred_spin_parity = infer_spin_parity_from_angular_momentum_transfer(
            effective_l_string,
            target_spin_parity_string,
            transferred_particle_spin_parity_string
        )
        if inferred_spin_parity and inferred_spin_parity.get("values"):
            data["spinParity"] = inferred_spin_parity

    return "L", data

def normalize_ensdf_text(text):
    """
    Strips ENSDF markup from a text string and uppercases it for normalized comparison.
    Used to match DSID fields in .ens identification records against XREF.txt descriptions.

    Rules applied in order:
    - '{+N}' or '{-N}' -> 'N'  (mass number superscripts: '{+33}S' -> '33S')
    - '{...}'          -> ''   (remaining brace groups removed)
    - '|x'            -> 'x'  (ENSDF pipe-prefix stripped: '|g' -> 'g')
    - Result is uppercased and stripped.
    """
    # Replace mass-number superscript braces: {+33} -> 33, {+3} -> 3
    text = re.sub(r'\{[+-]?(\d+)\}', r'\1', text)
    # Remove any remaining brace groups (e.g., {++}, {-2})
    text = re.sub(r'\{[^}]*\}', '', text)
    # Strip ENSDF pipe-prefix: |g -> g, |a -> a, |b -> b, etc.
    text = re.sub(r'\|(\S)', r'\1', text)
    return text.upper().strip()


def parse_xref_file(xref_path):
    """
    Reads an ENSDF-format XREF.txt file and returns a dictionary mapping each
    dataset letter to its reaction description string.

    XREF.txt uses the ENSDF X-record fixed-width format:
    - col 7  (0-indexed): 'X'  (record type)
    - col 8  (0-indexed): dataset letter (A-Z)
    - col 9+ (0-indexed): reaction description, already uppercase and markup-free

    Example line:  ' 34CL  XK33S(P,G)                  '
    Example entry: {'K': '33S(P,G)'}

    The descriptions are already in normalized uppercase form, matching
    the DSID field extracted from .ens identification records directly.
    """
    xref_map = {}
    if not os.path.exists(xref_path):
        print(f"Warning: XREF file not found at '{xref_path}'. Dataset letters will be unknown.")
        return xref_map
    with open(xref_path, 'r', encoding='utf-8') as f:
        for line in f:
            if len(line) > 9 and line[7:8] == 'X':
                letter = line[8:9]
                description = line[9:].strip()
                xref_map[letter] = description
    print(f"XREF loaded: {len(xref_map)} dataset labels.")
    return xref_map


def match_dsid_to_letter(dsid, xref_map):
    """
    Matches the DSID string extracted from a .ens identification record to its
    corresponding XREF dataset letter by direct string comparison.

    Both the DSID (from the .ens identification record) and the XREF descriptions
    (parsed from the ENSDF X-record format) are already uppercase, so no
    additional normalization is required beyond whitespace stripping.

    Returns the matched letter string, or None if no match is found.
    """
    normalized_dsid = dsid.strip().upper()
    for letter, description in xref_map.items():
        if normalized_dsid == description.upper():
            return letter
    return None


def parse_g_record(line):
    """
    Parses a single ENSDF G record using fixed-width column slicing.
    Returns a gamma decay data dictionary, or None if the line cannot be parsed.

    Column layout (0-indexed, matching ENSDF 80-column standard):
    -  9-18 : Gamma energy (keV)
    - 19-20 : Energy uncertainty (DE); converted from last-digit notation to absolute keV
    - 22-28 : Relative photon intensity (RI)
    - 29-30 : RI uncertainty (DRI); converted from last-digit notation to absolute intensity
              units using the decimal precision of RI (e.g., RI="98.6" DRI="14" -> 1.4).
              'LT'/'GT' are limit markers stored as 0.0.
    """
    if len(line) < 23:
        return None

    energy_string = line[9:19].strip()
    uncertainty_string = line[19:21].strip()
    intensity_string = line[22:29].strip()
    intensity_uncertainty_string = line[29:31].strip()

    if not energy_string:
        return None

    try:
        energy_value = float(energy_string)
    except ValueError:
        return None

    if uncertainty_string:
        energy_uncertainty = calculate_absolute_uncertainty(energy_string, uncertainty_string)
    else:
        energy_uncertainty = infer_uncertainty_from_precision(energy_string)

    # Relative intensity: absent or non-numeric (LT/GT limit markers) -> store as 0.0
    relative_intensity_value = 0.0
    if intensity_string:
        try:
            relative_intensity_value = float(intensity_string)
        except ValueError:
            pass  # LT/GT or other non-numeric markers: leave as 0.0

    # DRI: 'LT'/'GT' are limit qualifiers, not numeric uncertainties -> store as 0.0.
    # Valid DRI values follow the same "last-digit" notation as DE: convert to absolute
    # uncertainty using the precision of the RI string (e.g., RI="98.6" DRI="14" -> 1.4).
    intensity_uncertainty_value = 0.0
    if intensity_uncertainty_string and intensity_uncertainty_string not in ('LT', 'GT'):
        try:
            intensity_uncertainty_value = calculate_absolute_uncertainty(intensity_string, intensity_uncertainty_string)
        except (ValueError, Exception):
            pass

    has_intensity = bool(intensity_string)
    intensity_limit = None
    if intensity_uncertainty_string == 'LT':
        intensity_limit = 'LT'
    elif intensity_uncertainty_string == 'GT':
        intensity_limit = 'GT'

    return {
        "energy_value": energy_value,
        "energy_uncertainty": energy_uncertainty,
        "has_intensity": has_intensity,
        "relative_intensity_value": relative_intensity_value,
        "intensity_uncertainty_value": intensity_uncertainty_value,
        "intensity_limit": intensity_limit,
        "energy_input_string": format_evaluator_input(energy_string, uncertainty_string),
        "intensity_input_string": format_evaluator_input(intensity_string, intensity_uncertainty_string) if intensity_string else "",
    }


def convert_ens_files_to_datasets(raw_dir, xref_path, output_dir):
    """
    Main driver: scans all .ens files in raw_dir, matches each file to its XREF dataset
    letter via DSID comparison, parses L records (levels) and G records (gamma transitions),
    and writes one test_dataset_{letter}.json per file to output_dir.
    """
    xref_map = parse_xref_file(xref_path)
    os.makedirs(output_dir, exist_ok=True)

    ens_files = sorted(filename for filename in os.listdir(raw_dir) if filename.endswith('.ens'))
    if not ens_files:
        print(f"No .ens files found in '{raw_dir}'.")
        return

    for ens_filename in ens_files:
        # Extract the nucleus/target prefix from the .ens filename (e.g., 'S34' from 'S34_30si_a_g_a_n_resonances.ens')
        file_prefix = ens_filename.split('_')[0].rstrip('.ens') if '_' in ens_filename else ens_filename.rstrip('.ens')
        filepath = os.path.join(raw_dir, ens_filename)

        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        # 1. Extract DSID from the identification record (first line, cols 9-38, 0-indexed)
        dsid = ""
        if lines and len(lines[0]) > 9:
            dsid = lines[0][9:39].strip()

        # 2. Match DSID to XREF letter.
        # Primary path: normalize the DSID extracted from the identification record and look
        # it up in the XREF map.  Fallback path: if the file has no identification record
        # (e.g. synthetic test datasets that start immediately with L records), extract the
        # dataset letter directly from the filename pattern "*_test_{letter}.ens".
        letter = match_dsid_to_letter(dsid, xref_map)
        if letter is None:
            test_filename_match = re.search(r'test_([a-zA-Z])\.ens$', ens_filename, re.IGNORECASE)
            if test_filename_match:
                letter = test_filename_match.group(1).upper()
                print(f"Note: No XREF match for '{ens_filename}'; using filename-derived label '{letter}'.")
            else:
                print(f"Warning: DSID '{dsid}' in '{ens_filename}' did not match any XREF entry. Skipping.")
                continue

        print(f"Processing '{ens_filename}' -> Dataset {letter} (DSID: {dsid})...")

        target_spin_parity_string = extract_target_spin_parity(lines)

        # Detect transfer reactions from DSID (e.g., (D,P), (P,D), (D,3HE), (N,D), (T,P), (P,T), (3HE,A), etc.)
        transfer_particle_map = {
            r'\(D,P\)': '1/2+',
            r'\(P,D\)': '1/2+',
            r'\(D,N\)': '1/2+',
            r'\(N,D\)': '1/2+',
            r'\(D,3HE\)': '1/2+',
            r'\(3HE,D\)': '1/2+',
            r'\(3HE,A\)': '1/2+',
            r'\(A,3HE\)': '1/2+',
            r'\(A,T\)': '1/2+',
            r'\(T,A\)': '1/2+',
            r'\(T,P\)': '0+',
            r'\(P,T\)': '0+',
            r'\(3HE,P\)': '0+',
            r'\(A,2HE\)': '0+'
        }
        is_transfer = False
        transferred_particle_spin = "1/2+"
        for pattern, p_spin in transfer_particle_map.items():
            if re.search(pattern, dsid, re.IGNORECASE):
                is_transfer = True
                transferred_particle_spin = p_spin
                break

        # 3. Parse L records grouped with their immediately following G records
        level_list = []
        current_level = None

        for line in lines:
            if len(line) < 8:
                continue
            if line[6:7] == ' ' and line[7:8] == 'L':
                record_type, record_data = parse_ensdf_line(
                    line,
                    target_spin_parity_string=target_spin_parity_string,
                    transferred_particle_spin_parity_string=transferred_particle_spin,
                    is_transfer_reaction=is_transfer
                )
                if record_data is not None:
                    current_level = record_data
                    level_list.append(current_level)
            elif line[6:7] == ' ' and line[7:8] == 'G' and current_level is not None:
                gamma_data = parse_g_record(line)
                if gamma_data is not None:
                    current_level["gamma_decays"].append(gamma_data)

        # 4. Build gammasTable and resolve initial/final level indices
        gammas_table_list = []
        gamma_counter = 0

        for level_index, level_item in enumerate(level_list):
            if "gamma_decays" in level_item:
                level_gamma_indices = []
                initial_level_energy = level_item["energy"]["value"]

                for gamma_data in level_item["gamma_decays"]:
                    gamma_energy_value = gamma_data["energy_value"]
                    final_energy_target = initial_level_energy - gamma_energy_value

                    # Match final level by closest energy within 50 keV tolerance
                    best_match_index = 0
                    minimum_difference = 1e9
                    for candidate_index, candidate_level in enumerate(level_list):
                        difference = abs(candidate_level["energy"]["value"] - final_energy_target)
                        if difference < minimum_difference:
                            minimum_difference = difference
                            best_match_index = candidate_index

                    final_level_index = best_match_index if minimum_difference <= 50.0 else 0

                    gamma_energy_uncertainty_object = (
                        {"type": "symmetric", "value": gamma_data["energy_uncertainty"]}
                        if gamma_data["energy_uncertainty"] > 0
                        else {"type": "none"}
                    )
                    gamma_entry = {
                        "energy": {
                            "unit": "keV",
                            "value": gamma_energy_value,
                            "uncertainty": gamma_energy_uncertainty_object,
                            "evaluatorInput": gamma_data["energy_input_string"]
                        },
                        "initialLevel": level_index,
                        "finalLevel": final_level_index
                    }

                    if gamma_data["has_intensity"]:
                        if gamma_data["intensity_limit"] == 'LT':
                            intensity_uncertainty_object = {"type": "limit", "limitType": "upper"}
                        elif gamma_data["intensity_limit"] == 'GT':
                            intensity_uncertainty_object = {"type": "limit", "limitType": "lower"}
                        elif gamma_data["intensity_uncertainty_value"] > 0:
                            intensity_uncertainty_object = {"type": "symmetric", "value": gamma_data["intensity_uncertainty_value"]}
                        else:
                            intensity_uncertainty_object = {"type": "none"}

                        gamma_entry["intensity"] = {
                            "value": gamma_data["relative_intensity_value"],
                            "uncertainty": intensity_uncertainty_object,
                            "evaluatorInput": gamma_data["intensity_input_string"]
                        }

                    gammas_table_list.append(gamma_entry)
                    level_gamma_indices.append(gamma_counter)
                    level_list[final_level_index].setdefault("feedingGammas", []).append(gamma_counter)
                    gamma_counter += 1

                if level_gamma_indices:
                    level_item["emittedGammas"] = level_gamma_indices
                del level_item["gamma_decays"]

        output_data_structure = {"levelsTable": {"levels": level_list}}
        if gammas_table_list:
            output_data_structure["gammasTable"] = {"gammas": gammas_table_list}

        # Use prefix from the .ens filename and append '_lowercase' for lowercase letters to avoid case collisions.
        file_label = f"{letter}_lowercase" if letter.islower() else letter
        output_path = os.path.join(os.path.abspath(output_dir), f"{file_prefix}_dataset_{file_label}.json")
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output_data_structure, f, indent=4)
        print(f"Dataset {letter} saved to {output_path}")


if __name__ == "__main__":
    argument_parser = argparse.ArgumentParser(
        description="Parse ENSDF .ens files into structured JSON datasets."
    )
    argument_parser.add_argument(
        "--raw_dir", default="data/raw",
        help="Directory containing .ens input files (default: data/raw)"
    )
    argument_parser.add_argument(
        "--xref", default="data/raw/XREF.txt",
        help="Path to XREF.txt dataset label file (default: data/raw/XREF.txt)"
    )
    argument_parser.add_argument(
        "--output_dir", default="data/json",
        help="Directory for output JSON files (default: data/json)"
    )
    args = argument_parser.parse_args()
    convert_ens_files_to_datasets(args.raw_dir, args.xref, args.output_dir)
