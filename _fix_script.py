data = open('upload-to-shzl.ps1', 'rb').read().decode('utf-8')
# Fix: raw string wrote \' as literal backslash+quote, PS doesn't understand this
# Fix 1: \'ERROR\' -> 'ERROR'
data = data.replace("\\'ERROR\\'", "'ERROR'")
# Fix 2: replace date format with simple approach - use Get-Date with -Format as separate call
data = data.replace("(Get-Date).ToString('yyyy-MM-dd')", "((Get-Date).ToString('yyyy-MM-dd'))")
# Fix 3: Check for any other \' patterns
import re
data = re.sub(r"\\\\'", "'", data)
print('Done fixing, writing...')
open('upload-to-shzl.ps1', 'w', encoding='utf-8', newline='\n').write(data)
lines = open('upload-to-shzl.ps1', 'rb').read().decode('utf-8').split('\n')
print('Line 168:', repr(lines[167]))
