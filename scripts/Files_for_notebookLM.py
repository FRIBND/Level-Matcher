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
2. Create a dedicated 'docs/notebook_files' directory for organization.
3. Iterate through the project directory to locate source files.
4. Use shutil.copy to duplicate files into the target folder with the .txt extension.
5. Provide console feedback on conversion status.
"""
import shutil
import os

# Define target directory for professional organization
target_directory = 'docs/notebook_files'
os.makedirs(target_directory, exist_ok=True)

# Define files to copy (Source -> Destination Name)
file_map = {
    'Level_Matcher.py': 'Level_Matcher.txt',
    'Feature_Engineer.py': 'Feature_Engineer.txt',
    'Level_Clusterer.py': 'Level_Clusterer.txt',
    'Dataset_Parser.py': 'Dataset_Parser.txt',
    'Combined_Visualizer.py': 'Combined_Visualizer.txt',
    'scripts/hyperparameter_tuning/Hyperparameter_Tuner.py': 'Hyperparameter_Tuner.txt'
}

print(f"[INFO] Converting Python scripts to Text for NotebookLM in {target_directory}...")

for source_file, destination_name in file_map.items():
    if os.path.exists(source_file):
        destination_path = os.path.join(target_directory, destination_name)
        shutil.copy(source_file, destination_path)
        print(f"  - {source_file} → {destination_path}")
    else:
        print(f"  [WARNING] Source file not found: {source_file}")

print("[INFO] Conversion complete.")
