# Automatic Semantic Versioning

This project implements automatic semantic versioning that analyzes changes and increments the version appropriately.

## How It Works

### Change Analysis

The system automatically analyzes:

1. **Modified files** - via `git diff`
2. **Change content** - looking for specific patterns
3. **Commit type** - based on commit conventions

### Version Types

#### 🔴 MAJOR (X.0.0)
- **When:** Breaking changes
- **Detected by:**
  - Keywords: `BREAKING CHANGE`, `breaking change`, `!:`, `feat!`, `fix!`
  - Modified files: `build.gradle`, `.java`, `.groovy`

#### 🟡 MINOR (0.X.0)
- **When:** New features (backward compatible)
- **Detected by:**
  - Keywords: `feat:`, `feature:`, `new:`, `add:`
  - Modified files: `.java`, `.groovy`, `.yaml`

#### 🟢 PATCH (0.0.X)
- **When:** Bug fixes and improvements
- **Detected by:**
  - Keywords: `fix:`, `bugfix:`, `patch:`, `docs:`, `style:`, `refactor:`, `perf:`, `test:`, `chore:`
  - Modified files: `.java`, `.groovy`, `.yaml`, `.md`, `.txt`

## Commit Conventions

### For MAJOR (Breaking Changes)
```bash
git commit -m "feat!: new feature that breaks compatibility"
git commit -m "fix!: breaking fix"
git commit -m "feat: new feature

BREAKING CHANGE: this change breaks compatibility"
```

### For MINOR (New Features)
```bash
git commit -m "feat: add new feature"
git commit -m "feature: implement new filter"
git commit -m "add: support for AWS Lambda"
```

### For PATCH (Fixes)
```bash
git commit -m "fix: fix authentication bug"
git commit -m "docs: update documentation"
git commit -m "style: format code"
git commit -m "refactor: improve performance"
git commit -m "test: add tests"
git commit -m "chore: update dependencies"
```

## GitHub Actions Workflow

### Pull Requests
- ✅ Analyzes changes
- ✅ Calculates new version
- ✅ Shows information in PR comment
- ❌ **Does NOT commit automatically**

### Direct Push to Master
- ✅ Analyzes changes
- ✅ Calculates new version
- ✅ Updates `build.gradle`
- ✅ Commits new version
- ✅ Pushes to repository

## System Files

### Main Script
- **`scripts/version-bump.sh`** - Script that analyzes changes and updates version

### Workflow
- **`.github/workflows/build-jar.yml`** - Workflow that runs versioning

### Temporary File
- **`.version_info`** - Created during build with version information

## Example Output

```
[VERSION] Analyzing changes in direct push...
[VERSION] Getting modified files...
[VERSION] Modified files:
src/main/java/com/axway/aws/lambda/AWSLambdaProcessor.java
[VERSION] 🟡 MINOR changes detected (new features)
[VERSION] Current version: 1.0.1
[VERSION] New version calculated: 1.1.0 (MINOR)
[VERSION] Updating build.gradle...
[VERSION] ✅ Version updated successfully: 1.0.1 → 1.1.0
[VERSION] 📋 Change summary:
   Version type: MINOR
   Previous version: 1.0.1
   New version: 1.1.0
   Modified files: 1
[VERSION] 🚀 Direct push detected - preparing commit for new version
[VERSION] ✅ Semantic versioning completed!
```

## Configuration

### Environment Variables
The system uses the following GitHub Actions variables:
- `GITHUB_EVENT_NAME` - Event type (push, pull_request)
- `GITHUB_BASE_REF` - Base branch (in PRs)
- `GITHUB_HEAD_REF` - Head branch (in PRs)

### Permissions
The workflow needs permissions for:
- `contents: write` - To commit
- `pull-requests: write` - To comment on PRs

## Troubleshooting

### Problem: "Could not get current version"
**Solution:** Check if `build.gradle` has the line `version 'X.Y.Z'` in the correct format.

### Problem: "Failed to update version"
**Solution:** Check if `build.gradle` has write permissions and is in the expected format.

### Problem: "No modified files found"
**Solution:** This is normal in some cases. The system assumes PATCH by default.

## Contribution

To contribute improvements to the versioning system:

1. Modify the script `scripts/version-bump.sh`
2. Test locally: `./scripts/version-bump.sh`
3. Commit following conventions
4. Open a PR

## Version History

- **1.0.1** - Initial implementation of semantic versioning
- **1.1.0** - Improvements in change analysis and documentation 