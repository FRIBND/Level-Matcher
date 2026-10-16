"""
Level Matcher Graphical Interface
======================================

# High-level Structure and Workflow Explanation:
======================================

The user picks a workspace folder that holds their own ENSDF .ens files, including the adopted file whose X records list the dataset letters (XREF.txt is the fallback).
All outputs (parsed JSON and matching results) are written to json and outputs folders next to the workspace folder
(for example workspace .../S34/new -> .../S34/json and .../S34/outputs), so the user's .ens folder is never modified and
the tool never reads from or writes to the tool's own data/raw folder.

Workflow Diagram:

  [Browse: workspace folder]
            |
            v
  Dataset_Parser.py (subprocess, automatic) --> [<workspace parent>/json/{nuclide}_{XREF}.json]
            |
            v
  [Dataset table: Letter | XREF | ENSDF data file]  <-- parsed from the parser log
            |
            v
  [User selects two or more datasets] --> [Run Level Matcher]
            |
            v
  Level_Matcher.py --> Combined_Visualizer.py (subprocesses) --> [<workspace parent>/outputs/]
            |
            v
  [Live log panel]

Numbered Technical Steps:
1. Workspace selection: the user chooses the folder containing .ens files; the XREF list is read from the adopted .ens file there.
   The last folder is saved in %APPDATA%/Level_Matcher/settings.json and reopened and rescanned at the next start.
2. Automatic scan: Dataset_Parser.py converts every .ens file to JSON inside the json folder beside the workspace
   (created if missing, existing files deleted first); the parser log
   builds the dataset list.
3. Selection: at least two datasets are required for cross-dataset matching.
4. Run: matcher and visualizer run as subprocesses so training never freezes the window; output streams to the log.
5. Results: reports and figures are written to <workspace parent>/outputs.

Architecture:
- `Level_Matcher_Interface`: Tkinter window class holding all widgets and state (Arial for menus and labels, one monospaced family
  for the entry, dataset table rows and log; DPI aware, so pixel sizes follow the Windows display scale). Layout top to bottom:
  workspace row, dataset table, action bar (Select All, Clear Selection | Run Level Matcher), log, footer (status text | small FRIB logo).
  The title bar icon is the FRIB emblem (assets/frib_logo.ico, multi-size).
- `browse_workspace`: Folder chooser; triggers the scan.
- `scan_workspace`: Starts the parser subprocess and populates the dataset list.
- `run_matcher`: Starts the matcher and visualizer subprocesses in sequence.
- `start_subprocess_sequence`: Runs commands in a worker thread and pushes output lines to a queue.
- `poll_log_queue`: Main-thread timer that moves queued lines into the log widget.
"""

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


class Level_Matcher_Interface:
    def __init__(self, root_window):
        self.root_window = root_window
        root_window.title("Level Matcher")
        # Pixel sizes are multiplied by the display scale (1.0 at 96 dpi, 2.0 on a 200% display)
        display_scale = root_window.winfo_fpixels("1i") / 96
        window_width = min(int(900 * display_scale), root_window.winfo_screenwidth() - 40)
        window_height = min(int(720 * display_scale), root_window.winfo_screenheight() - 100)
        root_window.geometry(f"{window_width}x{window_height}")
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

        # Last used workspace folder is remembered between sessions
        self.settings_path = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "Level_Matcher", "settings.json")
        try:
            with open(self.settings_path, "r", encoding="utf-8") as settings_file:
                last_workspace = json.load(settings_file).get("last_workspace", "")
        except (OSError, ValueError):
            last_workspace = ""

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
        ttk.Button(workspace_frame, text="Browse...", command=self.browse_workspace).pack(side="left", padx=5, pady=5)

        # 2. Dataset selection table: Letter | XREF reaction | data file
        dataset_frame = ttk.LabelFrame(root_window, text="Select datasets to match levels (Ctrl or Shift for multiple)")
        dataset_frame.pack(fill="both", expand=True, padx=10, pady=5)
        self.dataset_table = ttk.Treeview(dataset_frame, columns=("letter", "reaction", "filename"), show="headings",
                                          selectmode="extended", height=8)
        self.dataset_table.heading("letter", text="Letter", anchor="w")
        self.dataset_table.heading("reaction", text="XREF", anchor="w")
        self.dataset_table.heading("filename", text="ENSDF data file", anchor="w")
        self.dataset_table.column("letter", width=int(70 * display_scale), stretch=False, anchor="w")
        self.dataset_table.column("reaction", width=int(300 * display_scale), stretch=False, anchor="w")
        self.dataset_table.column("filename", width=int(420 * display_scale), stretch=True, anchor="w")
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
        self.run_button.configure(state=state)

    def get_workspace(self):
        workspace_path = self.workspace_variable.get().strip()
        if not workspace_path or not os.path.isdir(workspace_path):
            messagebox.showerror("Workspace", "Select an existing workspace folder first.")
            return None
        return workspace_path

    def browse_workspace(self):
        chosen_folder = filedialog.askdirectory(title="Select workspace folder", initialdir=self.workspace_variable.get() or None)
        if chosen_folder:
            self.workspace_variable.set(os.path.normpath(chosen_folder))
            os.makedirs(os.path.dirname(self.settings_path), exist_ok=True)
            with open(self.settings_path, "w", encoding="utf-8") as settings_file:
                json.dump({"last_workspace": self.workspace_variable.get()}, settings_file)
            self.scan_workspace()

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
        workspace_path = self.get_workspace()
        if workspace_path is None:
            return
        self.dataset_records = []
        self.dataset_table.delete(*self.dataset_table.get_children())
        # The adopted dataset file begins with the XREF (X records) letter list; fall back to XREF.txt.
        adopted_files = sorted(name for name in os.listdir(workspace_path) if "adopted" in name.lower() and name.endswith(".ens"))
        xref_path = os.path.join(workspace_path, adopted_files[0] if adopted_files else "XREF.txt")
        self.xref_path = xref_path
        command = [sys.executable, os.path.join(project_directory, "Dataset_Parser.py"),
                   "--raw_dir", workspace_path,
                   "--xref", xref_path,
                   "--output_dir", os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "json")]
        self.status_variable.set("Scanning workspace...")

        def finish_scan(succeeded):
            for record in sorted(self.dataset_records, key=lambda item: item["letter"]):
                self.dataset_table.insert("", "end", values=(record["letter"], record["reaction"], record["filename"]))
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

        shared_arguments = ["--labels", *selected_letters,
                            "--xref", self.xref_path,
                            "--json_dir", os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "json"),
                            "--output_dir", os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "outputs")]
        commands = [
            [sys.executable, os.path.join(project_directory, "Level_Matcher.py"), *shared_arguments],
            [sys.executable, os.path.join(project_directory, "Combined_Visualizer.py"), *shared_arguments],
        ]
        self.status_variable.set("Running Level Matcher (model training may take a few seconds)...")

        def finish_run(succeeded):
            self.status_variable.set("Done. Results are in the outputs folder next to the workspace." if succeeded
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
