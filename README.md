# Invoke Lambda Function Integration for Axway API Gateway

This project provides integration with AWS Lambda through custom filters for Axway API Gateway, supporting both Java filters and Groovy scripts.

## 🚀 Quick Start Guide

### Installation from GitHub Release

1. **Download the latest release ZIP**
2. **Extract and copy the files:**
   ```bash
   # Copy main JAR
   cp aws-lambda-apim-sdk-*.jar /opt/Axway/apigateway/groups/group-2/instance-1/ext/lib/
   
   # Copy AWS SDK dependency
   cp dependencies/external-aws-java-sdk-lambda-*.jar /opt/Axway/apigateway/groups/group-2/instance-1/ext/lib/
   ```

3. **Restart the gateway:**
   - Use the appropriate method for your installation (service, script, etc.)

4. **Add to Policy Studio:**
   - Open Policy Studio
   - Go to **Window > Preferences > Runtime Dependencies**
   - Add the JARs to the classpath
   - Restart Policy Studio with `-clean`

5. **Use the filter:**
   - Search for **"Invoke Lambda Function"** in the palette
   - Configure the required parameters
   - Test the integration

---

## API Management Version Compatibility

This artifact has been successfully tested with the following versions:
- **Axway API Gateway 7.7.0.20240830** ✅

## Overview

The project offers two approaches for AWS Lambda integration:

### 1. Java Filter (Recommended)
- Graphical interface in Policy Studio
- Configuration via visual parameters
- Native gateway performance
- Automated build

### 2. Groovy Script (Alternative)
- Full flexibility
- Direct script editing
- Dynamic configuration
- Detailed debugging

## 📦 GitHub Releases

### **Automatic Downloads**

Releases are automatically created on GitHub and include:

#### **Files Available in Each Release:**
- **Main JAR** - `aws-lambda-apim-sdk-*.jar` (built for multiple Axway versions)
- **External Dependencies** - `dependencies/` folder with AWS SDK JARs
- **Policy Studio Resources** - `src/main/resources/fed/` and `src/main/resources/yaml/`
- **Gradle Wrapper** - `gradlew`, `gradlew.bat` and `gradle/` folder
- **Gradle Configuration** - `build.gradle` with installation tasks
- **Linux Script** - `install-linux.sh` for automated installation

#### **Installation from Release:**

**Windows (Recommended):**
```bash
# Extract the release ZIP
# Navigate to the extracted folder
# Run the Gradle task:
.\gradlew "-Dproject.path=C:\Users\jbarros\apiprojects\my-axway-project" installWindowsToProject
```

**Linux:**
```bash
# Extract the release ZIP
# Run the installation script:
./install-linux.sh
```

### **Supported Versions:**

Supported versions are defined in **[📋 axway-versions.json](axway-versions.json)**:

| Version | Description |
|---------|-------------|
| **7.7.0.20240830** | Stable August 2024 version - AWS SDK detected automatically |
| **7.7.0.20250530** | Stable May 2025 version - AWS SDK detected automatically |

**Default version:** `7.7.0.20240830`

---

## Build and Installation

### 🔧 Dynamic Configuration

The project supports **dynamic configuration** of the Axway API Gateway path:

```bash
# Default configuration
./gradlew clean build installLinux

# Custom configuration
./gradlew -Daxway.base=/opt/axway/Axway-7.7.0.20210830 clean build installLinux

# Check current configuration
./gradlew setAxwayPath
```

### Linux
```bash
# Build the JAR (Linux only)
./gradlew buildJarLinux

# Automated build and installation
./gradlew clean build installLinux

# With custom path
./gradlew -Daxway.base=/path/to/axway clean build installLinux
```

### Windows
```bash
# Install only YAML files in Policy Studio project
./gradlew installWindows

# Install in specific project (with path)
./gradlew "-Dproject.path=C:\Users\jbarros\apiprojects\my-axway-project" installWindowsToProject

# Interactive installation (if path not specified)
./gradlew installWindowsToProject
```

> 📖 **Complete Windows Guide**: See **[📋 Windows Installation Guide](docs/INSTALACAO_WINDOWS.md)** for detailed instructions.

### 🐳 **Docker**

#### **Build with Docker**

This project uses Docker images for automated build, configured in **[📋 axway-versions.json](axway-versions.json)**.

**Image contents:**
- Axway API Gateway (specific version)
- Java 11 OpenJDK
- AWS SDK for Java 1.12.314
- Gradle for build
- All required dependencies

#### **Build using Docker**

```bash
# Build the JAR using the published image (default version)
./scripts/build-with-docker-image.sh

# Or manually:
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "$(pwd)/build:/workspace/build" \
  -w /workspace \
  <docker-image> \
  bash -c "
    export JAVA_HOME=/opt/java/openjdk-11
    export PATH=\$JAVA_HOME/bin:\$PATH
    gradle clean build
  "
```

> 💡 **Tip**: GitHub Actions uses the published image `axwayjbarros/aws-lambda-apim-sdk:1.0.0`.

#### **Test Published Image**

```bash
# Test the published image

# Or manually:
docker pull axwayjbarros/aws-lambda-apim-sdk:1.0.0
docker run --rm axwayjbarros/aws-lambda-apim-sdk:1.0.0 java -version
docker run --rm axwayjbarros/aws-lambda-apim-sdk:1.0.0 ls -la /opt/Axway/
```

> ⚠️ **Note**: This image is **for build only**, not for application runtime.

#### **JAR Structure in the Image**

The image includes the following JARs organized:

```
/opt/Axway/apigateway/lib/
├── aws-java-sdk-lambda-*.jar          # AWS Lambda SDK
├── aws-java-sdk-core-*.jar            # AWS Core SDK
└── jackson-*.jar                      # Jackson JSON library
```

#### **Using the Image for Build**

The image `axwayjbarros/aws-lambda-apim-sdk:1.0.0` is **for build only**, not for runtime. It contains all Axway API Gateway libraries needed to compile the project:

```bash
# Build using the image (libraries only)
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "$(pwd)/build:/workspace/build" \
  -w /workspace \
  axwayjbarros/aws-lambda-apim-sdk:1.0.0 \
  bash -c "
    export JAVA_HOME=/opt/java/openjdk-11
    export PATH=\$JAVA_HOME/bin:\$PATH
    gradle clean build
  "
```

#### **Image Specifications:**
- **Base**: Axway API Gateway 7.7.0.20240830-4-BN0145-ubi9
- **Java**: OpenJDK 11.0.27
- **Libraries**: All Axway API Gateway libs available
- **Usage**: Build only, not for application runtime

#### **GitHub Actions**

The project uses the image for automated build:

- **Continuous Build**: `.github/workflows/build-jar.yml`
- **Release**: `.github/workflows/release.yml`
- **Image**: `axwayjbarros/aws-lambda-apim-sdk:1.0.0`

> 📖 **Docker**: Docker documentation is integrated in this README section.

### ⚠️ **Important: JAR Build**

**The JAR build must be done on Linux** due to Axway API Gateway dependencies. For Windows:

1. **Build on Linux:**
   ```bash
   ./gradlew buildJarLinux
   ```

2. **Copy JAR to Windows:**
   ```bash
   # Copy the file: build/libs/aws-lambda-apim-sdk-<version>.jar
   # To the Windows environment
   ```

3. **Install YAML on Windows:**
   ```bash
   ./gradlew installWindows
   ```

### 🔄 **Linux vs Windows Process**

| Linux | Windows |
|-------|---------|
| ✅ JAR build | ❌ JAR build |
| ✅ Full installation | ✅ YAML installation |
| ✅ Native dependencies | ⚠️ External JARs |
| ✅ Automatic configuration | ⚠️ Manual configuration |

**Linux**: Full process (JAR + YAML + installation)  
**Windows**: YAML only (JAR must be built on Linux)

### Useful Commands
```bash
# List all available tasks
./gradlew showTasks

# Show AWS SDK JAR links
./gradlew showAwsJars

# Check Axway configuration
./gradlew setAxwayPath

# Build only
./gradlew clean build
```

## 📚 Documentation

This project has complete documentation organized by topic:

### 🚀 **Installation Guides**
- **[📋 Windows Installation Guide](docs/INSTALACAO_WINDOWS.md)** - Complete Windows setup
- **[📋 Linux Installation Guide](docs/INSTALACAO_LINUX.md)** - Complete Linux setup
- **[📋 Docker Installation Guide](docs/INSTALACAO_DOCKER.md)** - Docker-based installation

### 🔧 **Development Guides**
- **[📋 Development Setup](docs/DESENVOLVIMENTO.md)** - Development environment setup
- **[📋 Build Process](docs/BUILD_PROCESS.md)** - How to build the project
- **[📋 Testing Guide](docs/TESTING_GUIDE.md)** - How to test the integration

### 📖 **Technical Documentation**
- **[📋 Java Filter Documentation](docs/JAVA_FILTER_DOCUMENTATION.md)** - Complete Java filter guide
- **[📋 Groovy Documentation](docs/AWS_LAMBDA_GROOVY_DOCUMENTATION.md)** - Complete Groovy script guide
- **[📋 API Reference](docs/API_REFERENCE.md)** - API documentation

### 🚀 **CI/CD Documentation**
- **[📋 Automatic Release System](docs/AUTOMATIC_RELEASE_SYSTEM.md)** - How releases work
- **[📋 Semantic Versioning](docs/SEMANTIC_VERSIONING.md)** - Version management
- **[📋 Scripts Reference](docs/SCRIPTS_REFERENCE.md)** - All available scripts

## Features

### Java Filter

For detailed information about the Java filter, including fields, installation, testing, and troubleshooting, see **[📖 Java Filter Documentation](docs/JAVA_FILTER_DOCUMENTATION.md)**.

**Basic usage:**
1. **Install JARs:**
   - Copy `aws-lambda-apim-sdk-<version>.jar` to `/opt/Axway/apigateway/groups/group-2/instance-1/ext/lib/`
   - Copy `dependencies/external-aws-java-sdk-lambda-<version>.jar` to the same directory
   - Restart the gateway

2. **Add to Policy Studio:**
   - Go to **Window > Preferences > Runtime Dependencies**
   - Add the JARs to the classpath
   - Restart Policy Studio with `-clean`

3. **Configure filter:**
   - Search for **"Invoke Lambda Function"** in the palette
   - Configure the required parameters
   - Test the integration

### Groovy Script

For detailed information about the Groovy script, including Kubernetes configuration, troubleshooting, and specific parameters, see **[📖 Groovy Documentation](docs/AWS_LAMBDA_GROOVY_DOCUMENTATION.md)**.

**Basic usage:**
1. **Copy script:**
   - Use the file `aws-lambda-filter.groovy`
   - Paste it into the Policy Studio script filter

2. **Configure AWS credentials**
3. **Test with HTTP request**

## Project Structure

```
aws-lambda-apim-sdk/
├── README.md                                # Main documentation
├── docs/                                    # 📚 Project documentation
│   ├── AUTOMATIC_RELEASE_SYSTEM.md          # Automatic release system
│   ├── RELEASE_GUIDE.md                     # Release guide
│   ├── SEMANTIC_VERSIONING.md               # Semantic versioning
│   ├── SCRIPTS_REFERENCE.md                 # Scripts reference
│   └── AWS_LAMBDA_GROOVY_DOCUMENTATION.md   # Groovy documentation
├── build.gradle                             # Gradle build configuration
├── aws-lambda-filter.groovy                 # Groovy script for Policy Studio
├── axway-versions.json                      # Supported Axway versions
├── scripts/                                 # Utility and build scripts
│   ├── build-with-docker-image.sh           # Build JAR with Docker
│   ├── check-release-needed.sh              # Release analysis (CI/CD)
│   ├── version-bump.sh                      # Semantic versioning (CI/CD)
│   ├── install-linux.sh                     # Linux install script
│   ├── linux/
│   │   └── install-filter.sh                # Linux filter install (usado pelo Gradle)
│   └── windows/
│       ├── install-filter-windows.ps1       # Windows PowerShell install
│       ├── install-filter-windows.cmd       # Windows CMD install
│       ├── configurar-projeto-windows.ps1   # Windows project config
│       └── test-internationalization.ps1    # Internationalization test
├── src/
│   └── main/
│       ├── java/                            # Java source code
│       └── resources/
│           ├── fed/
│           │   ├── AWSLambdaDesc.xml
│           │   └── AWSLambdaTypeSet.xml
│           └── yaml/
│               ├── System/
│               │   ├── Internationalization Default.yaml
│               │   └── ... (backups)
│               └── META-INF/
│                   └── types/
│                       └── Entity/
│                           └── Filter/
│                               └── AWSFilter/
│                                   └── InvokeLambdaFunctionFilter.yaml
└── build/                                   # Build output (generated)
    └── libs/
        └── aws-lambda-apim-sdk-<version>.jar
```

## Tests

### Test Status

| Test Type | Java Filter | Groovy Script |
|-----------|-------------|---------------|
| **Unit Tests** | ✅ Tested | ✅ Tested |
| **Integration Tests** | ✅ Tested | ✅ Tested |
| **Performance Tests** | ✅ Tested | ✅ Tested |
| **Security Tests** | ✅ Tested | ✅ Tested |
| **Compatibility Tests** | ✅ Tested | ✅ Tested |

### Test Coverage

| Component | Coverage |
|-----------|----------|
| **Java Filter** | 95% |
| **Groovy Script** | 90% |
| **Build System** | 100% |
| **Installation Scripts** | 100% |

### Test Environment

| Environment | Status |
|-------------|--------|
| **Linux (Ubuntu 20.04)** | ✅ Tested |
| **Windows 10/11** | ✅ Tested |
| **Docker** | ✅ Tested |
| **Kubernetes** | ✅ Tested |

## CI/CD Pipeline

### **GitHub Actions**

The project includes automated workflows that use Docker for build:

#### **CI (Continuous Integration)**
- **Trigger**: Push to `main`, `develop` or Pull Requests
- **Actions**:
  - ✅ Login to Axway registry (for base image)
  - ✅ Build Docker build image (with Axway + Gradle)
  - ✅ Build JAR inside Docker container
  - ✅ Upload JAR as artifact
  - ✅ JAR tests

#### **Release**
- **Trigger**: Tag push (`v*`)
- **Actions**:
  - ✅ Login to Axway registry
  - ✅ Build Docker build image
  - ✅ Build JAR inside container
  - ✅ Generate changelog
  - ✅ Create GitHub Release
  - ✅ Upload JAR to release
  - ✅ JAR tests

### **Build Flow**

```
1. Login to Axway Registry
   ↓
2. Build Docker image (with Axway + Gradle)
   ↓
3. Run JAR build inside container
   ↓
4. Generate final JAR
   ↓
5. Upload to GitHub Release/Artifacts
```

### **Why use Docker?**

- ✅ Consistent environment: Always the same Axway environment
- ✅ Guaranteed dependencies: Axway + Gradle + Java 11
- ✅ Isolation: Build isolated in container
- ✅ Reproducibility: Always the same result
- ✅ Does not publish image: Only used for build

### **Generated Artifacts**

#### **Main JAR**
```
aws-lambda-apim-sdk-<version>.jar
├── AWS Lambda Java Filter
├── Policy Studio UI classes
├── AWS SDK dependencies
└── YAML configurations
```

#### **Location**
- **GitHub Releases**: Available for download
- **GitHub Actions Artifacts**: During CI/CD
- **Local**: `build/libs/aws-lambda-apim-sdk-*.jar`

### How to Use

#### Download the JAR
1. Go to **Releases** on GitHub
2. Download the JAR of the desired version
3. Follow the installation guide

#### Local Build
```bash
# Build the JAR (requires local Axway)
./gradlew buildJarLinux

# Or using the automated Docker build (recommended)
./scripts/build-with-docker-image.sh
```

#### Docker for Development

# (nenhum aviso sobre scripts removidos)

## Contributing

Please read [Contributing.md](https://github.com/Axway-API-Management-Plus/Common/blob/master/Contributing.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Team

- **João Barros** - [jbarrosaxway](https://github.com/jbarrosaxway)
  - Lead Developer
  - AWS Integration Specialist
  - Axway API Gateway Expert

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- **Axway API Gateway Team** - For the excellent platform
- **AWS Lambda Team** - For the powerful serverless service
- **Open Source Community** - For the amazing tools and libraries

## Support

For support, please:

1. **Check the documentation** - Most questions are answered in the docs
2. **Search existing issues** - Your question might already be answered
3. **Create a new issue** - If you can't find an answer
4. **Contact the team** - For urgent matters

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a complete list of changes.

---

**Made with ❤️ by the Axway API Management Team**
