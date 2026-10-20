"""
Level Matcher Graphical Interface
======================================

# High-level Structure and Workflow Explanation:
======================================

The user picks a workspace folder that holds their own ENSDF .ens files. The folder must contain the adopted file: its X records list the dataset letters used to match each .ens file to its XREF letter.
Every file the tool writes (parsed JSON, parameters.json, reports and optional figures) goes into one folder named
level_matcher_files in the raw folder beside the workspace folder, without subfolders
(for example workspace .../S34/new -> .../S34/raw/level_matcher_files). Nothing is ever created or changed inside the workspace
folder itself (it holds the user's .ens files).

This file is the only program the user runs: start it with `python Level_Matcher_GUI.py` (or a shortcut to it). The six programs
in engine/ are started by this window as subprocesses and are not meant to be edited or run by hand.

Workflow Diagram:

  [Browse: workspace folder]
            |
            v
  Dataset_Parser.py (engine/, subprocess, automatic) --> [<workspace parent>/raw/level_matcher_files/{nuclide}_{XREF}.json]
            |
            v
  [Dataset table: Letter | XREF | ENSDF data file | Levels]  <-- parsed from the parser log
            |
            v
  [User selects two or more datasets]      [Parameters dialog: feature engineering, level matcher]
            |                                          |
            +------------------+-----------------------+
                               |
                               v
                      [Run Level Matcher]  (saves level_matcher_files/parameters.json)
                               |
                               v
  engine/Level_Matcher.py (--parameters) --> engine/Combined_Visualizer.py (unless Generate figures is unticked) --> [<workspace parent>/raw/level_matcher_files/]
            |
            v
  [Live log panel]

Numbered Technical Steps:
1. Workspace selection: the user chooses the folder containing .ens files; the dataset letters are read from the X records of the adopted .ens file there (a folder without an adopted file is refused).
   The last folder is saved in %APPDATA%/Level_Matcher/settings.json and reopened and rescanned at the next start.
2. Automatic scan: engine/Dataset_Parser.py converts every .ens file to JSON inside <workspace parent>/raw/level_matcher_files
   (created if missing; earlier parser output there is deleted first, other files are left alone); the parser log
   builds the dataset list, including the number of levels parsed from the L records of each dataset. The workspace folder itself is only read.
3. Selection: at least two datasets are required for cross-dataset matching.
4. Parameters: the dialog has an input box for each scoring parameter at the top of engine/Feature_Engineer.py (listed first) and for
   each threshold at the top of engine/Level_Matcher.py (defaults are read from those files; values are range-checked; pointing at a
   box shows what the parameter does), plus the Generate figures option (on by default; its default is set in this file).
   Figure layout and fonts are not in the dialog (edit the top of engine/Combined_Visualizer.py). Changed values are saved in settings.json.
5. Run: matcher and visualizer run as subprocesses so training never freezes the window; the matcher receives all parameter
   values with --parameters (saved as level_matcher_files/parameters.json) and every run streams its output to the log.
   Unless Generate figures is unticked in the Parameters dialog, --figures is added for the matcher and engine/Combined_Visualizer.py
   runs afterwards; when unticked no PNG file is written.
6. Results: the pair report and the cluster report (plus the PNG figures when enabled) are written to the same level_matcher_files folder.

Architecture:
- `Level_Matcher_Interface`: Tkinter window class holding all widgets and state (Arial for menus and labels, one monospaced family
  for the entry, dataset table rows and log; DPI aware, so pixel sizes follow the Windows display scale). Layout top to bottom:
  workspace row, dataset table, action bar (Select All, Clear Selection | Parameters, Run Level Matcher), log, footer (status text | small FRIB logo).
  The title bar icon is the FRIB emblem (assets/frib_logo.ico, multi-size).
- `read_script_defaults`: Reads parameter defaults from the top of the engine scripts without importing them.
- `open_parameter_dialog`: One fixed-size panel with a row for every parameter: group | name | input | default. Feature engineering
  parameters come first (input column headed Label), then level matcher parameters (headed Probability). It opens beside the main
  window with the same height and top edge; a scrollbar appears only when the rows do not fit that height. Has validation and Reset to Defaults.
- `save_settings`: Writes the last workspace and the changed parameter values to settings.json.
- `browse_workspace`: Folder chooser; triggers the scan.
- `scan_workspace`: Starts the parser subprocess and populates the dataset list.
- `run_matcher`: Starts the matcher and visualizer subprocesses in sequence.
- `start_subprocess_sequence`: Runs commands in a worker thread and pushes output lines to a queue.
- `poll_log_queue`: Main-thread timer that moves queued lines into the log widget.
"""

import ast
import json
import os
import queue
import re
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, font as tkfont, messagebox, ttk

project_directory = os.path.dirname(os.path.abspath(__file__))
engine_directory = os.path.join(project_directory, "engine")  # Programs this window runs in subprocesses; users never edit them
output_folder_name = "level_matcher_files"  # Created in the raw folder beside the workspace folder, never inside it; holds every file the tool writes (no subfolders)

# Parameters of the Parameters dialog. Row = (key path, label, kind, minimum, maximum, meaning); kind is float, int, bool or a tuple of choices.
# Feature engineering parameters (Feature_Engineer.py) come first, then the level matcher parameters (Level_Matcher.py); the scripts stay the source of the defaults
# (except the options in interface_option_defaults below).
parameter_sections = [
    ("Feature Engineering", "Label", [
        ("Energy", [
            ("Scoring_Config.Energy.Sigma_Scale", "Energy kernel width (larger = stricter)", float, 0.001, 10.0,
             "Energy score = exp(-value x z^2), z = energy difference in combined uncertainties. Larger is stricter.")]),
        ("Spin similarity", [
            ("Scoring_Config.Spin.Match_Firm", "Same spin, both firm", float, 0.0, 1.0,
             "Spin score when the spin is the same and both assignments are firm."),
            ("Scoring_Config.Spin.Match_Strong", "Same spin, any tentative", float, 0.0, 1.0,
             "Spin score when the spin is the same but an assignment is tentative."),
            ("Scoring_Config.Spin.Mismatch_Weak", "Spin differs by 1, any tentative", float, 0.0, 1.0,
             "Spin score when the spin differs by 1 and an assignment is tentative."),
            ("Scoring_Config.Spin.Mismatch_Strong", "Spin differs by 1, both firm", float, 0.0, 1.0,
             "Spin score when the spin differs by 1 and both assignments are firm."),
            ("Scoring_Config.Spin.Mismatch_Firm", "Spin differs by 2 or more", float, 0.0, 1.0,
             "Spin score when the spin differs by 2 or more.")]),
        ("Parity similarity", [
            ("Scoring_Config.Parity.Match_Firm", "Same parity, both firm", float, 0.0, 1.0,
             "Parity score when the parity is the same and both assignments are firm."),
            ("Scoring_Config.Parity.Match_Strong", "Same parity, any tentative", float, 0.0, 1.0,
             "Parity score when the parity is the same but an assignment is tentative."),
            ("Scoring_Config.Parity.Mismatch_Weak", "Opposite parity, any tentative", float, 0.0, 1.0,
             "Parity score when the parity is opposite and an assignment is tentative."),
            ("Scoring_Config.Parity.Mismatch_Firm", "Opposite parity, both firm", float, 0.0, 1.0,
             "Parity score when the parity is opposite and both assignments are firm.")]),
        ("Spin-parity ambiguity", [
            ("Scoring_Config.Specificity.Formula", "Formula", ("sqrt", "linear", "log", "tunable"), None, None,
             "Penalty for n = options of level 1 x options of level 2: sqrt = 1/sqrt(n), linear = 1/n, log = 1/(1+log10 n), tunable uses Alpha."),
            ("Scoring_Config.Specificity.Alpha", "Alpha (tunable formula only)", float, 0.0, 100.0,
             "Tunable penalty = 1/(1 + Alpha x (n - 1)); higher is stricter.")]),
        ("Missing data, veto", [
            ("Scoring_Config.General.Neutral_Remap_Factor", "Missing-data score (0.5 to 1)", float, 0.5, 1.0,
             "Training rule: replaces 'unknown' (0.5) when spin, parity or gamma data are missing; higher is a milder penalty."),
            ("Scoring_Config.Label.Spin_Veto_Max", "Spin veto (reject if score is at most)", float, 0.0, 1.0,
             "Training rule: a spin score at or below this value gives match probability 0, whatever the other scores are."),
            ("Scoring_Config.Label.Parity_Veto_Max", "Parity veto (reject if score is at most)", float, 0.0, 1.0,
             "Training rule: a parity score at or below this value gives match probability 0, whatever the other scores are.")]),
        ("Energy rescue", [
            ("Scoring_Config.Feature_Correlation.Enabled", "Enabled", bool, None, None,
             "Training rule: softens the energy penalty when spin and parity, or the gamma pattern, agree strongly (likely calibration offset)."),
            ("Scoring_Config.Feature_Correlation.Rescue_Threshold", "Score that triggers rescue", float, 0.0, 1.0,
             "Training rule: rescue triggers when the spin and parity scores, or the gamma score, reach this value."),
            ("Scoring_Config.Feature_Correlation.Rescue_Exponent", "Energy boost exponent (1 = none)", float, 0.1, 1.0,
             "Training rule: on rescue the energy score is raised to this power; 0.5 is a strong boost, 1 is none.")])]),
    ("Level Matcher", "Probability", [
        ("Thresholds", [
            ("pairwise_output_threshold", "Pair report minimum probability", float, 0.0, 1.0,
             "Pairs with a lower match probability are left out of the pair report and the clustering."),
            ("clustering_merge_threshold", "Cluster merge minimum probability", float, 0.0, 1.0,
             "Two levels join one cluster only if their match probability is at least this value (pairs below the pair report minimum are never considered).")]),
        ("Output", [
            ("generate_figures", "Generate figures", bool, None, None,
             "Writes the PNG figures (level schemes, cluster table, training diagnostics) next to the results; they can be several megabytes. Untick to skip them.")])]),
]
parameter_rows = {row[0]: row for _, _, groups in parameter_sections for _, rows in groups for row in rows}
# Options owned by the interface itself: they appear in the dialog like parameters but are not variables of the scripts
interface_option_defaults = {"generate_figures": True}


def read_script_defaults(key_paths):
    """Reads the default of every key path (for example Scoring_Config.Energy.Sigma_Scale) from the top of the scripts without importing them."""
    top_level_names = {key_path.split(".")[0] for key_path in key_paths}
    top_level_values = {}
    for script_name in ("Level_Matcher.py", "Feature_Engineer.py"):
        with open(os.path.join(engine_directory, script_name), "r", encoding="utf-8") as script_file:
            for node in ast.parse(script_file.read()).body:
                if isinstance(node, ast.Assign) and isinstance(node.targets[0], ast.Name) and node.targets[0].id in top_level_names:
                    top_level_values[node.targets[0].id] = ast.literal_eval(node.value)
    defaults = {}
    for key_path in key_paths:
        value = top_level_values
        for part in key_path.split("."):
            value = value[part]
        defaults[key_path] = value
    return defaults


class Level_Matcher_Interface:
    def __init__(self, root_window):
        self.root_window = root_window
        root_window.title("Level Matcher")
        # Pixel sizes are multiplied by the display scale (1.0 at 96 dpi, 2.0 on a 200% display)
        display_scale = root_window.winfo_fpixels("1i") / 96
        window_width = min(int(840 * display_scale), root_window.winfo_screenwidth() - 40)
        window_height = min(int(750 * display_scale), root_window.winfo_screenheight() - int(100 * display_scale))
        # Top-left corner: this window and the Parameters dialog beside it (same height) then stay fully on the screen
        root_window.geometry(f"{window_width}x{window_height}+{int(20 * display_scale)}+{int(10 * display_scale)}")
        root_window.minsize(int(700 * display_scale), int(520 * display_scale))

        # Menus, labels, buttons and table headings use Arial; text inside boxes (entry, table rows, log) uses one monospaced family
        installed_fonts = set(tkfont.families())
        menu_family = next((name for name in ("Arial", "Liberation Sans", "Helvetica") if name in installed_fonts), "Helvetica")
        monospace_family = next((name for name in ("JetBrains Mono SemiBold", "JetBrains Mono", "JuliaMono", "Fira Code", "Consolas", "Courier New")
                                 if name in installed_fonts), "Courier")
        for named_font in ("TkDefaultFont", "TkMenuFont", "TkHeadingFont", "TkCaptionFont", "TkTextFont", "TkTooltipFont"):
            tkfont.nametofont(named_font).configure(family=menu_family, size=10)
        tkfont.nametofont("TkFixedFont").configure(family=monospace_family, size=10)
        self.monospace_font = tkfont.Font(family=monospace_family, size=10)
        table_style = ttk.Style()
        table_style.configure("Treeview", font=self.monospace_font, rowheight=self.monospace_font.metrics("linespace") + 6)
        table_style.configure("Treeview.Heading", font=(menu_family, 10, "bold"))

        # Title bar icon
        title_icon_path = os.path.join(project_directory, "assets", "frib_logo.ico")
        if os.name == "nt" and os.path.isfile(title_icon_path):
            root_window.iconbitmap(default=title_icon_path)
        logo_png_path = os.path.join(project_directory, "assets", "frib_logo.png")

        self.log_queue = queue.Queue()
        self.is_busy = False
        self.dataset_records = []  # One dictionary per detected dataset: letter, filename, reaction
        self.dataset_level_counts = {}  # Parsed level count per dataset letter, read from the parser log
        self.xref_path = ""

        # Settings remembered between sessions: last workspace folder and the parameter values changed from the defaults
        self.settings_path = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "Level_Matcher", "settings.json")
        try:
            with open(self.settings_path, "r", encoding="utf-8") as settings_file:
                self.settings = json.load(settings_file)
        except (OSError, ValueError):
            self.settings = {}
        last_workspace = self.settings.get("last_workspace", "")
        script_key_paths = [key_path for key_path in parameter_rows if key_path not in interface_option_defaults]
        self.parameter_defaults = {**read_script_defaults(script_key_paths), **interface_option_defaults}
        saved_parameters = self.settings.get("parameters", {})
        self.parameter_values = {key_path: saved_parameters.get(key_path, default) for key_path, default in self.parameter_defaults.items()}

        # Footer: status text on the left, small FRIB logo on the right
        ttk.Separator(root_window, orient="horizontal").pack(side="bottom", fill="x")
        footer_frame = tk.Frame(root_window, background="white")
        footer_frame.pack(side="bottom", fill="x")
        self.status_variable = tk.StringVar(value="Browse to a workspace folder containing .ens files.")
        tk.Label(footer_frame, textvariable=self.status_variable, background="white", anchor="w").pack(side="left", padx=12)
        if os.path.isfile(logo_png_path):
            logo_image = tk.PhotoImage(file=logo_png_path)
            logo_shrink_factor = max(1, round(logo_image.width() / (36 * display_scale)))
            self.footer_logo = logo_image.subsample(logo_shrink_factor)
            tk.Label(footer_frame, image=self.footer_logo, background="white").pack(side="right", padx=12, pady=2)

        # 1. Workspace selection row
        workspace_frame = ttk.LabelFrame(root_window, text="Workspace folder contains .ens files")
        workspace_frame.pack(fill="x", padx=10, pady=(10, 5))
        self.workspace_variable = tk.StringVar()
        ttk.Entry(workspace_frame, textvariable=self.workspace_variable, font=self.monospace_font).pack(side="left", fill="x", expand=True, padx=5, pady=5)
        self.browse_button = ttk.Button(workspace_frame, text="Browse", command=self.browse_workspace)
        self.browse_button.pack(side="left", padx=5, pady=5)

        # 2. Dataset selection table: Letter | XREF reaction | data file | number of levels
        dataset_frame = ttk.LabelFrame(root_window, text="Select datasets to match levels (Ctrl or Shift for multiple)")
        dataset_frame.pack(fill="both", expand=True, padx=10, pady=5)
        self.dataset_table = ttk.Treeview(dataset_frame, columns=("letter", "reaction", "filename", "levels"), show="headings",
                                          selectmode="extended", height=8)
        self.dataset_table.heading("letter", text="Letter", anchor="w")
        self.dataset_table.heading("reaction", text="XREF", anchor="w")
        self.dataset_table.heading("filename", text="ENSDF data file", anchor="w")
        self.dataset_table.heading("levels", text="Levels", anchor="w")
        self.dataset_table.column("letter", width=int(70 * display_scale), stretch=False, anchor="w")
        self.dataset_table.column("reaction", width=int(300 * display_scale), stretch=False, anchor="w")
        self.dataset_table.column("filename", width=int(380 * display_scale), stretch=True, anchor="w")
        self.dataset_table.column("levels", width=int(60 * display_scale), stretch=False, anchor="w")
        dataset_scrollbar = ttk.Scrollbar(dataset_frame, orient="vertical", command=self.dataset_table.yview)
        self.dataset_table.configure(yscrollcommand=dataset_scrollbar.set)
        self.dataset_table.pack(side="left", fill="both", expand=True, padx=(5, 0), pady=5)
        dataset_scrollbar.pack(side="left", fill="y", pady=5, padx=(0, 5))

        # 3. Action bar: selection helpers on the left, primary action on the right
        action_frame = ttk.Frame(root_window)
        action_frame.pack(fill="x", padx=15, pady=5)
        ttk.Button(action_frame, text="Select All",
                   command=lambda: self.dataset_table.selection_set(self.dataset_table.get_children())).pack(side="left")
        ttk.Button(action_frame, text="Clear Selection",
                   command=lambda: self.dataset_table.selection_remove(self.dataset_table.selection())).pack(side="left", padx=(5, 0))
        self.run_button = ttk.Button(action_frame, text="Run Level Matcher", command=self.run_matcher)
        self.run_button.pack(side="right", padx=(0, 25))
        ttk.Button(action_frame, text="Parameters", command=self.open_parameter_dialog).pack(side="right", padx=(0, 5))

        # 4. Log panel
        log_frame = ttk.LabelFrame(root_window, text="Log")
        log_frame.pack(fill="both", expand=True, padx=10, pady=(5, 10))
        self.log_text = tk.Text(log_frame, wrap="word", state="disabled", font=self.monospace_font, height=8)
        log_scrollbar = ttk.Scrollbar(log_frame, orient="vertical", command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=log_scrollbar.set)
        self.log_text.pack(side="left", fill="both", expand=True, padx=(5, 0), pady=5)
        log_scrollbar.pack(side="left", fill="y", pady=5, padx=(0, 5))

        self.poll_log_queue()

        # Reopen the last workspace and rescan it automatically
        if last_workspace and os.path.isdir(last_workspace):
            self.workspace_variable.set(last_workspace)
            root_window.after(300, self.scan_workspace)
    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def append_log(self, message):
        self.log_text.configure(state="normal")
        self.log_text.insert("end", message + "\n")
        self.log_text.see("end")
        self.log_text.configure(state="disabled")

    def set_busy(self, busy):
        self.is_busy = busy
        state = "disabled" if busy else "normal"
        # Browse starts a scan that rewrites the parsed JSON files, so it must not run during a run
        for button in (self.browse_button, self.run_button):
            button.configure(state=state)

    def get_workspace(self):
        workspace_path = self.workspace_variable.get().strip()
        if not workspace_path or not os.path.isdir(workspace_path):
            messagebox.showerror("Workspace", "Select an existing workspace folder first.")
            return None
        # The tool never writes into the workspace folder: refuse a folder (for example raw or a drive root) whose output folder would lie inside it
        workspace_absolute = os.path.abspath(workspace_path)
        if os.path.commonpath([workspace_absolute, os.path.abspath(self.get_output_directory(workspace_path))]) == workspace_absolute:
            messagebox.showerror("Workspace", "Select the folder that holds the .ens files (not a raw folder or a drive root): the tool never writes into the selected folder.")
            return None
        return workspace_path

    def get_output_directory(self, workspace_path):
        """<workspace parent>/raw/level_matcher_files: beside the folder holding the .ens files, never inside it."""
        return os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "raw", output_folder_name)

    def browse_workspace(self):
        chosen_folder = filedialog.askdirectory(title="Select workspace folder", initialdir=self.workspace_variable.get() or None)
        if chosen_folder:
            self.workspace_variable.set(os.path.normpath(chosen_folder))
            self.save_settings()
            self.scan_workspace()

    def save_settings(self):
        self.settings["last_workspace"] = self.workspace_variable.get()
        self.settings["parameters"] = {key_path: value for key_path, value in self.parameter_values.items()
                                       if value != self.parameter_defaults[key_path]}
        os.makedirs(os.path.dirname(self.settings_path), exist_ok=True)
        with open(self.settings_path, "w", encoding="utf-8") as settings_file:
            json.dump(self.settings, settings_file, indent=4)

    def open_parameter_dialog(self):
        """One fixed-size panel with every parameter (feature engineering first, then level matcher); OK validates, saves and uses them from the next run."""
        display_scale = self.root_window.winfo_fpixels("1i") / 96
        menu_family = tkfont.nametofont("TkDefaultFont").actual("family")
        group_font, section_font = (menu_family, 10, "bold"), (menu_family, 11, "bold")
        dialog = tk.Toplevel(self.root_window)
        dialog.title("Parameters")
        dialog.transient(self.root_window)
        container = ttk.Frame(dialog)
        container.pack(fill="both", expand=True, padx=14, pady=(10, 0))
        canvas = tk.Canvas(container, highlightthickness=0)
        scrollbar = ttk.Scrollbar(container, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=scrollbar.set)
        canvas.pack(side="left", fill="both", expand=True)
        inner = ttk.Frame(canvas)
        canvas.create_window((0, 0), window=inner, anchor="nw")
        inner.bind("<Configure>", lambda event: canvas.configure(scrollregion=canvas.bbox("all")))
        help_variable = tk.StringVar(value="Point at a parameter to see what it does.")
        help_label = tk.Label(dialog, textvariable=help_variable, anchor="nw", justify="left", height=3)
        help_label.pack(fill="x", padx=14, pady=(8, 4))

        # Rows: group | item | input | default; each section heads its own input column (Label, Probability)
        variables = {}
        grid_row = 0
        for section_index, (section_title, input_heading, groups) in enumerate(parameter_sections):
            ttk.Label(inner, text=section_title, font=section_font).grid(row=grid_row, column=0, columnspan=2, sticky="sw", pady=(12 if section_index else 0, 2))
            ttk.Label(inner, text=input_heading, font=group_font).grid(row=grid_row, column=2, sticky="sw", padx=8, pady=(0, 2))
            if section_index == 0:  # the Default heading stands once, in the first section heading row
                ttk.Label(inner, text="Default", font=group_font).grid(row=grid_row, column=3, sticky="sw", padx=8, pady=(0, 2))
            ttk.Separator(inner, orient="horizontal").grid(row=grid_row + 1, column=0, columnspan=4, sticky="ew", pady=(0, 2))
            grid_row += 2
            for group_index, (group_title, rows) in enumerate(groups):
                for row_in_group, (key_path, label, kind, minimum, maximum, meaning) in enumerate(rows):
                    row_padding = (8 if row_in_group == 0 and group_index > 0 else 2, 2)
                    if row_in_group == 0:
                        ttk.Label(inner, text=group_title, font=group_font).grid(row=grid_row, column=0, sticky="w", padx=(0, 8), pady=row_padding)
                    label_widget = ttk.Label(inner, text=label)
                    label_widget.grid(row=grid_row, column=1, sticky="w", padx=8, pady=row_padding)
                    current_value = self.parameter_values[key_path]
                    if kind is bool:
                        variables[key_path] = tk.BooleanVar(value=current_value)
                        input_widget = ttk.Checkbutton(inner, variable=variables[key_path])
                        valid_range = ""
                    elif isinstance(kind, tuple):
                        variables[key_path] = tk.StringVar(value=current_value)
                        input_widget = ttk.Combobox(inner, textvariable=variables[key_path], values=kind, state="readonly", width=8)
                        valid_range = f" Choices: {', '.join(kind)}."
                    else:
                        variables[key_path] = tk.StringVar(value=str(current_value))
                        input_widget = ttk.Entry(inner, textvariable=variables[key_path], width=9, font=self.monospace_font)
                        valid_range = f" Range {minimum} to {maximum}."
                    input_widget.grid(row=grid_row, column=2, sticky="w", padx=8, pady=row_padding)
                    default_widget = ttk.Label(inner, text=str(self.parameter_defaults[key_path]), foreground="gray")
                    default_widget.grid(row=grid_row, column=3, sticky="w", padx=8, pady=row_padding)
                    # The meaning of a parameter appears in the help line under the list
                    for widget, event_name in ((label_widget, "<Enter>"), (input_widget, "<Enter>"), (default_widget, "<Enter>"), (input_widget, "<FocusIn>")):
                        widget.bind(event_name, lambda event, text=f"{key_path}: {meaning}{valid_range}": help_variable.set(text))
                    grid_row += 1

        def reset_to_defaults():
            for key_path, variable in variables.items():
                default_value = self.parameter_defaults[key_path]
                variable.set(default_value if isinstance(variable, tk.BooleanVar) else str(default_value))

        def apply_parameters():
            new_values = {}
            for key_path, (_, label, kind, minimum, maximum, _meaning) in parameter_rows.items():
                raw_value = variables[key_path].get()
                if kind is bool or isinstance(kind, tuple):
                    new_values[key_path] = raw_value
                    continue
                try:
                    value = kind(raw_value)
                except ValueError:
                    messagebox.showerror("Parameters", f"'{label}' must be a {'whole number' if kind is int else 'number'}.", parent=dialog)
                    return
                if not minimum <= value <= maximum:
                    messagebox.showerror("Parameters", f"'{label}' must be between {minimum} and {maximum}.", parent=dialog)
                    return
                new_values[key_path] = value
            self.parameter_values = new_values
            self.save_settings()
            changed_count = sum(1 for key_path, value in new_values.items() if value != self.parameter_defaults[key_path])
            self.status_variable.set(f"Parameters saved ({changed_count} changed from the defaults); they apply from the next run.")
            dialog.destroy()

        button_frame = ttk.Frame(dialog)
        button_frame.pack(fill="x", padx=14, pady=(4, 12))
        ttk.Button(button_frame, text="OK", command=apply_parameters).pack(side="left")
        ttk.Button(button_frame, text="Cancel", command=dialog.destroy).pack(side="left", padx=5)
        ttk.Button(button_frame, text="Reset to Defaults", command=reset_to_defaults).pack(side="left")

        # Same height as the main window so both line up; a scrollbar appears only when the rows do not fit that height
        inner.update_idletasks()
        canvas.configure(width=inner.winfo_reqwidth(), height=inner.winfo_reqheight())
        dialog.update_idletasks()
        dialog_height = self.root_window.winfo_height()
        canvas_height = dialog_height - (dialog.winfo_reqheight() - inner.winfo_reqheight())
        if canvas_height < inner.winfo_reqheight():
            scrollbar.pack(side="right", fill="y")
            dialog.bind("<MouseWheel>", lambda event: canvas.yview_scroll(-int(event.delta / 120), "units"))
        canvas.configure(height=canvas_height)
        dialog.update_idletasks()
        dialog_width = dialog.winfo_reqwidth()
        help_label.configure(wraplength=dialog_width - int(30 * display_scale))
        # Beside the main window (right, else left) with the top edges aligned, so both stay visible
        gap = int(8 * display_scale)
        screen_width = dialog.winfo_screenwidth()
        main_left = self.root_window.winfo_rootx()
        main_right = main_left + self.root_window.winfo_width()
        if main_right + gap + dialog_width <= screen_width:
            dialog_left = main_right + gap
        elif main_left - gap - dialog_width >= 0:
            dialog_left = main_left - gap - dialog_width
        else:
            dialog_left = screen_width - dialog_width
        dialog.geometry(f"{dialog_width}x{dialog_height}+{dialog_left}+{self.root_window.winfo_y()}")
        dialog.resizable(False, False)
        dialog.bind("<Escape>", lambda event: dialog.destroy())
        dialog.grab_set()

    # ------------------------------------------------------------------
    # Subprocess handling
    # ------------------------------------------------------------------
    def start_subprocess_sequence(self, commands, on_finished):
        """Run commands one after another in a worker thread; stop at the first failure."""
        self.set_busy(True)

        def worker():
            environment = dict(os.environ, PYTHONUNBUFFERED="1", PYTHONIOENCODING="utf-8", MPLBACKEND="Agg")
            creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
            succeeded = True
            for command in commands:
                self.log_queue.put(("line", "> " + " ".join(command)))
                process = subprocess.Popen(
                    command, cwd=project_directory, env=environment, stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                    creationflags=creation_flags)
                assert process.stdout is not None  # stdout=PIPE above guarantees a stream
                for output_line in process.stdout:
                    self.log_queue.put(("line", output_line.rstrip()))
                if process.wait() != 0:
                    self.log_queue.put(("line", f"[ERROR] Command failed with exit code {process.returncode}."))
                    succeeded = False
                    break
            self.log_queue.put(("done", (succeeded, on_finished)))

        threading.Thread(target=worker, daemon=True).start()

    def poll_log_queue(self):
        """Main-thread timer: move queued worker output into the log widget."""
        while True:
            try:
                kind, payload = self.log_queue.get_nowait()
            except queue.Empty:
                break
            if kind == "line":
                self.append_log(payload)
                parser_match = re.match(r"Processing '(.+?)' -> Dataset (\w) \(DSID: (.*)\)\.\.\.", payload)
                if parser_match:
                    self.dataset_records.append({
                        "filename": parser_match.group(1), "letter": parser_match.group(2),
                        "reaction": parser_match.group(3)})
                # The parser prints this fixed line right after saving a dataset; the wording is not decoration
                level_count_match = re.match(r"Levels in dataset (\w): (\d+)$", payload)
                if level_count_match:
                    self.dataset_level_counts[level_count_match.group(1)] = int(level_count_match.group(2))
            else:
                succeeded, on_finished = payload
                self.set_busy(False)
                if on_finished:
                    on_finished(succeeded)
        self.root_window.after(100, self.poll_log_queue)

    # ------------------------------------------------------------------
    # Button callbacks
    # ------------------------------------------------------------------
    def scan_workspace(self):
        if self.is_busy:  # A running scan must not be restarted halfway, or two logs would mix their dataset records
            return
        workspace_path = self.get_workspace()
        if workspace_path is None:
            return
        self.dataset_records = []
        self.dataset_level_counts = {}
        self.dataset_table.delete(*self.dataset_table.get_children())
        # The adopted dataset file begins with the XREF (X records) letter list; it is required, there is no substitute
        adopted_files = sorted(name for name in os.listdir(workspace_path) if "adopted" in name.lower() and name.endswith(".ens"))
        if not adopted_files:
            messagebox.showerror("Workspace", "This folder has no adopted .ens file. The adopted file lists the dataset letters (XREF) and is required.")
            self.status_variable.set("No adopted .ens file in the selected folder.")
            return
        self.xref_path = os.path.join(workspace_path, adopted_files[0])
        command = [sys.executable, os.path.join(engine_directory, "Dataset_Parser.py"),
                   "--raw_dir", workspace_path,
                   "--xref", self.xref_path,
                   "--output_dir", self.get_output_directory(workspace_path)]
        self.status_variable.set("Scanning workspace...")

        def finish_scan(succeeded):
            for record in sorted(self.dataset_records, key=lambda item: item["letter"]):
                level_count = self.dataset_level_counts.get(record["letter"])
                self.dataset_table.insert("", "end", values=(record["letter"], record["reaction"], record["filename"],
                                                             "-" if level_count is None else level_count))
            if not succeeded:
                self.status_variable.set("Scan failed. See log.")
            elif not self.dataset_records:
                self.status_variable.set("No usable .ens files found in the workspace.")
            else:
                self.status_variable.set(f"Found {len(self.dataset_records)} datasets. Select two or more, then click Run Level Matcher.")

        self.start_subprocess_sequence([command], finish_scan)

    def run_matcher(self):
        workspace_path = self.get_workspace()
        if workspace_path is None:
            return
        selected_letters = [self.dataset_table.item(row_identifier, "values")[0] for row_identifier in self.dataset_table.selection()]
        if len(selected_letters) < 2:
            messagebox.showerror("Datasets", "Select at least two datasets to match.")
            return

        # The parameter values of this run are saved next to the results as parameters.json (nested like the script variables)
        output_directory = self.get_output_directory(workspace_path)
        os.makedirs(output_directory, exist_ok=True)
        nested_parameters = {}
        for key_path, value in self.parameter_values.items():
            *parent_names, leaf_name = key_path.split(".")
            target = nested_parameters
            for parent_name in parent_names:
                target = target.setdefault(parent_name, {})
            target[leaf_name] = value
        parameters_path = os.path.join(output_directory, "parameters.json")
        with open(parameters_path, "w", encoding="utf-8") as parameters_file:
            json.dump(nested_parameters, parameters_file, indent=4)

        shared_arguments = ["--labels", *selected_letters,
                            "--xref", self.xref_path,
                            "--json_dir", output_directory,
                            "--output_dir", output_directory]
        commands = [[sys.executable, os.path.join(engine_directory, "Level_Matcher.py"), *shared_arguments, "--parameters", parameters_path]]
        if self.parameter_values["generate_figures"]:
            commands[0].append("--figures")
            commands.append([sys.executable, os.path.join(engine_directory, "Combined_Visualizer.py"), *shared_arguments])
        self.status_variable.set("Running Level Matcher (model training may take a few seconds)...")

        def finish_run(succeeded):
            self.status_variable.set(f"Done. Results are in {output_directory}" if succeeded
                                     else "Run failed. See log.")

        self.start_subprocess_sequence(commands, finish_run)


if __name__ == "__main__":
    # DPI awareness: without it Windows treats the window as 96 dpi, so the title bar text and icon stay tiny on scaled displays
    if os.name == "nt":
        import ctypes
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except (AttributeError, OSError):
            ctypes.windll.user32.SetProcessDPIAware()
    root_window = tk.Tk()
    Level_Matcher_Interface(root_window)
    root_window.mainloop()
