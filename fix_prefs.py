import re

with open("app/src/main/java/com/tkc/screener/util/AppPreferences.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if skip:
        skip = False
        continue
    
    if "fun hasTokocryptoCredentials(): Boolean = hasTokocryptoCredentials()" in line:
        continue
    
    if line.strip() == ".remove(KEY_TOKOCRYPTO_API_KEY)":
        if i > 0 and ".remove(KEY_TOKOCRYPTO_API_KEY)" in new_lines[-1]:
            continue
    if line.strip() == ".remove(KEY_TOKOCRYPTO_SECRET_KEY)":
        if i > 0 and ".remove(KEY_TOKOCRYPTO_SECRET_KEY)" in new_lines[-1]:
            continue
            
    if "var tokocryptoApiKey: String" in line and i > 125: # the duplicate definition
        # we know it's 4 lines
        skip = True
        lines[i+1] = ""
        lines[i+2] = ""
        lines[i+3] = ""
        continue
        
    if "var tokocryptoSecretKey: String" in line and i > 125: # the duplicate definition
        skip = True
        lines[i+1] = ""
        lines[i+2] = ""
        lines[i+3] = ""
        continue
        
    if "private const val KEY_TOKOCRYPTO_API_KEY" in line:
        if i > 0 and "private const val KEY_TOKOCRYPTO_API_KEY" in new_lines[-1]:
            continue
    if "private const val KEY_TOKOCRYPTO_SECRET_KEY" in line:
        if i > 0 and "private const val KEY_TOKOCRYPTO_SECRET_KEY" in new_lines[-1]:
            continue
            
    if "?: prefs.getString(KEY_TOKOCRYPTO_API_KEY, \"\").orEmpty()" in line:
        continue
    if "?: prefs.getString(KEY_TOKOCRYPTO_SECRET_KEY, \"\").orEmpty()" in line:
        continue
    if ".putString(KEY_TOKOCRYPTO_API_KEY, value.trim())" in line:
        if i > 0 and ".putString(KEY_TOKOCRYPTO_API_KEY, value.trim())" in new_lines[-1]:
            continue
    if ".putString(KEY_TOKOCRYPTO_SECRET_KEY, value.trim())" in line:
        if i > 0 and ".putString(KEY_TOKOCRYPTO_SECRET_KEY, value.trim())" in new_lines[-1]:
            continue

    new_lines.append(line)

with open("app/src/main/java/com/tkc/screener/util/AppPreferences.kt", "w") as f:
    f.writelines(new_lines)
