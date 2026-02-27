# Portable Testing Scripts Plan

## Goal

Make testing scripts easy to:
1. Add to new Lightning Server + KiteUI projects
2. Update across all projects when improvements are made
3. Customize per-project (ports, paths, etc.)

## Proposed Architecture

### Central Repository

Create `~/Projects/lk-testing-scripts/` (or a git repo) containing:

```
lk-testing-scripts/
├── lib.sh              # Shared functions (stop, wait, etc.)
├── setup.sh            # Main setup script
├── rebuild.sh          # Rebuild and restart
├── stop.sh             # Stop all servers
├── api.sh              # API helper
├── install.sh          # Installer for new projects
└── README.md           # Documentation
```

### Per-Project Structure

Each project gets a minimal `testing/` directory:

```
myproject/
└── testing/
    ├── config.env      # Project-specific: ports, paths, timeouts
    ├── settings.json   # Project-specific: server settings
    └── scripts/        # Symlink or copy of central scripts
        ├── lib.sh → ~/Projects/lk-testing-scripts/lib.sh
        ├── setup.sh → ...
        └── ...
```

Or simpler - thin wrappers:

```
myproject/
└── testing/
    ├── config.env      # Project-specific config
    ├── settings.json   # Server settings
    ├── setup.sh        # Thin wrapper: source central + run
    ├── rebuild.sh      # Thin wrapper
    ├── stop.sh         # Thin wrapper
    └── api.sh          # Thin wrapper
```

## Option A: Symlinks (Simplest)

**Central location:** `~/Projects/lk-testing-scripts/`

**Per-project wrapper (`testing/setup.sh`):**
```bash
#!/bin/bash
# Load project config, then run central script
export LK_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export LK_TESTING_DIR="$LK_PROJECT_DIR/testing"
source "$LK_TESTING_DIR/config.env"
exec ~/Projects/lk-testing-scripts/setup.sh "$@"
```

**Pros:**
- One source of truth
- Update central scripts, all projects get updates immediately
- No sync needed

**Cons:**
- Depends on absolute path `~/Projects/lk-testing-scripts/`
- Scripts not portable if sharing project with others

## Option B: Git Submodule

**Add submodule to each project:**
```bash
git submodule add https://github.com/user/lk-testing-scripts testing/scripts
```

**Per-project wrapper:**
```bash
#!/bin/bash
source "$(dirname "$0")/config.env"
exec "$(dirname "$0")/scripts/setup.sh" "$@"
```

**Update all projects:**
```bash
git submodule update --remote
```

**Pros:**
- Versioned
- Portable (works when cloning repo)
- Standard git workflow

**Cons:**
- Submodules add complexity
- Need to commit submodule updates

## Option C: Install/Update Script (Recommended)

**Central repo:** `~/Projects/lk-testing-scripts/` or git URL

**One-time install in new project:**
```bash
# From project root
curl -sL https://raw.githubusercontent.com/.../install.sh | bash
# Or local:
~/Projects/lk-testing-scripts/install.sh
```

**Creates in project:**
```
testing/
├── config.env          # Generated with defaults, edit as needed
├── settings.json       # Template, edit as needed
├── .scripts-version    # Version marker for updates
├── lib.sh              # Copied from central
├── setup.sh            # Copied from central
├── rebuild.sh          # Copied from central
├── stop.sh             # Copied from central
└── api.sh              # Copied from central
```

**Update existing project:**
```bash
./testing/update.sh
# Or:
~/Projects/lk-testing-scripts/install.sh --update
```

**Pros:**
- Self-contained after install (no external dependencies at runtime)
- Easy to update (`./testing/update.sh`)
- Portable (scripts are copied, not linked)
- Can work from git URL or local path

**Cons:**
- Need to run update manually
- Scripts duplicated in each project

## Recommended: Option C with Local Source

### Implementation

#### 1. Central Scripts Location

Create `~/Projects/lk-testing-scripts/`:

```
lk-testing-scripts/
├── VERSION                 # Version number (e.g., "1.0.0")
├── install.sh              # Install/update to a project
├── scripts/
│   ├── lib.sh              # Shared functions
│   ├── setup.sh            # Main setup
│   ├── rebuild.sh          # Rebuild + restart
│   ├── stop.sh             # Stop servers
│   ├── api.sh              # API calls
│   └── update.sh           # Self-update script
├── templates/
│   ├── config.env.template # Template with placeholders
│   └── settings.json.template
└── README.md
```

#### 2. Install Script (`install.sh`)

```bash
#!/bin/bash
# Install or update lk-testing-scripts in a project
#
# Usage:
#   ~/Projects/lk-testing-scripts/install.sh [project-dir]
#   ~/Projects/lk-testing-scripts/install.sh --update  # Run from project

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION=$(cat "$SCRIPT_DIR/VERSION")

# Determine target project
if [[ "$1" == "--update" ]]; then
    PROJECT_DIR="$(pwd)"
elif [[ -n "$1" ]]; then
    PROJECT_DIR="$(cd "$1" && pwd)"
else
    PROJECT_DIR="$(pwd)"
fi

TESTING_DIR="$PROJECT_DIR/testing"

echo "Installing lk-testing-scripts v$VERSION to $TESTING_DIR"

# Create testing directory
mkdir -p "$TESTING_DIR"

# Copy scripts
cp "$SCRIPT_DIR/scripts/"*.sh "$TESTING_DIR/"
chmod +x "$TESTING_DIR/"*.sh

# Save version and source path for updates
echo "$VERSION" > "$TESTING_DIR/.scripts-version"
echo "$SCRIPT_DIR" > "$TESTING_DIR/.scripts-source"

# Create config.env if it doesn't exist
if [[ ! -f "$TESTING_DIR/config.env" ]]; then
    echo "Creating config.env from template..."
    cp "$SCRIPT_DIR/templates/config.env.template" "$TESTING_DIR/config.env"
    echo "  Edit testing/config.env to customize ports"
fi

# Create settings.json if it doesn't exist
if [[ ! -f "$TESTING_DIR/settings.json" ]]; then
    echo "Creating settings.json from template..."
    cp "$SCRIPT_DIR/templates/settings.json.template" "$TESTING_DIR/settings.json"
    echo "  Edit testing/settings.json for server config"
fi

echo ""
echo "Done! Scripts installed:"
echo "  ./testing/setup.sh      - Start test environment"
echo "  ./testing/rebuild.sh    - Rebuild and restart"
echo "  ./testing/stop.sh       - Stop all servers"
echo "  ./testing/api.sh        - Make API calls"
echo "  ./testing/update.sh     - Update scripts from source"
```

#### 3. Self-Update Script (`scripts/update.sh`)

```bash
#!/bin/bash
# Update testing scripts from source
set -e

TESTING_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ ! -f "$TESTING_DIR/.scripts-source" ]]; then
    echo "ERROR: No source path found. Was this installed with install.sh?"
    exit 1
fi

SOURCE_DIR=$(cat "$TESTING_DIR/.scripts-source")

if [[ ! -d "$SOURCE_DIR" ]]; then
    echo "ERROR: Source not found at $SOURCE_DIR"
    exit 1
fi

echo "Updating from $SOURCE_DIR..."
exec "$SOURCE_DIR/install.sh" --update
```

#### 4. Config Template (`templates/config.env.template`)

```bash
# testing/config.env - Project-specific configuration
# Generated by lk-testing-scripts

# Ports (change to avoid conflicts with other projects)
BACKEND_PORT=8081
FRONTEND_PORT=8941

# Timeouts (seconds)
BACKEND_STARTUP_TIMEOUT=90
FRONTEND_STARTUP_TIMEOUT=120

# Gradle tasks (override if different)
BACKEND_RUN_TASK=":server:run"
FRONTEND_RUN_TASK=":apps:jsViteDev"
FRONTEND_BUILD_TASK=":apps:jsBrowserDevelopmentVitePrepare"
BACKEND_BUILD_TASK=":server:compileKotlin"

# Settings file (relative to project root)
SETTINGS_FILE="testing/settings.json"
```

### Workflow

**New project:**
```bash
cd ~/Projects/my-new-project
~/Projects/lk-testing-scripts/install.sh
# Edit testing/config.env if needed
# Edit testing/settings.json for server config
```

**Update existing project:**
```bash
cd ~/Projects/my-project
./testing/update.sh
```

**Update all projects at once:**
```bash
for dir in ~/Projects/*/testing; do
    if [[ -f "$dir/.scripts-source" ]]; then
        echo "Updating $(dirname $dir)..."
        (cd "$(dirname $dir)" && ./testing/update.sh)
    fi
done
```

### Files to Add to .gitignore

```gitignore
# Testing artifacts (but keep scripts)
testing/.backend.log
testing/.frontend.log
testing/.admin-token
testing/.scripts-source
testing/.scripts-version
```

### Migration from Current Setup

1. Create `~/Projects/lk-testing-scripts/` with generalized scripts
2. In open-ticketer, run install to overwrite (preserving config.env)
3. Verify everything works
4. Apply to other projects

## Summary

| Approach | Best For |
|----------|----------|
| **Symlinks** | Single developer, all projects on same machine |
| **Git Submodule** | Team projects, need versioning |
| **Install Script** | Flexibility, self-contained projects, easy updates |

**Recommendation:** Option C (Install Script) because:
- Projects are self-contained (can share/clone without dependencies)
- Easy one-command update
- Config stays project-specific
- Works with local path now, could add git URL later
