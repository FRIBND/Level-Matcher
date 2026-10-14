"""
Dataset Parser for Nuclear Level Matcher
=========================================

# High-level Structure and Workflow Explanation:
======================================

Workflow Diagram:

  [--raw_dir/*.ens files]            [--xref: adopted .ens X records or XREF.txt]
         |                                        |
         v                                        v
  [Identification record]               [X records: letter -> reaction map]
  [dataset identifier, cols 10-39]      [reaction descriptions]
         |                                        |
         +------------------+---------------------+
                            |
                            v
               [Identifier -> XREF letter match]
               (filename fallback: *_test_{letter}.ens)
                            |
         +------------------+--------------------+
         |                                       |
         v                                       v
  [First general comment]              [Reaction type from identifier]
  [target ground-state spin-parity;    [transferred / captured particle spin-parity]
   default 0+]                                    |
         |                                        |
         +------------------+---------------------+
                            |
                            v
         [L records]  -->  [G records after each L record]
         [energy, uncertainty, spin-parity]   [energy, uncertainty, intensity]
                            |
                            v
         [Blank spin-parity in transfer/capture reactions]
         --> [angular momentum coupling from L-transfer (cols 56-64)]
                            |
                            v
         [Gamma placement: initial level = parent L record,
          final level = nearest level to E_level - E_gamma]
                            |
                            v
         [ENSDF JSON Schema 1.0.0: levelsTable + gammasTable]
                            |
                            v
         [output_dir/{prefix}_dataset_{letter}.json]
         (lowercase letters get a _lowercase suffix to avoid
          case-insensitive filename collisions)

# Technical Steps:
# 1. Output folder: create it if missing and delete every existing file in it, so stale JSON never survives a run.
# 2. XREF: read the X records (col 8 = 'X', col 9 = case-sensitive dataset letter, col 10+ = reaction) into a letter -> reaction map.
# 3. Dataset letter: match the first-line reaction text (cols 10-39, e.g. "{+33}S(P,G)") to an XREF reaction after removing
#    markup and uppercasing ("{+33}S" -> "33S"). No match: use the letter in *_test_{letter}.ens, else skip the file.
# 4. Records used: only L records (levels) and G records (gammas) are converted; comments, continuation records, headers and
#    all other record types are ignored. G records before the first L record are ignored. The identification record and the
#    first general comment are read only to obtain the reaction and the target ground-state spin-parity.
# 5. Level fields (fixed-width slicing): energy cols 10-19, uncertainty cols 20-21, spin-parity cols 22-39
#    (half-life is ignored: not used for level matching). Spin-parity strings (lists, ranges, tentative parentheses) become twoTimesSpin/parity
#    hypotheses; tentative flags are written only when true.
# 6. Spin-parity inference: when a level has no spin-parity and the reaction is a transfer, capture, inelastic scattering, charge
#    exchange or resonance reaction, the target spin-parity (first general comment, default 0+) is coupled with the transferred or
#    captured particle spin-parity (reaction map; several spin options are coupled separately and unioned) using the L-transfer in
#    cols 56-64. Comma = union of L values, plus = intersection of L values, parentheses = tentative. Without an L value the default
#    is tentative L = 0..5 (thermal neutron capture: L = 0). Other datasets keep an empty spin-parity for energy-based matching.
# 7. Missing energy uncertainties (stated numeric uncertainties are always kept, cols 20-21 for levels and gammas; letter
#    qualifiers such as AP, GE, LT are treated as missing): level energies take
#    50 keV if all excited-state energies in the dataset are whole tens, 5 keV if all are integers, otherwise precision-based
#    (ground state = none); gamma energies take 1 keV (decimal), 5 keV (integer), 20 keV (whole ten).
# 8. Gamma fields: energy cols 10-19, uncertainty cols 20-21, relative intensity cols 22-29, intensity uncertainty cols 30-31
#    (LT/GT become upper/lower limits). Each G record belongs to the L record preceding it.
# 9. Intensity normalization (per level, so datasets share one reference): r = 100*I/I_ref with I_ref = strongest non-limit gamma
#    (reference has no uncertainty); others get sigma_r = r*sqrt((sigma_I/I)^2 + (sigma_ref/I_ref)^2). Limits scale by the same
#    factor. evaluatorInput keeps the raw text. Example: RI 80, 20 -> 100, 25; RI 50, 8.5, 10 -> 100, 17, 20.
# 10. Gamma placement: initialLevel = parent L record; finalLevel = level nearest to E_level - E_gamma; the parent lists
#    emittedGammas and the final level lists feedingGammas.
# 11. Output: one {prefix}_dataset_{letter}.json per file (prefix = filename text before the first underscore), complying with
#    ENSDF JSON Schema 1.0.0 (level.json, level-energy.json, spin-parity.json, gamma.json, radiation-energy.json, radiation-intensity.json).
#
# Architecture:
# - `infer_uncertainty_from_precision`: Precision-based level uncertainty (5 x the last reported digit place) used when a dataset has decimal energies.
# - `parse_spin_parity_endpoint`: Parses one range endpoint (spin, parity, tentative flags).
# - `parse_spin_parity_range`: Expands range expressions (A:B, (A:B)+, A+:B-) per the ENSDF spin-parity notation rules.
# - `parse_spin_parity`: Converts a spin-parity string (lists, ranges, tentative notation) into spin-parity.json value hypotheses.
# - `calculate_absolute_uncertainty`: Converts last-digit uncertainty notation into an absolute value.
# - `format_evaluator_input`: Builds the evaluatorInput string (value plus stated uncertainty).
# - `extract_target_spin_parity`: Reads the target ground-state spin-parity from the first general comment; defaults to 0+.
# - `infer_spin_parity_from_angular_momentum_transfer`: Couples target, transferred particle and L-transfer into spin-parity hypotheses.
# - `parse_level_record`: Converts one L record into a level dictionary (energy, uncertainty, spin-parity); returns None for other lines.
# - `normalize_ensdf_text`: Strips ENSDF markup ({+N}, |g, etc.) for identifier-to-XREF matching.
# - `parse_xref_file`: Reads X records into a letter -> reaction dictionary.
# - `match_dsid_to_letter`: Maps a file's identification text to its XREF dataset letter.
# - `parse_g_record`: Converts one G record into a gamma dictionary (energy, uncertainty, intensity, limits).
# - `convert_ens_files_to_datasets`: Driver: cleans the output folder, identifies each dataset, selects the coupling particle from the
#   reaction, parses L and G records, places gammas, and writes the JSON files.
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
    
    Convention used here: Uncertainty = 5 × least_significant_digit_place_value
    (half of the next-higher digit place; same rule as the integer default of 5 keV).
    
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
                spin_fraction = Fraction(spin_raw_firm)
                two_times_spin_value = int(round(float(spin_fraction) * 2))
                hypothesis = {
                    "twoTimesSpin": two_times_spin_value,
                    "parity": tentative_parity,
                    "isTentativeParity": True
                }
                if is_globally_tentative:
                    hypothesis["isTentativeSpin"] = True
                results.append(hypothesis)
            except (ValueError, ZeroDivisionError):
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
            spin_fraction = Fraction(spin_raw)
            two_times_spin_value = int(round(float(spin_fraction) * 2))
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
        except (ValueError, ZeroDivisionError):
            continue

    return results

def calculate_absolute_uncertainty(value_string, uncertainty_string):
    """
    Calculates absolute uncertainty based on Evaluated Nuclear Structure Data File convention.
    123(12) -> 12
    123.4(12) -> 1.2
    0.123(4) -> 0.004
    5.6E-4 with 10 -> 1.0E-4 (uncertainty applies to the last mantissa digit)
    """
    if not uncertainty_string:
        return 0.0
    
    # Scientific notation: count decimals in the mantissa only, then apply the exponent
    mantissa_string, _, exponent_string = value_string.strip().upper().partition('E')
    decimals = len(mantissa_string.split('.')[1]) if '.' in mantissa_string else 0
        
    try:
        exponent = int(exponent_string) if exponent_string else 0
        return float(Decimal(uncertainty_string).scaleb(exponent - decimals))
    except (InvalidOperation, ValueError):
        return 0.0

def format_evaluator_input(value_string, uncertainty_string):
    if not uncertainty_string:
        return value_string
    return f"{value_string} {uncertainty_string}"

def extract_target_spin_parity(dataset_lines):
    """
    Reads the target ground-state Jpi from the first general comment (and its continuation lines) of the dataset.
    Accepted wordings: "J|p=3/2+ for {+33}S ground state", "... J|p=0+ ... g.s.", "ground states of ... all have J|p=0+".
    Default is 0+: even-Z even-N ground states are always 0+, and unspecified targets are assumed 0+.
    """
    top_comment_text = ""
    for line in dataset_lines[1:]:
        # General comment = 'c' in col 7 and blank col 8; a blank col 6 starts a new comment, a digit continues it
        if len(line) > 8 and line[6] == 'c' and line[7] == ' ':
            if line[5] == ' ' and top_comment_text:
                break
            top_comment_text += " " + line[9:].strip()

    spin_pattern = r'J\|?p\s*=\s*(\d+(?:/\d+)?[+-])'
    keyword_pattern = r'(?:ground\s*states?|g\.s\.|target)'
    spin_first_match = re.search(spin_pattern + r'[^.]{0,60}?' + keyword_pattern, top_comment_text, re.IGNORECASE)
    keyword_first_match = re.search(keyword_pattern + r'[^.=]{0,60}?' + spin_pattern, top_comment_text, re.IGNORECASE)
    candidate_matches = [match for match in (spin_first_match, keyword_first_match) if match]
    if candidate_matches:
        return min(candidate_matches, key=lambda match: match.start()).group(1)
    return '0+'


def infer_spin_parity_from_angular_momentum_transfer(angular_momentum_transfer_string, target_spin_parity_string, transferred_particle_spin_parity_string="1/2+"):
    """
    Calculates allowed final state spin-parity options using quantum angular momentum coupling.
    Supports single or mixed transferred particles (e.g. '1/2+' or '0+,1+' for mixed transfers).
    Selection Rules:
    1. Channel Spin S: |J_target - s_particle| <= S <= J_target + s_particle (step 1)
    2. Final Spin J:   |S - L| <= J <= S + L (step 1)
    3. Final Parity π: π_final = π_target * π_particle * (-1)^L
    """
    if not angular_momentum_transfer_string or not target_spin_parity_string or not transferred_particle_spin_parity_string:
        return None

    clean_target = target_spin_parity_string.strip().strip('()')
    if not (clean_target.endswith('+') or clean_target.endswith('-')):
        return None

    try:
        target_spin = Fraction(clean_target[:-1])
        target_parity = 1 if clean_target.endswith('+') else -1
    except (ValueError, ZeroDivisionError):
        return None

    # Parse particle spin-parity list (e.g., '1/2+', or '0+,1+' for mixed Fermi+GT or mixed np transfer)
    particle_specs = []
    for p_token in transferred_particle_spin_parity_string.split(','):
        clean_p = p_token.strip().strip('()')
        if clean_p.endswith('+') or clean_p.endswith('-'):
            try:
                p_spin = Fraction(clean_p[:-1])
                p_parity = 1 if clean_p.endswith('+') else -1
                particle_specs.append((p_spin, p_parity))
            except (ValueError, ZeroDivisionError):
                pass
    if not particle_specs:
        return None

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
        l_components = []
        for comp_str in clean_group.split('+'):
            digits = re.sub(r'[^\d]', '', comp_str)
            if digits:
                l_components.append(int(digits))
        if not l_components:
            continue
        group_final_tentative = group_tentative or '(' in group_str

        # Each transferred-spin option (e.g. np pair s=0 or s=1) couples separately; results are unioned
        intersected_states = set()
        for particle_spin, particle_parity in particle_specs:
            channel_spin_values = []
            current_channel_spin = abs(target_spin - particle_spin)
            while current_channel_spin <= target_spin + particle_spin:
                channel_spin_values.append(current_channel_spin)
                current_channel_spin += 1

            states_per_l = []
            for l_val in l_components:
                parity_string = '+' if target_parity * particle_parity * ((-1)**l_val) > 0 else '-'
                states = set()
                for channel_s in channel_spin_values:
                    current_final_j = abs(channel_s - l_val)
                    while current_final_j <= channel_s + l_val:
                        states.add((int(round(float(current_final_j) * 2)), parity_string))
                        current_final_j += 1
                states_per_l.append(states)

            # Mixed L (e.g. 0+2): level must be reachable by every L component
            intersected_states |= set.intersection(*states_per_l)

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


def parse_level_record(line, target_spin_parity_string=None, transferred_particle_spin_parity_string="1/2+", is_transfer_reaction=False, default_angular_momentum_transfer="(0,1,2,3,4,5)", default_level_uncertainty=None):
    """
    Parses a single ENSDF L record using fixed-width column slicing.
    A missing energy uncertainty is replaced by default_level_uncertainty (dataset-wide 5 or 50 keV) or, failing that, by precision.
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
    
    # Stated numeric uncertainty first; letter qualifiers (AP, GE, LT, GT, LE, SY) carry no number, so they fall through to the defaults
    if uncertainty_string.isdigit():
        uncertainty_value = calculate_absolute_uncertainty(energy_string, uncertainty_string)
    elif energy_value == 0.0:
        # Ground state (0.0 keV) is the absolute reference => 0 uncertainty
        uncertainty_value = 0.0
    else:
        # Dataset-wide default (5 or 50 keV) when all excited-state energies are integers / whole tens
        uncertainty_value = default_level_uncertainty or infer_uncertainty_from_precision(energy_string)

    spin_parity_raw = line[21:39].strip()
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
    spin_parity_values = parse_spin_parity(spin_parity_raw)
    if spin_parity_values:
        data["spinParity"] = {
            "stringRepresentation": spin_parity_raw,
            "values": spin_parity_values
        }
    elif is_transfer_reaction and target_spin_parity_string:
        angular_momentum_transfer_string = line[55:64].strip() if len(line) >= 56 else ""
        # L not given: default is tentative L=0..5 (thermal neutron capture is s-wave only, L=0)
        effective_l_string = angular_momentum_transfer_string if angular_momentum_transfer_string else default_angular_momentum_transfer
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
    normalized_dsid = normalize_ensdf_text(dsid)
    for letter, description in xref_map.items():
        if normalized_dsid == normalize_ensdf_text(description):
            return letter

    # Fallback to direct uppercase comparison without markup stripping
    raw_dsid = dsid.strip().upper()
    for letter, description in xref_map.items():
        if raw_dsid == description.strip().upper():
            return letter
    return None


def parse_g_record(line):
    """
    Parses a single ENSDF G record using fixed-width column slicing.
    Returns a gamma decay data dictionary, or None if the line cannot be parsed.

    Column layout (0-indexed, matching ENSDF 80-column standard):
    -  9-18 : Gamma energy (keV)
    - 19-20 : Energy uncertainty (DE); converted from last-digit notation to absolute keV.
              If blank: 1 keV (decimal energy), 20 keV (whole ten), 5 keV (other integer).
    - 21-28 : Relative photon intensity (RI) (cols 22-29, 1-indexed)
    - 29-30 : RI uncertainty (DRI); converted from last-digit notation to absolute intensity
              units using the decimal precision of RI (e.g., RI="98.6" DRI="14" -> 1.4).
              'LT'/'GT' are returned as intensity_limit and become upper/lower limits.
    """
    # Editors may trim trailing spaces, so only the energy field (cols 10-19) is required
    if len(line) < 10:
        return None
    if len(line) > 5 and line[5] != ' ':
        return None
    if len(line) > 6 and line[6] == 'c':
        return None
    if len(line) > 7 and line[7] != 'G':
        return None

    energy_string = line[9:19].strip()
    uncertainty_string = line[19:21].strip()
    intensity_string = line[21:29].strip()
    intensity_uncertainty_string = line[29:31].strip()

    if not energy_string:
        return None

    try:
        energy_value = float(energy_string)
    except ValueError:
        return None

    if uncertainty_string.isdigit():
        energy_uncertainty = calculate_absolute_uncertainty(energy_string, uncertainty_string)
    elif '.' in energy_string:
        energy_uncertainty = 1.0
    elif int(energy_value) % 10 == 0:
        energy_uncertainty = 20.0
    else:
        energy_uncertainty = 5.0

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
    Main driver: cleans output_dir, matches each .ens file in raw_dir to its XREF dataset letter,
    parses its L records (levels) and G records (gamma transitions), and writes
    one {prefix}_dataset_{letter}.json per file to output_dir.
    """
    xref_map = parse_xref_file(xref_path)
    os.makedirs(output_dir, exist_ok=True)
    # Stale JSON from earlier runs must not survive into this run
    for stale_name in os.listdir(output_dir):
        stale_path = os.path.join(output_dir, stale_name)
        if os.path.isfile(stale_path):
            os.remove(stale_path)

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

        # Reaction-to-particle coupling mapping strictly based on angular_momentum_coupling.md:
        transfer_particle_map = {
            # Single-neutron transfer (s=1/2+)
            r'\(D,P\)': '1/2+', r'\(P,D\)': '1/2+',
            r'\(T,D\)': '1/2+', r'\(D,T\)': '1/2+',
            r'\(3HE,A\)': '1/2+', r'\(A,3HE\)': '1/2+',
            # Single-proton transfer (s=1/2+)
            r'\(D,N\)': '1/2+', r'\(N,D\)': '1/2+',
            r'\(3HE,D\)': '1/2+', r'\(D,3HE\)': '1/2+',
            r'\(A,T\)': '1/2+', r'\(T,A\)': '1/2+',
            # Two-nucleon identical transfer (2n / 2p, relative s-state: s=0+, T=1)
            r'\(T,P\)': '0+', r'\(P,T\)': '0+',
            r'\(3HE,N\)': '0+', r'\(N,3HE\)': '0+',
            r'\(A,2HE\)': '0+',
            # Two-nucleon np transfer: (a,d), (d,a) -> deuteron-like s=1+, T=0
            r'\(A,D\)': '1+', r'\(D,A\)': '1+',
            # Two-nucleon np transfer: (3He,p), (p,3He) -> mixed 0+ (T=1) and 1+ (T=0)
            r'\(3HE,P\)': '0+,1+', r'\(P,3HE\)': '0+,1+',
            # Cluster transfer (alpha cluster s=0+)
            r'\(6LI,D\)': '0+', r'\(D,6LI\)': '0+',
            r'\(7LI,T\)': '0+', r'\(T,7LI\)': '0+',
            r'\(12C,8BE\)': '0+', r'\(16O,12C\)': '0+',
            # Inelastic scattering: (a,a') natural parity 0+; (p,p') non-flip 0+ and spin-flip 1+
            r"\(A,A'": '0+',
            r"\(P,P'": '0+,1+',
            # Radiative capture: captured particle couples to the target
            r'\(P,G\)': '1/2+', r'\(N,G\)': '1/2+', r'\(A,G\)': '0+',
            # Charge exchange (Fermi 0+, GT 1+)
            r'\(6LI,6HE\)': '1+',
            r'\(P,N\)': '0+,1+', r'\(N,P\)': '0+,1+',
            r'\(3HE,T\)': '0+,1+', r'\(T,3HE\)': '0+,1+'
        }
        is_transfer = False
        transferred_particle_spin = "1/2+"
        if 'RESONANCES' in dsid.upper():
            # Resonance datasets: the projectile is captured by the target (inverse kinematics: 4He target = captured alpha)
            upper_dsid = dsid.strip().upper()
            captured_particle_match = re.match(r"^\w+\((\w+),", upper_dsid)
            captured_particle = 'A' if upper_dsid.startswith('4HE(') else (captured_particle_match.group(1) if captured_particle_match else None)
            if captured_particle in ('N', 'P'):
                is_transfer, transferred_particle_spin = True, '1/2+'
            elif captured_particle == 'A':
                is_transfer, transferred_particle_spin = True, '0+'
        else:
            for pattern, p_spin in transfer_particle_map.items():
                if re.search(pattern, dsid, re.IGNORECASE):
                    is_transfer = True
                    transferred_particle_spin = p_spin
                    break

        # 3. Parse L records grouped with their immediately following G records
        # Excited-state energy strings decide the dataset-wide default level uncertainty (ground state excluded)
        excited_energy_strings = []
        for line in lines:
            if len(line) > 19 and line[5:8] == '  L':
                energy_text = line[9:19].strip()
                try:
                    if float(energy_text) != 0.0:
                        excited_energy_strings.append(energy_text)
                except ValueError:
                    pass
        default_level_uncertainty = None
        if excited_energy_strings and all(re.fullmatch(r'\d+', text) and int(text) % 10 == 0 for text in excited_energy_strings):
            default_level_uncertainty = 50.0
        elif excited_energy_strings and all(re.fullmatch(r'\d+', text) for text in excited_energy_strings):
            default_level_uncertainty = 5.0

        level_list = []
        current_level = None

        for line in lines:
            if len(line) < 8:
                continue
            if line[6:7] == ' ' and line[7:8] == 'L':
                record_type, record_data = parse_level_record(
                    line,
                    target_spin_parity_string=target_spin_parity_string,
                    transferred_particle_spin_parity_string=transferred_particle_spin,
                    is_transfer_reaction=is_transfer,
                    default_angular_momentum_transfer="0" if 'THERMAL' in dsid.upper() else "(0,1,2,3,4,5)",
                    default_level_uncertainty=default_level_uncertainty
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

                # Normalize intensities so the strongest non-limit gamma of this level = 100 (exact, no uncertainty).
                # Others: r = 100*I/I_ref, sigma_r = r*sqrt((sigma_I/I)^2 + (sigma_ref/I_ref)^2) (independent measurements).
                # LT/GT limits scale by the same factor and keep their limit type; no reference => values stay as evaluated.
                reference_candidates = [gamma for gamma in level_item["gamma_decays"]
                                        if gamma["has_intensity"] and gamma["intensity_limit"] is None and gamma["relative_intensity_value"] > 0]
                if reference_candidates:
                    reference_gamma = max(reference_candidates, key=lambda gamma: gamma["relative_intensity_value"])
                    reference_value = reference_gamma["relative_intensity_value"]
                    reference_relative_uncertainty = reference_gamma["intensity_uncertainty_value"] / reference_value
                    for gamma in level_item["gamma_decays"]:
                        raw_value = gamma["relative_intensity_value"]
                        if not gamma["has_intensity"] or raw_value <= 0:
                            continue
                        normalized_value = 100.0 * raw_value / reference_value
                        if gamma is reference_gamma:
                            normalized_uncertainty = 0.0
                        else:
                            own_relative_uncertainty = gamma["intensity_uncertainty_value"] / raw_value
                            normalized_uncertainty = normalized_value * (own_relative_uncertainty**2 + reference_relative_uncertainty**2) ** 0.5
                        gamma["relative_intensity_value"] = round(normalized_value, 6)
                        gamma["intensity_uncertainty_value"] = round(normalized_uncertainty, 6)

                for gamma_data in level_item["gamma_decays"]:
                    gamma_energy_value = gamma_data["energy_value"]
                    final_energy_target = initial_level_energy - gamma_energy_value

                    # Match final level: G-record de-excites to the level closest to (E_initial - E_gamma)
                    best_match_index = 0
                    minimum_difference = 1e9
                    for candidate_index, candidate_level in enumerate(level_list):
                        difference = abs(candidate_level["energy"]["value"] - final_energy_target)
                        if difference < minimum_difference:
                            minimum_difference = difference
                            best_match_index = candidate_index

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
                        "finalLevel": best_match_index
                    }
                    level_list[best_match_index].setdefault("feedingGammas", []).append(gamma_counter)

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
