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

Technical Steps:
1. Read XREF.txt to build a letter-to-reaction mapping; strip ENSDF markup for normalized comparison.
2. Scan all .ens files in data/raw/; extract each file's DSID from its identification record (cols 9-38).
3. Match each DSID to its XREF letter using normalized string comparison.
4. Parse L records (energy levels) and G records (gamma transitions) using fixed-width column slicing;
   skip continuation/annotation records (e.g., FLAG=H lines) that carry no level data.
5. Infer uncertainties from precision where explicit uncertainty values are absent.
6. Output one test_dataset_{letter}.json per .ens file into data/json/.

Architecture:
- `normalize_ensdf_text`: Strips ENSDF markup ({+N}, |g, etc.) for DSID-to-XREF matching.
- `parse_xref_file`: Reads XREF.txt into a normalized letter-to-description dictionary.
- `match_dsid_to_letter`: Maps a .ens file's DSID to its XREF dataset letter.
- `infer_uncertainty_from_precision`: Heuristic engine for precision-based uncertainty estimation.
- `parse_spin_parity`: Normalizes ENSDF Spin-Parity strings into structured spin/parity hypothesis lists.
- `parse_g_record`: Slices G records into structured gamma transition data dictionaries.
- `parse_ensdf_line`: Slices L records into structured level data dictionaries; returns None for annotation/continuation lines.
- `convert_ens_files_to_datasets`: Main driver that scans .ens files, matches labels, and writes JSON.
"""

import json
import re
import os
import argparse
from decimal import Decimal, InvalidOperation

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

    # 3. Range support: "1:3" or "(1:3)-" (after stripping) -> "1:3".
    #    Step by 2 in 2J space to correctly cover both integer (2,4,6) and
    #    half-integer (1,3,5) spin sequences with a single rule.
    if ':' in working_string and ',' not in working_string:
        range_parts = working_string.split(':')
        if len(range_parts) == 2:
            try:
                start_spin = float(eval(range_parts[0].strip()))
                end_spin   = float(eval(range_parts[1].strip()))
                start_two_j = int(round(start_spin * 2))
                end_two_j   = int(round(end_spin   * 2))
                is_tentative = is_globally_tentative or '(' in clean_string
                # range stop is exclusive, so use end_two_j + 2 to include the endpoint
                # Parity from outside the closing parenthesis is a firm assignment
                # regardless of whether the spin range itself is tentative.
                # Example: "(1:3)-" → spin tentative, parity '-' is firm.
                return [{
                    "twoTimesSpin": two_j,
                    "isTentativeSpin": is_tentative,
                    "parity": global_parity,
                    "isTentativeParity": False
                } for two_j in range(start_two_j, end_two_j + 2, 2)]
            except (ValueError, SyntaxError):
                pass

    # 4. List parsing: "2,3", "1+,2-", or after stripping "2,3" from "(2,3)-".
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
                results.append({
                    "twoTimesSpin": two_times_spin_value,
                    "isTentativeSpin": is_globally_tentative,
                    "parity": tentative_parity,
                    "isTentativeParity": True
                })
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
            # Example: "(1,2)-" → isTentativeParity = False (parity outside parens)
            # Example: "(2+)"   → isTentativeParity = True  (parity inside parens)
            parity_is_from_outside_parens = (
                (part_parity is not None)
                and (part_parity == global_parity)
                and (global_parity is not None)
            )
            results.append({
                "twoTimesSpin": two_times_spin_value,
                "isTentativeSpin": is_tentative,
                "parity": part_parity,
                "isTentativeParity": (not parity_is_from_outside_parens) and is_tentative and (part_parity is not None)
            })
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

def parse_ensdf_line(line):
    """
    Parses a single ENSDF L record using fixed-width column slicing.
    Applies precision-based uncertainty inference when an explicit uncertainty is absent.
    Skips all non-L record types (comment, gamma, header, and identification records)
    as well as continuation/annotation records (e.g., FLAG=H lines) that carry no level data.
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
    data = {
        "energy": {
            "unit": "keV",
            "value": energy_value,
            "uncertainty": {
                "value": uncertainty_value,
                "type": "symmetric" if uncertainty_string else "inferred"
            }
        },
        "isStable": False,
        "gamma_decays": []
    }
    if energy_string:
        data["energy"]["evaluatorInput"] = format_evaluator_input(energy_string, uncertainty_string)

    spin_parity_values = parse_spin_parity(spin_parity_raw)
    if spin_parity_values or spin_parity_raw:
        data["spinParity"] = {}
        if spin_parity_values:
            data["spinParity"]["values"] = spin_parity_values
        if spin_parity_raw:
            data["spinParity"]["evaluatorInput"] = spin_parity_raw

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

    return {
        "energy_value": energy_value,
        "energy_uncertainty": energy_uncertainty,
        "relative_intensity_value": relative_intensity_value,
        "intensity_uncertainty_value": intensity_uncertainty_value,
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

        # 3. Parse L records grouped with their immediately following G records
        level_list = []
        current_level = None

        for line in lines:
            if len(line) < 8:
                continue
            if line[6:7] == ' ' and line[7:8] == 'L':
                record_type, record_data = parse_ensdf_line(line)
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

                    gamma_entry = {
                        "energy": {
                            "unit": "keV",
                            "value": gamma_energy_value,
                            "uncertainty": (
                                {"value": gamma_data["energy_uncertainty"], "type": "symmetric"}
                                if gamma_data["energy_uncertainty"] > 0
                                else {"type": "unreported"}
                            )
                        },
                        "gammaIntensity": {
                            "value": gamma_data["relative_intensity_value"],
                            "uncertainty": (
                                {"value": gamma_data["intensity_uncertainty_value"], "type": "symmetric"}
                                if gamma_data["intensity_uncertainty_value"] > 0
                                else {"type": "unreported"}
                            )
                        },
                        "initialLevel": level_index,
                        "finalLevel": final_level_index
                    }
                    if gamma_data.get("energy_input_string"):
                        gamma_entry["energy"]["evaluatorInput"] = gamma_data["energy_input_string"]
                    gamma_entry["gammaIntensity"]["evaluatorInput"] = gamma_data["intensity_input_string"]

                    gammas_table_list.append(gamma_entry)
                    level_gamma_indices.append(gamma_counter)
                    gamma_counter += 1

                if level_gamma_indices:
                    level_item["gammas"] = level_gamma_indices
                del level_item["gamma_decays"]

        output_data_structure = {"levelsTable": {"levels": level_list}}
        if gammas_table_list:
            output_data_structure["gammasTable"] = {"gammas": gammas_table_list}

        output_path = os.path.join(os.path.abspath(output_dir), f"test_dataset_{letter}.json")
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
