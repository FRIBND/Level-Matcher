"""
Angular Momentum Coupling Calculator
======================================

# High-level Structure and Workflow Explanation:
======================================

Lists the final spin and parity values allowed when a target nucleus of known spin and parity couples with a transferred
particle and an orbital angular momentum. Standalone helper (see docs/angular_momentum_coupling.md); the matching pipeline does not call it.

Workflow Diagram:
[Target spin-parity] + [Particle spin-parity]   (command-line arguments, or interactive prompts when omitted)
            |
            v
[parse_spin_parity: validate the text, split into spin and parity]
            |
            v
[Channel spins: |target spin - particle spin| ... target spin + particle spin, step 1]
            |
            v
[For each orbital angular momentum 0 to 6 and each channel spin: final spins |channel - orbital| ... channel + orbital]
            |
            v
[Final parity = target parity x particle parity x (-1)^orbital]  -->  [Printed table: wave, orbital, channel spin, final spin-parity]

Numbered Technical Steps:
1. Input: two spin-parity strings such as 3/2- and 1/2+ (integer or fractional spin, then + or -).
2. Parsing: `parse_spin_parity` returns the spin as a Fraction and the parity as +1 or -1.
3. Channel spin: every value from the absolute difference to the sum of target and particle spin.
4. Final states: spins from |channel - orbital| to channel + orbital for waves s, p, d, f, g, h, i.
5. Output: one table row per wave and channel spin, separated by orbital angular momentum.

Architecture:
- `parse_spin_parity`: Splits a string such as 3/2- into a Fraction spin and a parity of +1 or -1.
- `prompt_spin_parity`: Asks interactively until the input is valid.
- `calc`: Prints the coupling table for a target and a particle.
"""

import argparse
from fractions import Fraction


def parse_spin_parity(value: str, label: str) -> tuple[Fraction, int]:
    if not value:
        raise ValueError(f"{label} is empty")
    if not (value.endswith('+') or value.endswith('-')):
        raise ValueError(f"{label} parity missing (use + or - at end)")

    spin = Fraction(value[:-1])
    parity = 1 if value.endswith('+') else -1
    return spin, parity


def prompt_spin_parity(prompt_text: str, label: str) -> tuple[Fraction, int, str]:
    while True:
        value = input(f"{prompt_text}: ").strip()
        try:
            spin, parity = parse_spin_parity(value, label)
            return spin, parity, value
        except ValueError as error:
            print(f"Error: {error}")
            print("Format example: 3/2- or 1/2+ or 0+")

def calc(target, particle):
    """
    Calculates allowed final angular momenta (J) and parity (π) 
    by coupling a target nucleus with a particle and orbital angular momentum L.
    
    Parameters:
    -----------
    target : str
        Target nucleus in J^π format (e.g., "3/2-", "0+", "5/2+")
        where J is spin (integer or half-integer) and π is parity (+/-)
    
    particle : str
        Transferred particle in s^π format (e.g., "1/2+", "0+", "1-")
        where s is intrinsic particle spin and π is intrinsic particle parity

    Selection Rules:
    1. Channel Spin S: |J_target - s_particle| <= S <= J_target + s_particle
    2. Final Spin J: |S - L| <= J <= S + L
    3. Parity: π_final = π_target * π_particle * (-1)^L
    """
    
    # Parse inputs (e.g., "0+" -> spin="0", parity=1)
    try:
        J_target, pi_target = parse_spin_parity(target, "Target")
        s_particle, pi_particle = parse_spin_parity(particle, "Particle")
    except ValueError as e:
        print(f"Error parsing input: {e}")
        print("Input format example: target=3/2- and particle=1/2+")
        print("                     (Target: J=3/2 π=-, Particle: s=1/2 π=+)")
        return

    print(f"\nTarget: J={J_target} π={'+' if pi_target>0 else '-'} | Particle: s={s_particle} π={'+' if pi_particle>0 else '-'}")
    
    # Channel spins S = |J_target - s_particle| ... J_target + s_particle
    min_S = abs(J_target - s_particle)
    max_S = J_target + s_particle
    
    # Generate S values (step is 1)
    S_values = []
    curr_S = min_S
    while curr_S <= max_S:
        S_values.append(curr_S)
        curr_S += 1
    
    # Explanation for S
    explanation = f"from |{J_target} - {s_particle}| to {J_target} + {s_particle}"
    print(f"Channel Spins S: {', '.join(map(str, S_values))} ({explanation})")
    
    # Table Header
    print("-" * 70)
    print(f"{'Wave':<6} {'L':<4} {'S':<6} {'Final Jπ'}")
    print("-" * 70)
    
    # Orbital angular momentum L from 0 to 6 (s to i)
    waves = {0:'s', 1:'p', 2:'d', 3:'f', 4:'g', 5:'h', 6:'i'}
    
    for l in range(7): # l = 0 to 6
        wave_name = waves.get(l, '?')
        
        # Parity selection: pi_final = pi_target * pi_particle * (-1)^l
        pi_final = pi_target * pi_particle * ((-1)**l)
        parity_str = '+' if pi_final > 0 else '-'
        
        for S in S_values:
            # Final J = |S - l| ... S + l
            min_J = abs(S - l)
            max_J = S + l
            
            current_Js = []
            curr = min_J
            while curr <= max_J:
                current_Js.append(curr)
                curr += 1
            
            # Format J values with parity
            Jpi_list = [f"{j}{parity_str}" for j in sorted(current_Js)]
            Jpi_str = ", ".join(Jpi_list)
            
            print(f"{wave_name:<6} {l:<4} {str(S):<6} {Jpi_str}")
        
        # Separator between L groups
        print("-" * 70)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Compute allowed final Jπ from angular momentum coupling.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Examples:\n"
               "  python angular_momentum_coupling.py 0+ 1+\n"
               "  python angular_momentum_coupling.py 3/2- 1/2+\n"
               "  python angular_momentum_coupling.py  (interactive mode)"
    )
    parser.add_argument("target", nargs="?", metavar="TARGET",
                        help="Target nucleus spin-parity, e.g. 0+ or 3/2-")
    parser.add_argument("particle", nargs="?", metavar="PARTICLE",
                        help="Particle intrinsic spin-parity, e.g. 1+ or 1/2+")
    args = parser.parse_args()

    if args.target and args.particle:
        calc(args.target, args.particle)
    else:
        print("Interactive mode: enter target and particle spin-parity.")
        print("Format: Jπ, such as 3/2-, 1/2+, 0+, 1-")
        print("")
        _, _, target_text = prompt_spin_parity("Target Jπ", "Target")
        _, _, particle_text = prompt_spin_parity("Particle Jπ", "Particle")
        print("")
        calc(target_text, particle_text)
