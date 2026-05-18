#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy Claude Code tutorial articles as new posts."""
import subprocess
import sys
import os

# Force UTF-8 output
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

def create_post(title, html_content, slug, category_id=17):
    # Write content to file in container
    escaped = html_content.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`').replace('\n', '\\n')
    cmd = f'{SSH} "{DOCKER} bash -c \'printf "%s" "{escaped}" > /tmp/new_post.html\'"'
    run(cmd)
    # Write title to file too to avoid encoding issues
    title_clean = title.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('`', '\\`')
    cmd = f'{SSH} "{DOCKER} bash -c \'printf "%s" "{title_clean}" > /tmp/new_title.txt\'"'
    run(cmd)
    # Create post
    cmd = f'{SSH} "{DOCKER} wp post create --post_title=\"$(cat /tmp/new_title.txt)\" --post_name=\"{slug}\" --post_content=\"$(cat /tmp/new_post.html)\" --post_status=publish --post_category={category_id} --path=/var/www/html --allow-root"'
    result = run(cmd)
    print(f"Created: {slug}")

# Article 22 - Claude Code intro
create_post(
    "Claude Code: AI Pair Programming in Your Terminal",
    '<h2>Claude Code: AI Pair Programming in Your Terminal</h2>\n<p>If you are a programmer, you have definitely experienced these moments: staring at documentation for an unfamiliar framework, debugging until 3am without finding the issue, writing repetitive boilerplate code until you want to scream.</p>\n<p>Last November, I tried Claude Code for the first time. To be honest, I was skeptical at first -- another AI coding tool? But after two weeks of use, my development efficiency improved by at least 40%. Not because the code it writes is amazing, but because it handles all the "necessary but uncreative" grunt work.</p>\n<h3>What is Claude Code?</h3>\n<p>Simply put: it is an AI programming assistant that runs in your terminal. Unlike IDE plugins like Cursor and GitHub Copilot, Claude Code operates directly in your project directory -- reading files, writing code, running commands, committing git -- like an AI colleague sitting next to you.</p>\n<h3>Get Started in 5 Minutes</h3>\n<pre><code>npm install -g @anthropic-ai/claude-code</code></pre>\n<p>Run in your project directory:</p>\n<pre><code>cd my-project\nclaude</code></pre>\n<h3>My Top 5 Use Cases</h3>\n<p><strong>1. Understand unfamiliar code:</strong> "What is the authentication flow of this project? Draw the call chain." It analyzes all related files in seconds.</p>\n<p><strong>2. Batch refactoring:</strong> "Migrate all API calls from axios to fetch." Used to take half a day, now 10 minutes.</p>\n<p><strong>3. Write tests:</strong> "Write complete unit tests for UserService covering all edge cases." The generated test code quality is quite good.</p>\n<p><strong>4. Debug:</strong> "Run npm test, if there are errors, analyze the cause and fix them." It runs tests, reads error messages, modifies code, and re-verifies automatically.</p>\n<p><strong>5. Git operations:</strong> "Check recent changes, write a proper commit message, commit and push." It analyzes your diff and writes a better commit message than you would.</p>\n<h3>Tips</h3>\n<p><strong>Write a CLAUDE.md:</strong> Create a CLAUDE.md file in your project root with project conventions and tech stack. Claude Code reads it automatically on startup.</p>\n<p><strong>Dont give huge tasks at once:</strong> "Rewrite the entire project" gives poor results. Break it down: "Analyze architecture" -> "Design new solution" -> "Migrate step by step".</p>\n<div style="background:#d4edda;padding:15px;border-left:4px solid #28a745;margin:20px 0">\n<strong>Honest take:</strong> Claude Code is not omnipotent. It sometimes makes mistakes. Trust but verify. Treat it as an intern -- capable but needs your oversight.\n</div>',
    "claude-code-intro"
)

# Article 23 - Claude Code plugins
create_post(
    "Claude Code Ecosystem: Plugins That Double Your Efficiency",
    '<h2>Claude Code Ecosystem: Plugins That Double Your Efficiency</h2>\n<p>If you have started using Claude Code, congratulations -- you are already in the first tier of AI programming. But the real power of Claude Code lies in its surrounding plugins and tools.</p>\n<h3>Plugin 1: Superpowers -- Give Claude Code "Superpowers"</h3>\n<p>Superpowers is a skills package for Claude Code. Core capabilities:</p>\n<ul>\n<li><strong>TDD Mode:</strong> Enforce writing tests before code</li>\n<li><strong>Code Review:</strong> Auto-trigger review after writing code</li>\n<li><strong>Security Scan:</strong> Auto-detect SQL injection, XSS, hardcoded secrets</li>\n<li><strong>Git Workflow:</strong> Automated commit messages, PR creation</li>\n</ul>\n<p><strong>My experience:</strong> The TDD skill pack was uncomfortable at first. But after two weeks, my bug rate genuinely decreased.</p>\n<h3>Plugin 2: MCP (Model Context Protocol) -- Connect "Peripherals"</h3>\n<p>MCP is like a USB port for Claude Code. Useful MCP servers:</p>\n<ul>\n<li><strong>GitHub MCP:</strong> Create issues, review PRs, manage repos</li>\n<li><strong>PostgreSQL MCP:</strong> Query databases directly</li>\n<li><strong>Slack MCP:</strong> Read messages, send notifications</li>\n</ul>\n<p><strong>My experience:</strong> PostgreSQL MCP lets me ask: "Which API endpoints were slowest last week?" It queries the database and analyzes the data itself.</p>\n<h3>Plugin 3: Hooks System -- Auto-execute Checks</h3>\n<p>Auto-format code and type-check after every edit:</p>\n<pre><code>{\n  "hooks": {\n    "PostToolUse": [\n      {\n        "matcher": "Write|Edit",\n        "command": "npx prettier --write \\"$FILE_PATH\\"",\n        "description": "Auto-format after edits"\n      }\n    ]\n  }\n}</code></pre>\n<h3>Plugin 4: Claude Code + Playwright -- Automated Testing</h3>\n<p>Ask Claude Code to write Playwright E2E tests:</p>\n<pre><code>"Write an E2E test for the login page using Playwright,\ncovering: normal login, wrong password, non-existent account"</code></pre>\n<p>Claude Code will: install Playwright -> analyze page structure -> write tests -> run tests -> auto-fix if they fail.</p>\n<h3>My Recommended Setup</h3>\n<table border="1" cellpadding="10" cellspacing="0" style="border-collapse:collapse;width:100%">\n<tr style="background:#f5f5f5"><th>Priority</th><th>Plugin/MCP</th><th>Problem Solved</th></tr>\n<tr><td>Must-have</td><td>Superpowers</td><td>Standardize workflow</td></tr>\n<tr><td>Must-have</td><td>GitHub MCP</td><td>Code management automation</td></tr>\n<tr><td>Recommended</td><td>PostgreSQL MCP</td><td>Database queries</td></tr>\n<tr><td>Recommended</td><td>Hooks System</td><td>Auto-checks</td></tr>\n</table>\n<div style="background:#cce5ff;padding:15px;border-left:4px solid #004085;margin:20px 0">\n<strong>Installation tip:</strong> Do not install everything at once. Install Superpowers first, get comfortable with it, then add the next one. One new plugin per week is a good pace.\n</div>',
    "claude-code-plugins"
)

print("ALL CLAUDE CODE ARTICLES CREATED!")
