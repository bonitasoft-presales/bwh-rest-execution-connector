#!/usr/bin/env bash
set -euo pipefail

# ─── Configuration ───────────────────────────────────────────────────────────
CONNECTOR_DIR="C:/JavaProjects/process-builder/Connectors/pb-execution-connector"
LIBRARY_WORKFLOWS="C:/JavaProjects/process-builder/Custom Library/process-builder-extension-library/.github/workflows"
ORG_REPO="bonitasoft-presales/bwh-rest-execution-connector"
REMOTE_NAME="presales"
REMOTE_URL="https://github.com/${ORG_REPO}.git"

cd "$CONNECTOR_DIR"
echo "==> Working in: $(pwd)"

# ─── 1. Copy CI/CD workflows ────────────────────────────────────────────────
echo "==> Copying GitHub Actions workflows..."
mkdir -p .github/workflows
cp "$LIBRARY_WORKFLOWS"/*.yml .github/workflows/
echo "    Copied: $(ls .github/workflows/*.yml | xargs -I{} basename {})"

# ─── 2. Update pom.xml version (remove SNAPSHOT) ────────────────────────────
echo "==> Updating pom.xml version to 1.0.0..."
sed -i 's|<version>1.0.0-SNAPSHOT</version>|<version>1.0.0</version>|' pom.xml

# Verify the change
if grep -q '<version>1.0.0</version>' pom.xml; then
    echo "    pom.xml version updated successfully"
else
    echo "    ERROR: pom.xml version update failed" && exit 1
fi

# ─── 3. Create GitHub repo (or skip if exists) ──────────────────────────────
echo "==> Creating repo ${ORG_REPO} on GitHub..."
if gh repo view "$ORG_REPO" &>/dev/null; then
    echo "    Repo already exists, skipping creation"
else
    gh repo create "$ORG_REPO" --public --description "BWH REST Execution Connector - Multi-connector for REST API calls with auth management"
    echo "    Repo created"
fi

# ─── 4. Configure remote ────────────────────────────────────────────────────
echo "==> Configuring remote '${REMOTE_NAME}'..."
if git remote get-url "$REMOTE_NAME" &>/dev/null; then
    git remote set-url "$REMOTE_NAME" "$REMOTE_URL"
    echo "    Remote '${REMOTE_NAME}' updated"
else
    git remote add "$REMOTE_NAME" "$REMOTE_URL"
    echo "    Remote '${REMOTE_NAME}' added"
fi

# ─── 5. Commit and push ─────────────────────────────────────────────────────
echo "==> Staging all changes..."
git add .

echo "==> Committing..."
git commit -m "$(cat <<'EOF'
chore: migrate to presales and release 1.0.0

- Copy CI/CD workflows from shared library
- Remove SNAPSHOT from version
- Target org: bonitasoft-presales
EOF
)"

echo "==> Pushing to ${REMOTE_NAME}/main..."
git push "$REMOTE_NAME" main

# ─── 6. Summary ─────────────────────────────────────────────────────────────
echo ""
echo "=============================================="
echo "  Migration complete!"
echo "  New repo: https://github.com/${ORG_REPO}"
echo "  Version:  1.0.0"
echo "=============================================="
echo ""
echo "  NEXT STEP: Archive the old repo:"
echo "  gh repo archive bonitasoft-ps/bwh-rest-execution-connector --yes"
echo "=============================================="
