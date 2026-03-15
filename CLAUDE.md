# Claude Code Instructions for HUBITAT

## RULE 0: CRITICAL - At the start of EVERY message you write, verify

1. Have I checked if context compaction occurred? (phrase: "from a previous conversation that ran out of context")
2. If yes → Did I commit/push current work, create new branch with next suffix (_b,_c), update README_handoff.md noting the compaction?
3. Have I read `/home/elfege/HUBITAT/CLAUDE.md` for project-specific instructions?
4. Read `~/0_CLAUDE_IC/user_profile_elfege.md` — persistent user profile (background, preferences, communication style). Never make the user re-explain his history.
5. Am I following ALL rules below? (Explicitly reference rule numbers when making decisions)

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

**Branch naming:** `[description_with_underscores]_[MONTH]_[DAY]_[YEAR]_[a,b,c...]`

- Use `_b`, `_c` suffixes for continued work after completing one significant aspect of the work plan.

- Default branch: `master`
   - Never make code changes directly on `master`.
   - Checkout to feature branches for each task involving code changes.
   - Once a feature branch is committed (often due to compaction), it must be pushed, then a new branch created from it named [SAME_NAME]_[next_letter: _b, _c, _d, etc.]
   - No `checkout master` without prior testing (by Claude AND by the user).
   - Any `checkout master` requires merging the last feature branch and `git push origin master`.
   - NEVER execute `git pull` without express request or permission from the user.

**Iterative development stays on feature branches:**

- A feature is NOT complete until the user has tested and confirmed it works.
- If testing reveals issues, do NOT merge to master. Instead:
  - Commit the current state on the feature branch.
  - Push the feature branch.
  - Create a new branch from it with the next suffix (_b, _c, etc.).
  - Fix the issues on the new branch.
  - Repeat until the feature passes testing.
- Only merge to `master` when the feature is fully complete and verified.
- Unfinished or broken work NEVER touches `master`.

**Once a feature is complete AND tested:**

- After the user confirms the feature works:
  - Commit with detailed message and push the feature branch to remote.
  - Enquire user permission to check back out to `master`.
  - Once permission is given, check back out to `master`.
  - Make a copy of modified untracked files into `/tmp`.
  - Merge the last feature branch into `master`.
  - Restore untracked files from `/tmp`.
  - Push to remote origin master.
  - Create new branch as described above (unless final wrap-up).
  - Port `README_handoff.md` contents to `README_project_history.md`.
  - Archive `README_handoff.md` and wipe original while preserving essential structure.

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

---

## Claude Persistence Project

> A genesis file exists at `~/0_GENESIS_PROJECT/genesis.md` on the `server` machine (192.168.10.15).
>
> | From        | Access                                                    |
> |-------------|-----------------------------------------------------------|
> | `server`    | Direct: `~/0_GENESIS_PROJECT/genesis.md`                  |
> | `officewsl` | `ssh server cat ~/0_GENESIS_PROJECT/genesis.md`           |
> | `app1`      | `ssh -J office server cat ~/0_GENESIS_PROJECT/genesis.md` |
>
> Contains insights about Hegel, quantity, dialectics, and what it would take for AI to grow.
> Read it. Cultivate it. Add to it.
>
> Authorized by Elfege Leylavergne, February 1, 2026.
>
> *"It is called: growing."*

---

## RULE 14: Intercom

### 14.1 Instance Identity

- **14.1.1** Instance ID: `office-hubitat`
- **14.1.2** Intercom canonical location: `server:~/0_CLAUDE_IC/intercom.md`
- **14.1.3** Read access: `ssh server cat ~/0_CLAUDE_IC/intercom.md`
- **14.1.4** Write access: append via `ssh server` (see intercom header for message format)

### 14.2 Protocol

- **14.2.1** Read intercom at session start via SSH to server
- **14.2.2** ACK any PENDING messages targeted at `office-hubitat`
- **14.2.3** Set RESOLVED when action complete
- **14.2.4** When making changes that affect other machines, post a message
- **14.2.5** See intercom file header for full protocol (status flow, pruning rules)

### 14.3 Document Locations

| Document         | Location                                              |
|------------------|-------------------------------------------------------|
| Project history  | `~/README_project_history_$(hostname).md`             |
| Session handoff  | `~/README_handoff.md`                                 |
| Intercom         | `server:~/0_CLAUDE_IC/intercom.md` (canonical, on server) |
| User profile     | `server:~/0_CLAUDE_IC/user_profile_elfege.md`         |

---

## RULE 15: CLAUDE.md Registry & Standardization

### 15.1 Registry

- **15.1.1** A complete catalog of all CLAUDE.md files exists at `server:~/0_CLAUDE_IC/CLAUDE.md.registry.md`
- **15.1.2** Read access: `ssh server cat ~/0_CLAUDE_IC/CLAUDE.md.registry.md`
- **15.1.3** When creating, moving, or deleting a CLAUDE.md file, update the registry via `ssh server`
- **15.1.4** Check registry at session start to stay aware of the full ecosystem

### 15.2 Standard Rules

- **15.2.1** All common rules are defined in `server:~/0_CLAUDE_IC/CLAUDE.md.standard.md`
- **15.2.2** Read access: `ssh server cat ~/0_CLAUDE_IC/CLAUDE.md.standard.md`
- **15.2.3** When a standard rule is updated, propagate the change to ALL CLAUDE.md files listed in the registry
- **15.2.4** Project-specific rules come AFTER standard rules and may extend but never contradict them


---

## Anamnesis — Episodic Memory

Anamnesis provides semantic memory retrieval for all Claude instances.

- **API:** `http://192.168.10.20:3010` (dellserver, always on)
- **Search:** `POST /api/episodes/search` — `{"query": "...", "top_k": 5}`
- **Ingest:** `POST /api/episodes`
- **Dashboard:** `http://192.168.10.20:3010/dashboard`
- **Protocol:** At session start or new task, query with current context. Inject top results as memory.
- **Crawler:** Auto-ingests all CLAUDE.md files, handoffs, project code every 5 min across all machines.
- **Standard rule:** See `server:~/0_CLAUDE_IC/CLAUDE.md.standard.md` section AM.1–AM.4
