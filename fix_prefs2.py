import re

with open("app/src/main/java/com/tkc/screener/util/AppPreferences.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    # Remove second `.remove(KEY_TOKOCRYPTO_API_KEY)`
    if line.strip() == ".remove(KEY_TOKOCRYPTO_API_KEY)":
        if any(".remove(KEY_TOKOCRYPTO_API_KEY)" in l for l in lines[i-3:i]):
            continue
    # Remove second `.remove(KEY_TOKOCRYPTO_SECRET_KEY)`
    if line.strip() == ".remove(KEY_TOKOCRYPTO_SECRET_KEY)":
        if any(".remove(KEY_TOKOCRYPTO_SECRET_KEY)" in l for l in lines[i-3:i]):
            continue
            
    # Fix the `get() = prefs.getString(...)` which returns String? but needs String fallback
    if "get() = prefs.getString(KEY_TOKOCRYPTO_API_KEY, null)" in line:
        line = line.replace("null", '""').replace('\n', '') + " ?: \"\"\n"
    if "get() = prefs.getString(KEY_TOKOCRYPTO_SECRET_KEY, null)" in line:
        line = line.replace("null", '""').replace('\n', '') + " ?: \"\"\n"
        
    # Remove duplicate constant declarations
    if "private const val KEY_TOKOCRYPTO_API_KEY" in line:
        if any("private const val KEY_TOKOCRYPTO_API_KEY" in l for l in new_lines[-5:]):
            continue
    if "private const val KEY_TOKOCRYPTO_SECRET_KEY" in line:
        if any("private const val KEY_TOKOCRYPTO_SECRET_KEY" in l for l in new_lines[-5:]):
            continue

    new_lines.append(line)

with open("app/src/main/java/com/tkc/screener/util/AppPreferences.kt", "w") as f:
    f.writelines(new_lines)
