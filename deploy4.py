#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy remaining Claude Code articles."""
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')

SSH = "ssh -p 2222 root@121.43.55.151"
DOCKER = "docker exec -u www-data wordpress"

def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True, timeout=60)
    out = result.stdout.decode('utf-8', errors='replace').strip()
    err = result.stderr.decode('utf-8', errors='replace').strip()
    if result.returncode != 0 and err and 'Warning' not in err:
        print(f"ERR: {err[:200]}")
    return out

def update_post(post_id, html_content):
    escaped = html_content.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`').replace('\n', '\\n')
    cmd = f'{SSH} "{DOCKER} bash -c \'printf "%s" "{escaped}" > /tmp/p{post_id}.html\'"'
    run(cmd)
    cmd = f'{SSH} "{DOCKER} wp post update {post_id} --post_content=\"$(cat /tmp/p{post_id}.html)\" --path=/var/www/html --allow-root"'
    result = run(cmd)
    print(f"Post {post_id}: {result}")

# Update post 69 with full content
update_post(69, """<h2>Claude Code: AI Pair Programming in Your Terminal</h2>
<p>If you are a programmer, you have definitely experienced these moments: staring at documentation for an unfamiliar framework, debugging until 3am without finding the issue, writing repetitive boilerplate code until you want to scream.</p>
<p>Last November, I tried Claude Code for the first time. To be honest, I was skeptical at first -- another AI coding tool? But after two weeks of use, my development efficiency improved by at least 40%. Not because the code it writes is amazing, but because it handles all the necessary but uncreative grunt work.</p>
<h3>What is Claude Code?</h3>
<p>Simply put: it is an AI programming assistant that runs in your terminal. Unlike IDE plugins like Cursor and GitHub Copilot, Claude Code operates directly in your project directory -- reading files, writing code, running commands, committing git -- like an AI colleague sitting next to you.</p>
<h3>Claude Code vs Cursor vs GitHub Copilot</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>Dimension</th><th>Claude Code</th><th>Cursor</th><th>GitHub Copilot</th></tr>
<tr><td>Where it runs</td><td>Terminal (CLI)</td><td>IDE (VS Code fork)</td><td>IDE Plugin</td></tr>
<tr><td>File access</td><td>Read/write any files</td><td>Current project</td><td>Current file</td></tr>
<tr><td>Run commands</td><td>Direct terminal execution</td><td>Partial support</td><td>Not supported</td></tr>
<tr><td>Context scope</td><td>Entire project</td><td>Current project</td><td>Current file</td></tr>
</table>
<h3>My Top 5 Use Cases</h3>
<p><strong>1. Understand unfamiliar code:</strong> Ask it to analyze a new project's authentication flow. It reads all related files in seconds.</p>
<p><strong>2. Batch refactoring:</strong> "Migrate all API calls from axios to fetch." Used to take half a day, now 10 minutes.</p>
<p><strong>3. Write tests:</strong> "Generate complete unit tests for UserService covering all edge cases." Quality is quite good.</p>
<p><strong>4. Debug:</strong> "Run npm test, if there are errors, analyze the cause and fix them." It runs tests, reads errors, modifies code, and re-verifies automatically.</p>
<p><strong>5. Git operations:</strong> "Check recent changes, write a proper commit message, commit and push." It analyzes your diff and writes better commit messages.</p>
<h3>Tips</h3>
<p><strong>Write a CLAUDE.md:</strong> Create a CLAUDE.md file in your project root with project conventions, tech stack, and code style. Claude Code reads it automatically on startup.</p>
<pre><code># CLAUDE.md
## Project Conventions
- Use TypeScript, no any types
- Functions under 50 lines
- Use pnpm for dependencies
## Tech Stack
- Frontend: Next.js + Tailwind
- Backend: FastAPI + PostgreSQL</code></pre>
<p><strong>Break down large tasks:</strong> Do not ask it to "rewrite the entire project." Break it into small steps: "analyze architecture" then "design new solution" then "migrate step by step."</p>
<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">
<strong>Honest take:</strong> Claude Code is not omnipotent. It sometimes makes mistakes and sometimes confidently says wrong things. Trust but verify. Treat it as an intern -- capable but needs your oversight.
</div>""")

print("Post 69 updated!")

# Create post 70 - Claude Code plugins
result = run(f'{SSH} "{DOCKER} wp post create --post_title=\\"Claude Code Ecosystem: Plugins That Double Your Efficiency\\" --post_name=\\"claude-code-plugins\\" --post_status=publish --post_category=17 --path=/var/www/html --allow-root"')
print(f"Post 70: {result}")

# Update post 70 content
update_post(70, """<h2>Claude Code Ecosystem: Plugins That Double Your Efficiency</h2>
<p>If you have started using Claude Code, congratulations -- you are already in the first tier of AI programming. But the real power of Claude Code lies in its surrounding plugins and tools. Like an iPhone without apps is just a phone, Claude Code with the right plugins can truly shine.</p>
<h3>Plugin 1: Superpowers -- Give Claude Code "Superpowers"</h3>
<p>Superpowers is a skills package for Claude Code. You can think of it as installing a "skill tree" for Claude Code.</p>
<p><strong>What problem does it solve?</strong> Native Claude Code is smart, but it does not know your project conventions, your team habits, or your preferred workflow. Superpowers turns this "tacit knowledge" into reusable rule files.</p>
<p><strong>Core capabilities:</strong></p>
<ul>
<li><strong>TDD Mode:</strong> Enforce writing tests before code. Yes, it forces you to follow TDD workflow</li>
<li><strong>Code Review:</strong> Auto-trigger review after writing code, checking for security vulnerabilities, performance issues, and code style</li>
<li><strong>Security Scan:</strong> Auto-detect SQL injection, XSS, hardcoded secrets, and other OWASP Top 10 vulnerabilities</li>
<li><strong>Git Workflow:</strong> Automated commit messages, PR creation, code review</li>
</ul>
<p><strong>My experience:</strong> The TDD skill pack made me very uncomfortable at first -- I wanted to write code directly, but it insisted I write tests first. But after being forced to use it for two weeks, my bug rate genuinely decreased.</p>
<h3>Plugin 2: MCP (Model Context Protocol) -- Connect "Peripherals"</h3>
<p>MCP is like a USB port for Claude Code. Through MCP, Claude Code can connect to various external tools and services.</p>
<p>Several useful MCP servers:</p>
<ul>
<li><strong>GitHub MCP:</strong> Let Claude Code directly operate GitHub -- create issues, review PRs, manage repositories</li>
<li><strong>PostgreSQL MCP:</strong> Query databases directly. Claude Code can "see" your data</li>
<li><strong>Slack MCP:</strong> Read Slack messages, send notifications</li>
<li><strong>Filesystem MCP:</strong> Enhanced file system operations supporting more file types</li>
</ul>
<p><strong>My experience:</strong> PostgreSQL MCP lets me ask Claude Code directly: "Which API endpoints were slowest last week?" It queries the database, analyzes data, and gives conclusions. Previously this would take me writing SQL queries for ages.</p>
<h3>Plugin 3: Hooks System -- Auto-execute Checks</h3>
<p>Hooks are Claude Code's "automation triggers." You can set commands to auto-execute when specific events occur.</p>
<pre><code>{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "command": "npx prettier --write \\"$FILE_PATH\\"",
        "description": "Auto-format after edits"
      },
      {
        "matcher": "Write|Edit",
        "command": "npx tsc --noEmit",
        "description": "Type-check after edits"
      }
    ]
  }
}</code></pre>
<p>This auto-formats code and checks type errors every time Claude Code modifies a file. No need to manually run commands.</p>
<h3>Plugin 4: Claude Code + Playwright -- Automated Testing Pipeline</h3>
<p>Ask Claude Code to write Playwright E2E tests:</p>
<pre><code>"Write an E2E test for the login page using Playwright,
covering: normal login, wrong password, non-existent account"</code></pre>
<p>Claude Code will: install Playwright -> analyze page structure -> write test code -> run tests -> auto-fix if they fail. The whole process just needs you to state one requirement.</p>
<h3>My Recommended Setup</h3>
<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">
<tr style="background:#f5f5f5"><th>Priority</th><th>Plugin/MCP</th><th>Problem Solved</th></tr>
<tr><td>Must-have</td><td>Superpowers</td><td>Standardize workflow</td></tr>
<tr><td>Must-have</td><td>GitHub MCP</td><td>Code management automation</td></tr>
<tr><td>Recommended</td><td>PostgreSQL MCP</td><td>Database queries</td></tr>
<tr><td>Recommended</td><td>Hooks System</td><td>Auto-checks</td></tr>
<tr><td>As needed</td><td>Playwright</td><td>Automated testing</td></tr>
</table>
<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">
<strong>Installation tip:</strong> Do not install everything at once. Install Superpowers first, get comfortable with it, then add the next one. One new plugin per week is a good pace. Installing too many at once will just distract you.
</div>""")

print("Post 70 updated!")
print("ALL DONE!")
