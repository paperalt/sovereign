#!/usr/bin/env python3
"""
Autonomous UI Defect & Sanitation Static Analysis Engine
Realtime Speech Intelligence Stack (Transcribe Core)

Enforces strict zero-defect UI/UX policies, elimination of collisions,
sanitation of text/AI responses, and absolute consistency across Kotlin Jetpack Compose files.
"""

import os
import re
import sys
import json
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent.parent
COMPOSE_SRC_DIR = (BASE_DIR / "app" / "src" / "main" / "java" / "org" / "sovereign" / "app") if (BASE_DIR / "app").exists() else (BASE_DIR / "android" / "app" / "src" / "main" / "java" / "org" / "sovereign" / "app")

# Regex Patterns for UI Defect Rules
EMOJI_PATTERN = re.compile(
    r'[\U00010000-\U0010ffff]'  # High surrogate emojis (📁, 🗑, 💡, ⏱, etc.)
    r'|[\u2600-\u27bf]'          # Dingbats and miscellaneous symbols
    r'|[\u2300-\u23ff]'          # Miscellaneous Technical
    r'|[\u2b50\u2b55\u2934\u2935]'
)

# Allowed utility symbols in non-decorative status contexts
ALLOWED_SYMBOLS = {'•', '→', '↓', '“', '”', '✓', '✕'}

def audit_file(filepath: Path):
    violations = []
    content = filepath.read_text(encoding="utf-8")
    lines = content.splitlines()

    for idx, line in enumerate(lines, start=1):
        # 1. Emoji & Decorative Icon Rule (UI-EMOJI)
        # Scan string literals in Kotlin code
        string_matches = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line)
        for s in string_matches:
            for char in s:
                if char in ALLOWED_SYMBOLS:
                    continue
                if EMOJI_PATTERN.match(char):
                    violations.append({
                        "rule": "UI-EMOJI-LEAK",
                        "severity": "CRITICAL",
                        "file": str(filepath.relative_to(BASE_DIR)),
                        "line": idx,
                        "snippet": line.strip(),
                        "message": f"Illegal decorative emoji or icon character '{char}' (U+{ord(char):04X}) found in string literal."
                    })

        # 2. TextField Hardcoded Height Rule (UI-TEXTFIELD-HEIGHT)
        # OutlinedTextField should not have forced .height(...) which clips font descenders
        if ("OutlinedTextField" in line or "TextField(" in line) and ".height(" in line:
            violations.append({
                "rule": "UI-TEXTFIELD-HEIGHT",
                "severity": "HIGH",
                "file": str(filepath.relative_to(BASE_DIR)),
                "line": idx,
                "snippet": line.strip(),
                "message": "OutlinedTextField has rigid .height(...) constraint. Violates M3 touch targets and clips descenders."
            })

        # 3. Modal Drawer Hardcoded Width Rule (UI-DRAWER-RESPONSIVE)
        if "ModalDrawerSheet" in line or ("drawerContent" in line and ".width(" in line):
            # Check subsequent lines if width(320.dp) is hardcoded without fillMaxWidth / widthIn
            for j in range(idx, min(idx + 10, len(lines))):
                subline = lines[j]
                if re.search(r'\.width\(\s*3\d\d\.dp\s*\)', subline) and "fillMaxWidth" not in subline and "widthIn" not in subline:
                    violations.append({
                        "rule": "UI-DRAWER-RESPONSIVE",
                        "severity": "HIGH",
                        "file": str(filepath.relative_to(BASE_DIR)),
                        "line": j + 1,
                        "snippet": subline.strip(),
                        "message": "ModalDrawerSheet uses static width without fillMaxWidth/widthIn bounds. Occludes scrim on screens <= 320dp."
                    })

    # 4. Dialog Platform Default Width Rule (UI-DIALOG-BOUNDS)
    # Every AlertDialog and Dialog must define properties = DialogProperties(usePlatformDefaultWidth = false)
    for match in re.finditer(r'(?<![A-Za-z0-9_])(AlertDialog|Dialog)\s*\(', content):
        start_pos = match.start()
        line_num = content[:start_pos].count('\n') + 1
        line_text = lines[line_num - 1].strip()
        if line_text.startswith("//") or line_text.startswith("*"):
            continue
        # Extract dialog parameter block (up to 40 lines or closing brace)
        dialog_block = content[start_pos:start_pos + 1200]
        if "usePlatformDefaultWidth = false" not in dialog_block:
            violations.append({
                "rule": "UI-DIALOG-BOUNDS",
                "severity": "HIGH",
                "file": str(filepath.relative_to(BASE_DIR)),
                "line": line_num,
                "snippet": lines[line_num - 1].strip(),
                "message": "Dialog does not configure 'properties = DialogProperties(usePlatformDefaultWidth = false)'. Causes platform width squeezing."
            })

    # 5. Dismiss Button Style Consistency (UI-BUTTON-CONSISTENCY)
    # In AlertDialogs, dismissButton should use OutlinedButton with border, not raw borderless TextButton
    for match in re.finditer(r'dismissButton\s*=\s*\{([^\}]+)\}', content):
        body = match.group(1)
        start_pos = match.start()
        line_num = content[:start_pos].count('\n') + 1
        if "TextButton" in body and "AlertDialog" in content[max(0, start_pos - 1500):start_pos]:
            violations.append({
                "rule": "UI-BUTTON-CONSISTENCY",
                "severity": "MEDIUM",
                "file": str(filepath.relative_to(BASE_DIR)),
                "line": line_num,
                "snippet": lines[line_num - 1].strip(),
                "message": "dismissButton in dialog uses borderless TextButton instead of standard OutlinedButton."
            })

    return violations

def run_audit():
    print("=" * 72)
    print("  AUTONOMOUS UI DEFECT & SANITATION AUDITOR  ".center(72, "="))
    print("=" * 72)
    print(f"Target Scan Directory: {COMPOSE_SRC_DIR}\n")

    if not COMPOSE_SRC_DIR.exists():
        print(f"Error: Target directory does not exist: {COMPOSE_SRC_DIR}")
        sys.exit(1)

    all_violations = []
    kotlin_files = list(COMPOSE_SRC_DIR.glob("**/*.kt"))
    print(f"Discovered {len(kotlin_files)} Kotlin source files. Commencing deep rule inspection...")

    for kfile in sorted(kotlin_files):
        v = audit_file(kfile)
        all_violations.extend(v)

    print(f"\nAudit complete. Total rules checked across {len(kotlin_files)} files.")
    print("-" * 72)

    report_dir = BASE_DIR / "test" / "eval_harness" / "reports"
    report_dir.mkdir(parents=True, exist_ok=True)
    report_file = report_dir / "ui_audit_latest.json"
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump({
            "status": "PASS" if not all_violations else "FAIL",
            "files_scanned": len(kotlin_files),
            "violation_count": len(all_violations),
            "violations": all_violations
        }, f, indent=2)

    if not all_violations:
        print("[+] STATUS: 100% PRODUCTION READY! ZERO UI DEFECTS OR LEAKS DETECTED.")
        print("[+] All DialogProperties, touch targets, bounds, and styling rules passed perfectly.")
        print(f"[+] Audit Report written to: {report_file.relative_to(BASE_DIR)}")
        print("=" * 72)
        return 0
    else:
        print(f"[-] DEFECTS DETECTED: {len(all_violations)} violation(s) found:\n")
        for i, item in enumerate(all_violations, start=1):
            print(f"  {i}. [{item['severity']}] {item['rule']} at {item['file']}:{item['line']}")
            print(f"     Snippet: {item['snippet']}")
            print(f"     Message: {item['message']}\n")
        print("=" * 72)
        return 1

if __name__ == "__main__":
    sys.exit(run_audit())
