"""
# High-level Structure and Workflow Explanation:
======================================

Module Purpose:
This utility script automates the conversion of project Python source files (.py) into plain text files (.txt).
This is specifically required for integration with NotebookLM, which does not natively support .py file uploads.

Workflow Diagram:
[Start]
   |
   v
[Identify Source Files] --> [Check File Existence]
   |                            |
   v                            v
[Perform Copy Operation] <--- [True/False]
   |
   v
[Rename to .txt]
   |
   v
[End]

Technical Steps:
1. Define a mapping of core project files (Matcher, Engineer, Cluster, etc.).
2. Create a dedicated 'developer/notebook_files' directory for organization.
3. Iterate through the project directory to locate source files.
4. Use shutil.copy to duplicate files into the target folder with the .txt extension.
5. Provide console feedback on conversion status.
"""
import shutil
import os

repository_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
target_directory = os.path.join(repository_root, 'developer', 'notebook_files')
os.makedirs(target_directory, exist_ok=True)

# Define files to copy (Source relative to the repository root -> Destination name)
file_map = {
    'Level_Matcher_GUI.py': 'Level_Matcher_GUI.txt',
    'engine/Level_Matcher.py': 'Level_Matcher.txt',
    'engine/Feature_Engineer.py': 'Feature_Engineer.txt',
    'engine/Level_Clusterer.py': 'Level_Clusterer.txt',
    'engine/Dataset_Parser.py': 'Dataset_Parser.txt',
    'engine/Combined_Visualizer.py': 'Combined_Visualizer.txt',
    'engine/Training_Metrics_Visualizer.py': 'Training_Metrics_Visualizer.txt',
    'developer/hyperparameter_tuning/Hyperparameter_Tuner.py': 'Hyperparameter_Tuner.txt'
}

print(f"[INFO] Converting Python scripts to Text for NotebookLM in {target_directory}...")

for source_name, destination_name in file_map.items():
    source_file = os.path.join(repository_root, source_name)
    if os.path.exists(source_file):
        destination_path = os.path.join(target_directory, destination_name)
        shutil.copy(source_file, destination_path)
        print(f"  - {source_name} → {destination_name}")
    else:
        print(f"  [WARNING] Source file not found: {source_file}")

print("[INFO] Conversion complete.")
