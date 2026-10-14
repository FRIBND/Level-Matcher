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
  Dataset_Parser.py (subprocess, automatic) --> [<workspace parent>/json/{prefix}_dataset_{letter}.json]
            |
            v
  [Dataset table: Letter | XREF | ENSDF data file]  <-- parsed from the parser log
            |
            v
  [User selects two or more datasets] --> [Run Matching]
            |
            v
  Level_Matcher.py --> Combined_Visualizer.py (subprocesses) --> [<workspace parent>/outputs/]
            |
            v
  [Live log panel]

Numbered Technical Steps:
1. Workspace selection: the user chooses the folder containing .ens files; the XREF list is read from the adopted .ens file there.
2. Automatic scan: Dataset_Parser.py converts every .ens file to JSON inside the json folder beside the workspace
   (created if missing, existing files deleted first); the parser log
   builds the dataset list.
3. Selection: at least two datasets are required for cross-dataset matching.
4. Run: matcher and visualizer run as subprocesses so training never freezes the window; output streams to the log.
5. Results: reports and figures are written to <workspace parent>/outputs.

Architecture:
- `Level_Matcher_Interface`: Tkinter window class holding all widgets and state (Arial for menus and labels, one monospaced family
  for the entry, dataset table rows and log; icon from assets/frib_logo.png when present).
- `browse_workspace`: Folder chooser; triggers the scan.
- `scan_workspace`: Starts the parser subprocess and populates the dataset list.
- `run_matching`: Starts the matcher and visualizer subprocesses in sequence.
- `start_subprocess_sequence`: Runs commands in a worker thread and pushes output lines to a queue.
- `poll_log_queue`: Main-thread timer that moves queued lines into the log widget.
"""

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
        root_window.geometry("900x720")
        root_window.minsize(700, 520)

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

        # Window icon: assets/frib_logo.png if present, otherwise a transparent icon (no default feather)
        icon_path = os.path.join(project_directory, "assets", "frib_logo.png")
        self.icon_image = tk.PhotoImage(file=icon_path) if os.path.isfile(icon_path) else tk.PhotoImage(width=16, height=16)
        root_window.iconphoto(True, self.icon_image)

        self.log_queue = queue.Queue()
        self.is_busy = False
        self.dataset_records = []  # One dictionary per detected dataset: letter, filename, reaction

        # 0. Header (the operating system draws the title bar, so the app name is repeated here in Arial)
        ttk.Label(root_window, text="Level Matcher", font=(menu_family, 16, "bold")).pack(anchor="w", padx=12, pady=(10, 0))

        # 1. Workspace selection row
        workspace_frame = ttk.LabelFrame(root_window, text="1. Workspace folder (contains .ens files including the adopted file)")
        workspace_frame.pack(fill="x", padx=10, pady=(10, 5))
        self.workspace_variable = tk.StringVar()
        ttk.Entry(workspace_frame, textvariable=self.workspace_variable, font=self.monospace_font).pack(side="left", fill="x", expand=True, padx=5, pady=5)
        ttk.Button(workspace_frame, text="Browse...", command=self.browse_workspace).pack(side="left", padx=5, pady=5)

        # 2. Dataset selection table: Letter | XREF reaction | data file
        dataset_frame = ttk.LabelFrame(root_window, text="2. Select datasets to match (Ctrl or Shift for multiple)")
        dataset_frame.pack(fill="both", padx=10, pady=5)
        self.dataset_table = ttk.Treeview(dataset_frame, columns=("letter", "reaction", "filename"), show="headings",
                                          selectmode="extended", height=10)
        self.dataset_table.heading("letter", text="Letter", anchor="w")
        self.dataset_table.heading("reaction", text="XREF", anchor="w")
        self.dataset_table.heading("filename", text="ENSDF data file", anchor="w")
        self.dataset_table.column("letter", width=70, stretch=False, anchor="w")
        self.dataset_table.column("reaction", width=300, stretch=False, anchor="w")
        self.dataset_table.column("filename", width=420, stretch=True, anchor="w")
        dataset_scrollbar = ttk.Scrollbar(dataset_frame, orient="vertical", command=self.dataset_table.yview)
        self.dataset_table.configure(yscrollcommand=dataset_scrollbar.set)
        self.dataset_table.pack(side="left", fill="both", expand=True, padx=(5, 0), pady=5)
        dataset_scrollbar.pack(side="left", fill="y", pady=5, padx=(0, 5))

        # 3. Action buttons
        action_frame = ttk.Frame(root_window)
        action_frame.pack(fill="x", padx=10, pady=5)
        self.run_button = ttk.Button(action_frame, text="Run Matching", command=self.run_matching)
        self.run_button.pack(side="left", padx=(0, 5))
        self.status_variable = tk.StringVar(value="Browse to a workspace folder containing .ens files.")
        ttk.Label(action_frame, textvariable=self.status_variable).pack(side="left", padx=10)

        # 4. Log panel
        log_frame = ttk.LabelFrame(root_window, text="Log")
        log_frame.pack(fill="both", expand=True, padx=10, pady=(5, 10))
        self.log_text = tk.Text(log_frame, wrap="none", state="disabled", font=self.monospace_font)
        log_scrollbar = ttk.Scrollbar(log_frame, orient="vertical", command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=log_scrollbar.set)
        self.log_text.pack(side="left", fill="both", expand=True, padx=(5, 0), pady=5)
        log_scrollbar.pack(side="left", fill="y", pady=5, padx=(0, 5))

        self.poll_log_queue()

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
        chosen_folder = filedialog.askdirectory(title="Select workspace folder")
        if chosen_folder:
            self.workspace_variable.set(os.path.normpath(chosen_folder))
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
                self.status_variable.set(f"Found {len(self.dataset_records)} datasets. Select two or more, then click Run Matching.")

        self.start_subprocess_sequence([command], finish_scan)

    def run_matching(self):
        workspace_path = self.get_workspace()
        if workspace_path is None:
            return
        selected_letters = [self.dataset_table.item(row_identifier, "values")[0] for row_identifier in self.dataset_table.selection()]
        if len(selected_letters) < 2:
            messagebox.showerror("Datasets", "Select at least two datasets to match.")
            return

        shared_arguments = ["--labels", *selected_letters,
                            "--json_dir", os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "json"),
                            "--output_dir", os.path.join(os.path.dirname(os.path.normpath(workspace_path)), "outputs")]
        commands = [
            [sys.executable, os.path.join(project_directory, "Level_Matcher.py"), *shared_arguments],
            [sys.executable, os.path.join(project_directory, "Combined_Visualizer.py"), *shared_arguments],
        ]
        self.status_variable.set("Running matching (training may take several minutes)...")

        def finish_run(succeeded):
            self.status_variable.set("Done. Results are in the outputs folder next to the workspace." if succeeded
                                     else "Run failed. See log.")

        self.start_subprocess_sequence(commands, finish_run)


if __name__ == "__main__":
    root_window = tk.Tk()
    Level_Matcher_Interface(root_window)
    root_window.mainloop()
