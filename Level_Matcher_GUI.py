"""
Level Matcher Graphical Interface
======================================

# High-level Structure and Workflow Explanation:
======================================

The user picks a workspace folder that holds their own ENSDF .ens files, including the adopted file whose X records list the dataset letters (XREF.txt is the fallback).
All generated files are written inside that workspace, so the tool never reads from or writes to
the tool's own data/raw folder.

Workflow Diagram:

  [Browse: workspace folder]
            |
            v
  Dataset_Parser.py (subprocess, automatic) --> [workspace/json/test_dataset_{letter}.json]
            |
            v
  [Dataset list: letter, reaction, source file]  <-- parsed from the parser log
            |
            v
  [User selects two or more datasets] --> [Run Matching]
            |
            v
  Level_Matcher.py --> Combined_Visualizer.py (subprocesses) --> [workspace/outputs/]
            |
            v
  [Live log panel]

Numbered Technical Steps:
1. Workspace selection: the user chooses the folder containing .ens files; the XREF list is read from the adopted .ens file there.
2. Automatic scan: Dataset_Parser.py converts every .ens file to JSON inside workspace/json; the parser log
   builds the dataset list.
3. Selection: at least two datasets are required for cross-dataset matching.
4. Run: matcher and visualizer run as subprocesses so training never freezes the window; output streams to the log.
5. Results: reports and figures are written to workspace/outputs.

Architecture:
- `Level_Matcher_Interface`: Tkinter window class holding all widgets and state.
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
from tkinter import filedialog, messagebox, ttk

project_directory = os.path.dirname(os.path.abspath(__file__))


class Level_Matcher_Interface:
    def __init__(self, root_window):
        self.root_window = root_window
        root_window.title("Level Matcher")
        root_window.geometry("900x700")
        root_window.minsize(700, 500)

        self.log_queue = queue.Queue()
        self.is_busy = False
        self.dataset_records = []  # One dictionary per detected dataset: letter, filename, reaction

        # 1. Workspace selection row
        workspace_frame = ttk.LabelFrame(root_window, text="1. Workspace folder (contains .ens files including the adopted file)")
        workspace_frame.pack(fill="x", padx=10, pady=(10, 5))
        self.workspace_variable = tk.StringVar()
        ttk.Entry(workspace_frame, textvariable=self.workspace_variable).pack(side="left", fill="x", expand=True, padx=5, pady=5)
        ttk.Button(workspace_frame, text="Browse...", command=self.browse_workspace).pack(side="left", padx=5, pady=5)

        # 2. Dataset selection list
        dataset_frame = ttk.LabelFrame(root_window, text="2. Select datasets to match (Ctrl or Shift for multiple)")
        dataset_frame.pack(fill="both", padx=10, pady=5)
        self.dataset_listbox = tk.Listbox(dataset_frame, selectmode="extended", height=12, exportselection=False)
        dataset_scrollbar = ttk.Scrollbar(dataset_frame, orient="vertical", command=self.dataset_listbox.yview)
        self.dataset_listbox.configure(yscrollcommand=dataset_scrollbar.set)
        self.dataset_listbox.pack(side="left", fill="both", expand=True, padx=(5, 0), pady=5)
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
        self.log_text = tk.Text(log_frame, wrap="none", state="disabled", font=("Consolas", 9))
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
        self.dataset_listbox.delete(0, "end")
        # The adopted dataset file begins with the XREF (X records) letter list; fall back to XREF.txt.
        adopted_files = sorted(name for name in os.listdir(workspace_path) if "adopted" in name.lower() and name.endswith(".ens"))
        xref_path = os.path.join(workspace_path, adopted_files[0] if adopted_files else "XREF.txt")
        command = [sys.executable, os.path.join(project_directory, "Dataset_Parser.py"),
                   "--raw_dir", workspace_path,
                   "--xref", xref_path,
                   "--output_dir", os.path.join(workspace_path, "json")]
        self.status_variable.set("Scanning workspace...")

        def finish_scan(succeeded):
            for record in sorted(self.dataset_records, key=lambda item: item["letter"]):
                self.dataset_listbox.insert(
                    "end", f"{record['letter']}  |  {record['reaction']}  |  {record['filename']}")
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
        selected_letters = []
        for index in self.dataset_listbox.curselection():
            selected_letters.append(self.dataset_listbox.get(index).split("|")[0].strip())
        if len(selected_letters) < 2:
            messagebox.showerror("Datasets", "Select at least two datasets to match.")
            return

        shared_arguments = ["--labels", *selected_letters,
                            "--json_dir", os.path.join(workspace_path, "json"),
                            "--output_dir", os.path.join(workspace_path, "outputs")]
        commands = [
            [sys.executable, os.path.join(project_directory, "Level_Matcher.py"), *shared_arguments],
            [sys.executable, os.path.join(project_directory, "Combined_Visualizer.py"), *shared_arguments],
        ]
        self.status_variable.set("Running matching (training may take several minutes)...")

        def finish_run(succeeded):
            self.status_variable.set("Done. Results are in the workspace outputs folder." if succeeded
                                     else "Run failed. See log.")

        self.start_subprocess_sequence(commands, finish_run)


if __name__ == "__main__":
    root_window = tk.Tk()
    Level_Matcher_Interface(root_window)
    root_window.mainloop()
