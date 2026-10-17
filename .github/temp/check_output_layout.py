import os, sys, time, tempfile, shutil, ctypes, json, subprocess
from ctypes import wintypes
ctypes.windll.shcore.SetProcessDpiAwareness(2)
appdata = tempfile.mkdtemp()
os.environ["APPDATA"] = appdata
import tkinter as tk
project = r"d:\X\ND\Level-Matcher"
sys.path.insert(0, project)
import Level_Matcher_GUI as gui
from PIL import Image

user32, gdi32 = ctypes.windll.user32, ctypes.windll.gdi32
for function, argument_types, result_type in (
        (user32.GetDC, [ctypes.c_void_p], ctypes.c_void_p), (user32.ReleaseDC, [ctypes.c_void_p, ctypes.c_void_p], ctypes.c_int),
        (user32.GetAncestor, [ctypes.c_void_p, ctypes.c_uint], ctypes.c_void_p), (user32.GetWindowRect, [ctypes.c_void_p, ctypes.c_void_p], wintypes.BOOL),
        (user32.PrintWindow, [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint], wintypes.BOOL),
        (gdi32.CreateCompatibleDC, [ctypes.c_void_p], ctypes.c_void_p), (gdi32.CreateCompatibleBitmap, [ctypes.c_void_p, ctypes.c_int, ctypes.c_int], ctypes.c_void_p),
        (gdi32.SelectObject, [ctypes.c_void_p, ctypes.c_void_p], ctypes.c_void_p), (gdi32.DeleteObject, [ctypes.c_void_p], wintypes.BOOL), (gdi32.DeleteDC, [ctypes.c_void_p], wintypes.BOOL),
        (gdi32.GetDIBits, [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint, ctypes.c_uint, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint], ctypes.c_int)):
    function.argtypes, function.restype = argument_types, result_type

class BitmapInfoHeader(ctypes.Structure):
    _fields_ = [("biSize", wintypes.DWORD), ("biWidth", wintypes.LONG), ("biHeight", wintypes.LONG), ("biPlanes", wintypes.WORD), ("biBitCount", wintypes.WORD),
                ("biCompression", wintypes.DWORD), ("biSizeImage", wintypes.DWORD), ("biXPelsPerMeter", wintypes.LONG), ("biYPelsPerMeter", wintypes.LONG),
                ("biClrUsed", wintypes.DWORD), ("biClrImportant", wintypes.DWORD)]

def capture_window(widget, path):
    handle = user32.GetAncestor(widget.winfo_id(), 2)
    rect = wintypes.RECT(); user32.GetWindowRect(handle, ctypes.byref(rect))
    width, height = rect.right - rect.left, rect.bottom - rect.top
    screen_dc = user32.GetDC(None); memory_dc = gdi32.CreateCompatibleDC(screen_dc); bitmap = gdi32.CreateCompatibleBitmap(screen_dc, width, height)
    gdi32.SelectObject(memory_dc, bitmap)
    user32.PrintWindow(handle, memory_dc, 2)
    header = BitmapInfoHeader(ctypes.sizeof(BitmapInfoHeader), width, -height, 1, 32, 0, 0, 0, 0, 0, 0)
    buffer = ctypes.create_string_buffer(width * height * 4)
    gdi32.GetDIBits(memory_dc, bitmap, 0, height, buffer, ctypes.byref(header), 0)
    gdi32.DeleteObject(bitmap); gdi32.DeleteDC(memory_dc); user32.ReleaseDC(None, screen_dc)
    Image.frombuffer("RGBA", (width, height), buffer, "raw", "BGRA", 0, 1).convert("RGB").save(path)

def wait_until_idle(root, app, timeout):
    start = time.time()
    time.sleep(0.3)
    while app.is_busy and time.time() - start < timeout:
        root.update(); time.sleep(0.05)
    root.update()
    return round(time.time() - start, 1)

def descendants(widget):
    for child in widget.winfo_children():
        yield child
        yield from descendants(child)

base = tempfile.mkdtemp()
parent = os.path.join(base, "S34")
workspace = os.path.join(parent, "new")
os.makedirs(workspace)
for name in ("S34_30si_a_g_a_n_resonances.ens", "S34_33s_n_g_n_n_resonances.ens", "XREF.txt"):
    shutil.copy(os.path.join(project, "data", "raw", name), workspace)
with open(os.path.join(workspace, "my_notes.txt"), "w") as handle:
    handle.write("user file")
output_folder = os.path.join(workspace, "level_matcher_files")
print("output folder name constant:", gui.output_folder_name)

root = tk.Tk()
app = gui.Level_Matcher_Interface(root)
root.geometry("+10+10"); root.update()
print("Generate figures default:", app.figures_variable.get())

# 1. Scan: output folder inside the workspace, nothing created beside the workspace
app.workspace_variable.set(workspace)
app.scan_workspace()
print("scan seconds:", wait_until_idle(root, app, 120))
print("workspace folder entries:", sorted(os.listdir(workspace)))
print("beside the workspace:", sorted(os.listdir(parent)), "(expected ['new'])")
print("output folder after scan:", sorted(os.listdir(output_folder)))

# 2. Run without figures
wanted = [i for i in app.dataset_table.get_children() if app.dataset_table.item(i, "values")[0] in ("L", "Q")]
app.dataset_table.selection_set(wanted)
app.run_matcher()
print("run 1 (no figures) seconds:", wait_until_idle(root, app, 1200), "| status:", app.status_variable.get())
log = app.log_text.get("1.0", "end")
command_lines = [line for line in log.splitlines() if line.startswith("> ")]
print("commands:", [(os.path.basename(line.split()[2]), "--figures" in line) for line in command_lines[-3:]])
entries = sorted(os.listdir(output_folder))
print("output folder after run 1:", entries)
print("subfolders:", [e for e in entries if os.path.isdir(os.path.join(output_folder, e))], "| png files:", [e for e in entries if e.endswith(".png")], "| error in log:", "Traceback" in log or "[ERROR]" in log)

# 3. Turn figures on (saved immediately), run again
checkbutton = [w for w in descendants(root) if w.winfo_class() == "TCheckbutton"][0]
checkbutton.invoke(); root.update()
print("checkbox text:", checkbutton.cget("text"), "| value:", app.figures_variable.get(), "| saved:", json.load(open(app.settings_path)).get("generate_figures"))
app.log_text.configure(state="normal"); app.log_text.delete("1.0", "end"); app.log_text.configure(state="disabled")
app.run_matcher()
print("run 2 (figures) seconds:", wait_until_idle(root, app, 1200), "| status:", app.status_variable.get())
log = app.log_text.get("1.0", "end")
command_lines = [line for line in log.splitlines() if line.startswith("> ")]
print("commands:", [(os.path.basename(line.split()[2]), "--figures" in line) for line in command_lines[-3:]])
entries = sorted(os.listdir(output_folder))
print("output folder after run 2:", entries)
print("subfolders:", [e for e in entries if os.path.isdir(os.path.join(output_folder, e))], "| error in log:", "Traceback" in log or "[ERROR]" in log)
print("png sizes (KB):", {e: round(os.path.getsize(os.path.join(output_folder, e)) / 1024) for e in entries if e.endswith(".png")})
print("beside the workspace still:", sorted(os.listdir(parent)), "| workspace user file untouched:", open(os.path.join(workspace, "my_notes.txt")).read() == "user file")
capture_window(root, r"d:\X\ND\Level-Matcher\.github\temp\main_window.png")
root.destroy()

# 4. Reopen: option and workspace remembered
root = tk.Tk()
app = gui.Level_Matcher_Interface(root)
root.geometry("+10+10"); root.update()
print("reopened: generate figures:", app.figures_variable.get(), "| workspace:", app.workspace_variable.get() == workspace)
wait_until_idle(root, app, 120)
root.destroy()

# 5. Schema validator ignores parameters.json
result = subprocess.run([sys.executable, os.path.join(project, "scripts", "validate_schema.py"), "--json_dir", output_folder], capture_output=True, text=True)
print("validator exit:", result.returncode, "|", result.stdout.strip().splitlines()[-1] if result.stdout.strip() else result.stderr.strip()[-300:])
shutil.rmtree(base, ignore_errors=True)
