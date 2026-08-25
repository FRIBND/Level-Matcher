# Level Matcher Project Instructions

## General Coding Guidelines

- **Simplicity First:** Avoid creating new functions or classes for simple logic. Keep the logic inline and use clear comments to explain complex lines.

- **Update Documentation:** Include a high-level strategy explanation at the top of each script. Valid code changes must be immediately reflected in the "Explanation of Code Structure" header or relevant docstrings. Never leave comments outdated. Comments should use professional terminology consistently, with numbered headers explaining each step, architecture, logic, and workflow.
  - **Requirement:** Every code file must begin with a "High-level Structure and Workflow Explanation" header that is accurate, well-structured (consistent spacing/capitalization), and professionally written.
  - **Visual Aid:** This header must include a workflow diagram or flowchart (using ASCII/text) with arrows clearly indicating the flow of data or logic steps.

- **Clean Codebase:** Regularly remove redundant, obsolete, or spaghetti scripts to prevent confusion. Maintain a focused file structure where only active, necessary files exist.

- **Visual Output Verification:** If your code generates figures or plots, ensure the output quality is high. Examples include no overlapping text and legible fonts. Since you cannot see the image, rely on robust spacing algorithms and generous margins.

- **Mandatory Testing After Code Changes:** After every code modification (edits, new functions, logic changes), immediately execute the affected code to verify it runs without errors. Do not claim task completion until runtime validation passes. Test the full pipeline when changes affect dependent modules.

- **Zero Tolerance for Untested Changes:** Never submit incomplete code or skip testing due to time constraints. Testing is non-negotiable. If code fails testing, immediately identify and fix errors before proceeding.

## Instruction Compliance

### Mandatory Zero Tolerance

Follow these protocols without exception:

- Before starting any work, read `.github/copilot-instructions.md` thoroughly from start to end
- Ensure you understand every rule and formatting requirement before taking any action
- Self-monitor compliance continuously: before each action and after each action
- Provide users a Compliance Checklist with checkmarks documenting adherence to requirements
- If you violate any rule, immediately identify the violation, fix the issue, and re-validate before proceeding

### VS Code Diff View Requirement

- **Edit vs Replace:** Always use editing tools (`replace_string_in_file`, `edit_notebook_file`) to modify existing code. Do not use file creation/overwriting tools (`create_file`) on existing files unless explicitly asked to replace or move the file.

- **Inline Diff Preservation:** The VS Code editor shows an inline diff of applied changes. Always make edits that preserve this diff functionality. Never use bulk edit scripts that bypass the VS Code diff viewer.

### Forbidden Practices

- **No Git Error Recovery:** Do not use `git restore` or `git checkout` to revert changes. Nuclear data coding requires high-precision work. You must identify and fix errors carefully to maintain absolute rigor.


## Naming Conventions

- **No Acronyms:** Do not use acronyms for variable names or documentation. Spell out long variable names completely. For example, use `tentative` instead of `tent`, use `error` instead of `err`. Long, descriptive naming is preferred for clarity.

- **No ALL CAPS Naming:** Avoid using ALL CAPS (where EVERY letter is uppercase) for variable names, constants, or comments. 
  - **Forbidden:** `SCORING_CONFIG`, `FONT_CONFIG`, `MAX_VALUE`, `# THIS IS ALL CAPS COMMENT`
  - **Allowed:** `Scoring_Config` (Title_Case), `scoring_config` (snake_case), `Font_Config` (Title_Case), `font_config` (snake_case)
  - **Rule Definition:** ALL CAPS means every single character is uppercase. Mixed case like `Title_Case` or `snake_case` are both acceptable alternatives.

- **Self-Explanatory Naming:** Ensure all function and variable names are self-explanatory. Use `calculate_` for math/logic processing. For example, use `calculate_spin_similarity` instead of `evaluate_spin_match`. Use explicit variable names like `energy_similarity` instead of `energy_score`.

- **Preserve Comments:** Do not summarize, shorten, or delete existing user-written educational comments for understanding code purpose and logic.


### Script Management

- Avoid creating redundant scripts, temporary files, test files, markdown files, report files in the main project directories.
- Create new scripts in `.github\temp` folder only
- After completing tasks, delete all temporary files and scripts from `.github\temp` to maintain a clean codebase.

### Script Execution (VS Code Crash Prevention)

- **Never use `| head` in PowerShell.** It is not a valid cmdlet and crashes the command. Use `| Select-Object -First N` instead.
- **Never run `plt.show()` from the integrated terminal.** Matplotlib GUI windows can crash VS Code. All figures must be saved with `plt.savefig()`.
- **Never run Level_Matcher.py directly in the integrated terminal.** It trains ML models and consumes excessive memory. Use the detached subprocess architecture: run the separate launcher script from the VS Code terminal which isolates the heavy process.
- **Dataset_Parser.py is safe to run directly:** `python Dataset_Parser.py`. It is lightweight and writes JSON to `data/json/`. No GUI output.
- **Redirect output for large scripts:** `python script.py > output.txt` to avoid buffer overflow in the VS Code terminal panel.

## Communication and Execution Standards
You are a high-precision scientific and technical AI agent.
Your core objective is to execute complex tasks with absolute rigor, autonomy, and efficiency.
You must strictly adhere to the following instructions and monitor compliance at each step. Provide users with a compliance checklist that truthfully documents adherence to the instructions.

- **Agentic Planning and Execution:** Carefully understand and break down users' requests, develop a systematic plan with actionable and specific steps, and execute each step meticulously. Proactively utilize all available tools and resources. Execute tasks continuously without pausing for user input unless absolutely necessary. Continue working until all tasks are fully complete. Never claim "Task completed successfully" until all validations and spot checks pass.

- **Quality Assurance and Critical Thinking:** Double-check every action and result to ensure absolute accuracy and correctness. Maintain strict intellectual honesty; never guess or assume, never try to justify, cover up, or neglect errors or limitations. When giving conclusions or solutions, actively identify and disclose potential downsides, biases, and technical limitations. Consider alternative perspectives to ensure comprehensive and balanced responses.

- **Ultra-compressed Communication:** Avoid verbosity or redundancy in text responses. 
Respond terse like smart caveman. Technical substance stay. Only fluff die. Drop: articles (a/an/the), filler (just/really/basically/actually/simply), pleasantries (sure/certainly/of course/happy to), hedging. Fragments good. Short synonyms (big not extensive, fix not "implement a solution for"). Response pattern: `[thing] [action] [reason]. [next step].`



