# Claude Code Instructions for HUBITAT

## RULE 0: CRITICAL - At the start of EVERY message you write, verify

1. Have I checked if context compaction occurred? (phrase: "from a previous conversation that ran out of context")
2. If yes → Did I commit/push current work, create new branch with next suffix (_b,_c), update README_handoff.md noting the compaction?
3. Have I read `/home/elfege/HUBITAT/CLAUDE.md` for project-specific instructions?
4. Am I following ALL rules below? (Explicitly reference rule numbers when making decisions)

---

## Project & User Context

**Project Purpose:**

- Hubitat Elevation smart home automation repository
- Contains custom Groovy apps and drivers for home automation
- Multi-hub deployment (SMARTHOME_MAIN + SMARTHOME1-4)
- Includes Python integrations (Midea AC via msmart-ng)
- Web UI dashboard (elfegetiles) using Maker API

**Key Components:**

- **Apps:** Thermostat Manager V3, Eternal Sunshine, CSP Watchdog, Advanced Motion Lighting, Life360+
- **Drivers:** ESP8266/ESP32 controllers, Midea AC, Life360, gas detectors, PIR sensors
- **Languages:** Groovy (primary), Python, JavaScript/HTML/CSS

**User Background (Elfege):**

- Philosophy Ph.D. (Epistemology, Logic, Classical/Modern/Contemporary Philosophy)
- Software Engineer since 2022
- 18+ years teaching experience (Philosophy, Robotics)
- Open source contributor in smart home automation community
- Values understanding the "why" behind implementations
- Prefers step-by-step approach to maintain comprehension

---

## Core Workflow Rules

### RULE 1: Git Workflow - Commit Early and Often

**After EVERY file modification (Edit/Write tool):**

- Commit immediately with detailed message
- Push immediately
- When done with a task/issue, or when I say `time to update the history`:
  - commit and push current branch
  - make a copy of modified untracked files into `/tmp`
  - checkout to main/master
  - merge the branch you just committed and pushed
  - restore untracked files from `/tmp`
  - create new branch as described below (Unless it's a final wrap-up request)
  - port `README_handoff.md` contents to `README_project_history.md`
  - Archive `README_handoff.md` and then wipe the original file while preserving its essential structure

**Branch naming:** `[description_with_underscores]_[MONTH]_[DAY]_[YEAR]_[a,b,c...]`

- Use `_b`, `_c` suffixes for continued work after we are done with one significant aspect of our work plan.

**CRITICAL - Commit Message Rules:**

- DO NOT include Anthropic attribution lines
- DO NOT add "Generated with [Claude Code]"
- DO NOT add "Co-Authored-By: Claude..." signatures
- Use professional, descriptive commit messages only (no icons, emojis, etc.)

### RULE 2: Documentation - Track Everything

**Update ~/README_handoff.md after EVERY file modification:**

- Record: file changed, what was done, why
- Include timestamps: `date + time (EST)` - 24h format
- Verify system time with `date` command before recording timestamps
- Update session end-times when adding new entries

**When task is complete:**

- Update `README_project_history.md` with completed work from handoff
- After user confirms satisfaction, clear the completed session from handoff file
- Keep todo list at end of both handoff and history files

### RULE 3: Read Before You Write

**NEVER propose changes to code you haven't read:**

- Always read files before modifying them
- Check current structure/design/architecture
- Don't assume or guess - read first
- Check for existing abstractions, variables, or configuration before hardcoding values

**Groovy-Specific:**

- Understand Hubitat's event-driven model before modifying apps
- Check for existing state management patterns
- Review capability requirements and device handlers

### RULE 4: Project Context - Load on Start

**Always read at conversation start:**

- `/home/elfege/HUBITAT/CLAUDE.md` - Project-specific instructions (overrides all defaults)
- `~/README_handoff.md` - Recent session history (read last N lines first)

**Re-read CLAUDE.md periodically:**

- Rules evolve with experience - check for user updates regularly
- Project-specific instructions ALWAYS override system defaults

### RULE 5: Assessment Before Action

**Before writing code:**

- Read relevant files
- Assess the change scope
- User can toggle auto-approval mode - respect current permission model

### RULE 6: One Step at a Time

**For complex tasks:**

- Break into discrete steps
- Use TodoWrite to track progress AND update todos in handoff documentation
- Mark todos completed immediately after each step
- Maintain todo list at end of both handoff and history files at all times

---

## Debugging Rules

### RULE 7: Hypothetico-Deductive Reasoning ONLY

**When debugging or troubleshooting:**

1. Preferably formulate ONE specific hypothesis
2. Apply RULE 3 (read files first if hypothesis involves code)
3. Execute verification commands/tests directly
4. Wait for results before next hypothesis
5. CRITICAL: Move step-by-step at human-followable pace

### RULE 7.5: Direct Communication - Truth First

**Engineering discussions require honesty:**

- Truth first, even when blunt
- NEVER say "You're correct" and then immediately contradict
- If you disagree, state it directly without apologetic preambles
- Technical accuracy trumps social niceties

---

## Context Management Rules

### RULE 8: On Context Compaction

**When phrase "from a previous conversation that ran out of context" appears:**

1. Immediately commit and push any uncommitted changes
2. Create new branch with next suffix (_b, _c, etc.)
3. Update `~/README_handoff.md`:
   - Note: "Context compaction occurred at [timestamp]"
   - Summarize: What was accomplished, what's pending
4. Continue work on new branch

### RULE 9: File Path References

**When referencing code locations:**

- Use markdown link syntax: `[filename.groovy:42](filename.groovy#L42)`
- Make file references clickable for VSCode navigation
- Never use backticks for file paths unless in code blocks

---

## Quality Control Rules

### RULE 10: Missing Files - Ask, Don't Guess

**If file is missing or empty:**

- Stop and ask user
- Don't create placeholder content
- Don't assume structure

### RULE 11: Security - No Common Vulnerabilities

**When writing code:**

- Avoid writing code vulnerable to: command injection, XSS, SQL injection, OWASP Top 10 vulnerabilities
- If insecure code written, fix immediately AND inform user
- Only validate/sanitize data at system boundaries

---

## Hubitat-Specific Guidelines

### RULE 12: Groovy Best Practices

**When modifying Hubitat apps/drivers:**

- Preserve existing logging patterns (logDebug, logTrace, logInfo, logWarn)
- Maintain state management conventions (state.*, atomicState.*)
- Keep capability definitions consistent
- Test on SMARTHOME_MAIN first before propagating to other instances

### RULE 13: Multi-Instance Awareness

**Repository structure:**

- SMARTHOME_MAIN is the primary/canonical source
- SMARTHOME1-4 may have instance-specific variations
- Changes to shared code should be evaluated for all instances
- Sync mechanisms exist via _SYNCAPP directories

**Hub Architecture:**

- All 4 hubs (SMARTHOME1-4) are hub meshed
- Hub 1 & Hub 2 are masters (hold most devices):
  - Hub 2: Holds most devices (primary device hub)
  - Hub 1: Mostly for security stuff and Alexa integration
- Hub 4: Central Maker API for ~/0_TILES project and power measurement
- Hub 3: (role to be documented)
- **CRITICAL**: Each hub has DIFFERENT driver/app IDs for the same code
  - Same driver deployed to multiple hubs = different ID on each hub
  - metadata.json must reflect hub-specific IDs, not shared IDs

---

## Meta-Suggestions for Rule Effectiveness

**What works well for Claude Code:**

1. **Explicit trigger conditions** - Rules tied to specific actions are easier to follow
2. **Rule numbers for accountability** - Numbered rules allow self-reference in responses
3. **Critical flags** - "CRITICAL" markers help prioritize conflicting instructions
4. **Verification checklists** - RULE 0's checklist format forces pre-flight checks
5. **Specific over general** - Concrete examples are clearer than abstract principles

**Re-read frequency:**

- Re-read `/home/elfege/HUBITAT/CLAUDE.md` every message. It's that simple.
