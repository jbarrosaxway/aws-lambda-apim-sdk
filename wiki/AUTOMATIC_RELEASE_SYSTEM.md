# Automatic Release System

This document explains the automatic release system implemented in the project.

## Overview

The system automatically analyzes changes and determines if a release is needed, creating tags and releases automatically when appropriate.

## How It Works

### 🔍 **Intelligent Change Analysis**

The system checks if the changes are relevant to trigger a release:

#### **Files that DO NOT trigger a release:**
- 📚 Documentation: `README.md`, `docs/`, `*.md`
- 🔧 Configuration: `.gitignore`, `.github/`, `LICENSE`
- 📝 Temporary: `*.txt`, `*.log`, `*.bak`, `*.backup`
- 🛠️ IDE: `*.iml`, `*.ipr`, `*.iws`, `.idea/`, `.vscode/`
- 📦 Build: `node_modules/`, `__pycache__/`, `*.pyc`
- 📄 Documents: `*.docx`, `*.doc`, `*.pdf`
- 🔧 Installers: `*.run`, `license.txt`

#### **Files that DO trigger a release:**
- 💻 Source code: `src/`, `*.java`, `*.groovy`
- 🔧 Build: `build.gradle`, `gradle/`, `gradlew`, `settings.gradle`
- 🐳 Docker: `Dockerfile`, `docker-compose`
- 📋 Configuration: `*.yaml`, `*.yml`, `*.xml`, `*.properties`
- 🔧 Scripts: `*.sh`, `*.ps1`, `*.cmd`, `*.bat`

### 🔄 **Automatic Flow**

```
1. Push to master
   ↓
2. Analyze relevant changes
   ↓
3. If relevant changes → Semantic versioning
   ↓
4. If not a PR → Automatic tag creation
   ↓
5. Tag push → Triggers release workflow
   ↓
6. Release created automatically
```

## System Scripts

### `scripts/check-release-needed.sh`
- **Function:** Analyzes changes and determines if a release is needed
- **Input:** List of modified files
- **Output:** `.release_check` file with information

### `scripts/version-bump.sh`
- **Function:** Performs semantic versioning
- **Input:** Detected changes
- **Output:** New calculated version

## Updated Workflow

### **Trigger**
- Push to `master` or `main`
- Pull Requests
- Manual execution

### **Steps**
1. **Checkout** with full history
2. **Release Analysis** - Checks if needed
3. **Build** - If needed, runs build
4. **Tag Creation** - Automatically (only on direct push)
5. **PR Comment** - Detailed information

## Scenario Examples

### ✅ **Release Needed**
```bash
# Change in Java file
git commit -m "feat: add new feature" src/main/java/MyClass.java
git push origin master
# → Automatic release created
```

### ❌ **Release Not Needed**
```bash
# Change only in documentation
git commit -m "docs: update README" README.md
git push origin master
# → No release created
```

### 🔄 **Pull Request**
```bash
# Any change in PR
git commit -m "fix: bug fix" src/main/java/BugFix.java
git push origin feature/bugfix
# → Analysis done, but no release (waits for merge)
```

## Configuration

### **Configuration Files**
- **`.release_check`** - Created during analysis
- **`.version_info`** - Created during versioning

### **Environment Variables**
- `GITHUB_EVENT_NAME` - Event type
- `GITHUB_BASE_REF` - Base branch (PRs)
- `GITHUB_HEAD_REF` - Head branch (PRs)

## Benefits

### ✅ **Full Automation**
- Intelligent change analysis
- Automatic semantic versioning
- Automatic tag creation
- Automatic release

### ✅ **Smart Filters**
- Avoids unnecessary releases
- Focuses only on relevant changes
- Saves CI/CD resources

### ✅ **Transparency**
- Detailed information in PRs
- Clear decision logs
- Full traceability

## Troubleshooting

### **Problem: "Release not created"**
**Check:**
1. If the changes are relevant
2. If it is not a PR
3. If on the master branch
4. Logs from the "Check if Release is Needed" step

### **Problem: "Tag already exists"**
**Solution:**
```bash
# Remove local tag
git tag -d v1.0.1

# Remove remote tag
git push origin --delete v1.0.1

# Recreate (the system will do it automatically)
```

### **Problem: "Build failed"**
**Check:**
1. Workflow logs
2. If the Docker image is available
3. If secrets are configured

## Customization

### **Add New Patterns**
Edit `scripts/check-release-needed.sh`:

```bash
# Add file that does NOT trigger release
NON_RELEASE_FILES+=("new-pattern")

# Add file that DOES trigger release
RELEASE_FILES+=("new-pattern")
```

### **Modify Extension Logic**
```bash
case "$ext" in
    # Add new extension
    newext)
        return 0  # true - should trigger release
        ;;
esac
```

## Monitoring

### **Important Logs**
- `[RELEASE-CHECK]` - Change analysis
- `[VERSION]` - Semantic versioning
- `✅ Relevant for release` - File detected
- `⏭️ Not relevant for release` - File ignored

### **Status Files**
- `.release_check` - Analysis result
- `.version_info` - Version information

## Next Steps

1. **Test** the system with different types of changes
2. **Monitor** logs and results
3. **Adjust** patterns as needed
4. **Document** experiences and improvements

## Related Links

- **[📊 Semantic Versioning](SEMANTIC_VERSIONING.md)** - Versioning details
- **[🗳️ Release Guide](RELEASE_GUIDE.md)** - How to create releases manually
- **[🔧 Dynamic Configuration](DYNAMIC_CONFIGURATION.md)** - Project configuration 