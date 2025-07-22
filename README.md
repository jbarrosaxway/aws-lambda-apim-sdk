# AWS Lambda Integration for Axway API Gateway

Este projeto oferece integração com AWS Lambda através de filtros customizados para o Axway API Gateway, suportando tanto filtros Java quanto scripts Groovy.

## API Management Version Compatibility

Este artefato foi testado com sucesso nas seguintes versões:
- **Axway API Gateway 7.7.0.20240830** ✅

## Visão Geral

O projeto oferece duas abordagens para integração com AWS Lambda:

### 1. Filtro Java (Recomendado)
- Interface gráfica no Policy Studio
- Configuração via parâmetros visuais
- Performance nativa do gateway
- Build automatizado

### 2. Script Groovy (Alternativa)
- Flexibilidade total
- Edição direta do script
- Configuração dinâmica
- Debugging detalhado

## Build e Instalação

### 🔧 Configuração Dinâmica

O projeto suporta **configuração dinâmica** do caminho do Axway API Gateway:

```bash
# Configuração padrão
./gradlew clean build installLinux

# Configuração customizada
./gradlew -Daxway.base=/opt/axway/Axway-7.7.0.20210830 clean build installLinux

# Verificar configuração atual
./gradlew setAxwayPath
```

### Linux
```bash
# Build do JAR (apenas Linux)
./gradlew buildJarLinux

# Build e instalação automática
./gradlew clean build installLinux

# Com caminho customizado
./gradlew -Daxway.base=/caminho/para/axway clean build installLinux
```

### Windows
```bash
# Instalação apenas dos arquivos YAML em projeto Policy Studio
./gradlew installWindows

# Instalação em projeto específico (com caminho)
./gradlew "-Dproject.path=C:\Users\jbarros\apiprojects\DIGIO-POC-AKS" installWindowsToProject

# Instalação interativa (se não especificar caminho)
./gradlew installWindowsToProject
```

> 📖 **Guia Completo Windows**: Veja [INSTALACAO_WINDOWS.md](INSTALACAO_WINDOWS.md) para instruções detalhadas.

### 🐳 **Docker**

#### **Imagem Docker para Build e Desenvolvimento**

Este projeto inclui suporte para Docker usando a imagem oficial do Axway API Gateway. A imagem é **apenas para build e desenvolvimento**, não para execução do gateway:

```bash
# Build da imagem
./scripts/docker/build-image.sh

# Ou manualmente:
./gradlew buildJarLinux
docker build -t aws-lambda-apim-sdk:latest .

# Testar a imagem
docker run --rm aws-lambda-apim-sdk:latest java -version
docker run --rm aws-lambda-apim-sdk:latest ls -la /opt/aws-lambda-sdk/
```

> ⚠️ **Nota**: Esta imagem contém o SDK integrado ao Axway API Gateway e é destinada para desenvolvimento e build de projetos que dependem do SDK, não para execução do gateway em produção.

#### **Estrutura de JARs na Imagem**

A imagem inclui os seguintes JARs organizados:

```
/opt/aws-lambda-sdk/
└── aws-lambda-apim-sdk-*.jar          # Nosso SDK

/opt/Axway/apigateway/groups/emt-group/emt-service/ext/lib/
├── aws-lambda-apim-sdk-*.jar          # Nosso SDK (cópia)
├── aws-java-sdk-lambda-*.jar          # AWS Lambda SDK
├── aws-java-sdk-core-*.jar            # AWS Core SDK
└── jackson-*.jar                      # Jackson JSON library
```

#### **Configuração do Registry Privado**

Para usar a imagem oficial do Axway, configure as credenciais:

**GitHub Actions:**
1. Vá para **Settings > Secrets and variables > Actions**
2. Adicione os secrets:
   - `AXWAY_REGISTRY_USERNAME`: seu usuário Axway
   - `AXWAY_REGISTRY_PASSWORD`: sua senha Axway

**Build Local:**
```bash
# Login manual
docker login docker.repository.axway.com

# Ou usando variáveis de ambiente
export AXWAY_USERNAME=seu_usuario
export AXWAY_PASSWORD=sua_senha
./scripts/docker/build-image.sh
```

#### **Especificações da Imagem:**
- **Base**: Axway API Gateway 7.7.0.20240830-4-BN0145-ubi9
- **Java**: OpenJDK 11.0.27
- **SDK**: aws-lambda-apim-sdk integrado em `/opt/aws-lambda-sdk/`
- **JARs**: Copiados para `/opt/Axway/apigateway/groups/emt-group/emt-service/ext/lib/`
- **Uso**: Build e desenvolvimento de projetos que dependem do SDK

#### **Scripts de Debug:**
```bash
# Verificar JARs disponíveis na imagem base
./scripts/docker/check-axway-jars.sh

# Build completo com Docker
./scripts/docker/build-with-docker.sh

# Debug da imagem base
./scripts/docker/debug-image.sh
```

> 📖 **Guia Completo Docker**: Veja [DOCKER_GUIDE.md](DOCKER_GUIDE.md) para instruções detalhadas.

### ⚠️ **Importante: Build do JAR**

O **build do JAR deve ser feito no Linux** devido às dependências do Axway API Gateway. Para Windows:

1. **Build no Linux:**
   ```bash
   ./gradlew buildJarLinux
   ```

2. **Copiar JAR para Windows:**
   ```bash
   # Copie o arquivo: build/libs/aws-lambda-apim-sdk-1.0.1.jar
   # Para o ambiente Windows
   ```

3. **Instalar YAML no Windows:**
   ```bash
   ./gradlew installWindows
   ```

### 🔄 **Processo Linux vs Windows**

| Linux | Windows |
|-------|---------|
| ✅ Build do JAR | ❌ Build do JAR |
| ✅ Instalação completa | ✅ Instalação YAML |
| ✅ Dependências nativas | ⚠️ JARs externos |
| ✅ Configuração automática | ⚠️ Configuração manual |

**Linux**: Processo completo (JAR + YAML + instalação)  
**Windows**: Apenas YAML (JAR deve ser buildado no Linux)

### Comandos Úteis
```bash
# Ver todas as tasks disponíveis
./gradlew showTasks

# Mostrar links dos JARs AWS SDK
./gradlew showAwsJars

# Verificar configuração do Axway
./gradlew setAxwayPath

# Apenas build
./gradlew clean build
```

## Instalação Manual (Alternativa)

### Linux

1. **Build e instalação automática:**
   ```bash
   ./gradlew clean build
   ./scripts/linux/install-filter.sh
   ```

2. **Configurar Policy Studio:**
   - Abra o Policy Studio
   - Vá em **Window > Preferences > Runtime Dependencies**
   - Adicione o JAR: `/opt/axway/Axway-7.7.0.20240830/apigateway/groups/group-2/instance-1/ext/lib/aws-lambda-apim-sdk-1.0.1.jar`
   - Reinicie o Policy Studio com `-clean`

### Windows

1. **Configurar projeto (primeira vez):**
   ```powershell
   .\scripts\windows\configurar-projeto-windows.ps1
   ```

2. **Instalar arquivos YAML:**
   ```powershell
   .\scripts\windows\install-filter-windows.ps1
   ```
   ou
   ```cmd
   scripts\windows\install-filter-windows.cmd
   ```

3. **Download manual dos JARs AWS SDK:**
   - [aws-java-sdk-lambda-1.12.314.jar](https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-lambda/1.12.314/aws-java-sdk-lambda-1.12.314.jar)
   - [aws-java-sdk-core-1.12.314.jar](https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-core/1.12.314/aws-java-sdk-core-1.12.314.jar)

4. **Configurar Policy Studio:**
   - Adicione os JARs AWS SDK ao classpath
   - Reinicie o Policy Studio com `-clean`

## Configuração AWS

### Credenciais

#### 1. Arquivo de Credenciais (Recomendado)
```ini
# ~/.aws/credentials
[default]
aws_access_key_id = sua_access_key
aws_secret_access_key = sua_secret_key
aws_session_token = seu_session_token  # opcional
```

#### 2. Variáveis de Ambiente
```bash
export AWS_ACCESS_KEY_ID="sua_access_key"
export AWS_SECRET_ACCESS_KEY="sua_secret_key"
export AWS_SESSION_TOKEN="seu_session_token"  # opcional
export AWS_DEFAULT_REGION="us-east-1"
```

#### 3. IAM Roles (para EKS/EC2)
Configure IAM Roles para instâncias EC2 ou pods EKS.

## Uso

### Filtro Java

1. **Adicionar filtro à política:**
   - Abra o Policy Studio
   - Procure por **"AWS Lambda Filter"** na paleta
   - Arraste o filtro para a política

2. **Configurar parâmetros:**
   - `functionName` (obrigatório): Nome da função Lambda
   - `awsRegion` (opcional): Região AWS (padrão: `us-east-1`)
   - `invocationType` (opcional): Tipo de invocação (padrão: `RequestResponse`)
   - `logType` (opcional): Tipo de log (padrão: `None`)
   - `qualifier` (opcional): Versão ou alias da função
   - `maxRetries` (opcional): Número máximo de tentativas (padrão: `3`)
   - `retryDelay` (opcional): Delay entre tentativas em ms (padrão: `1000`)

3. **Atributos de saída:**
   - `aws.lambda.response`: Resposta da função Lambda
   - `aws.lambda.http.status.code`: Código de status HTTP

### Script Groovy

Para informações detalhadas sobre o script Groovy, incluindo configuração Kubernetes, troubleshooting e parâmetros específicos, consulte o arquivo `AWS_LAMBDA_GROOVY_DOCUMENTATION.md`.

**Uso básico:**
1. **Copiar script:**
   - Use o arquivo `aws-lambda-filter.groovy`
   - Cole no filtro de script do Policy Studio

2. **Configurar credenciais AWS**
3. **Testar com requisição HTTP**

## Estrutura do Projeto

```
aws-lambda-apim-sdk/
├── README.md                                # Documentação principal
├── AWS_LAMBDA_GROOVY_DOCUMENTATION.md      # Guia específico Groovy
├── build.gradle                             # Configuração build + tasks
├── aws-lambda-filter.groovy                # Script Groovy
├── scripts/
│   ├── linux/
│   │   └── install-filter.sh               # Instalação Linux
│   └── windows/
│       ├── install-filter-windows.ps1      # PowerShell
│       ├── install-filter-windows.cmd       # CMD
│       ├── configurar-projeto-windows.ps1  # Configuração
│       └── test-internationalization.ps1   # Teste
├── src/main/                               # Código fonte
└── build/
    └── build/libs/aws-lambda-apim-sdk-1.0.1.jar
```

## Troubleshooting

### Problemas Comuns

1. **Filtro não aparece na paleta:**
   - Verifique se o JAR foi adicionado ao classpath
   - Reinicie o Policy Studio com `-clean`

2. **Erro de credenciais AWS:**
   - Verifique se as credenciais estão configuradas
   - Teste com `aws sts get-caller-identity`

3. **Erro de função não encontrada:**
   - Verifique o nome da função e a região
   - Confirme se a função existe na AWS

### Logs

O filtro gera logs detalhados:
- **Sucesso**: "Success in the AWS Lambda filter"
- **Falha**: "Failed in the AWS Lambda filter"
- **Erro**: "Error in the AWS Lambda Error: ${circuit.exception}"

## Comparação das Abordagens

| Aspecto | Filtro Java | Script Groovy |
|---------|-------------|---------------|
| **Interface** | Gráfica no Policy Studio | Script de texto |
| **Configuração** | Parâmetros visuais | Variáveis no script |
| **Manutenção** | Requer rebuild do JAR | Edição direta do script |
| **Performance** | Nativo do gateway | Interpretado |
| **Flexibilidade** | Limitada aos parâmetros definidos | Totalmente customizável |
| **Debugging** | Logs estruturados | Logs detalhados |

## Segurança

- Use IAM Roles quando possível
- Rotacione credenciais regularmente
- Use políticas IAM com privilégios mínimos
- Monitore logs de acesso e execução
- Considere usar AWS Secrets Manager para credenciais sensíveis

## Contributing

Please read [Contributing.md](https://github.com/Axway-API-Management-Plus/Common/blob/master/Contributing.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Team

![alt text][Axwaylogo] Axway Team

[Axwaylogo]: https://github.com/Axway-API-Management/Common/blob/master/img/AxwayLogoSmall.png  "Axway logo"

## License
[Apache License 2.0](LICENSE)

## 🚀 **CI/CD Pipeline**

### **GitHub Actions**

O projeto inclui workflows automatizados que usam Docker para build:

#### **CI (Continuous Integration)**
- **Trigger**: Push para `main`, `develop` ou Pull Requests
- **Ações**:
  - ✅ Login no registry Axway (para imagem base)
  - ✅ Build da imagem Docker de build (com Axway + Gradle)
  - ✅ Build do JAR dentro do container Docker
  - ✅ Upload do JAR como artifact
  - ✅ Testes do JAR

#### **Release**
- **Trigger**: Push de tags (`v*`)
- **Ações**:
  - ✅ Login no registry Axway
  - ✅ Build da imagem Docker de build
  - ✅ Build do JAR dentro do container
  - ✅ Geração de changelog
  - ✅ Criação de GitHub Release
  - ✅ Upload do JAR para o release
  - ✅ Testes do JAR

### **Fluxo de Build**

```
1. Login no Axway Registry
   ↓
2. Build da imagem Docker (com Axway + Gradle)
   ↓
3. Execução do build do JAR dentro do container
   ↓
4. Geração do JAR final
   ↓
5. Upload para GitHub Release/Artifacts
```

### **Por que usar Docker?**

- **✅ Ambiente Consistente**: Mesmo ambiente Axway sempre
- **✅ Dependências Garantidas**: Axway + Gradle + Java 11
- **✅ Isolamento**: Build isolado em container
- **✅ Reproduzibilidade**: Mesmo resultado sempre
- **✅ Não Publica Imagem**: Apenas usa para build

### **Artefatos Gerados**

#### **JAR Principal**
```
aws-lambda-apim-sdk-1.0.1.jar
├── Filtro Java AWS Lambda
├── Classes de UI do Policy Studio
├── Dependências AWS SDK
└── Configurações YAML
```

#### **Localização**
- **GitHub Releases**: Disponível para download
- **GitHub Actions Artifacts**: Durante CI/CD
- **Local**: `build/libs/aws-lambda-apim-sdk-*.jar`

### **Como Usar**

#### **Download do JAR**
1. Vá para **Releases** no GitHub
2. Baixe o JAR da versão desejada
3. Siga o guia de instalação

#### **Build Local**
```bash
# Build do JAR (requer Axway local)
./gradlew buildJarLinux

# Ou usando Docker (recomendado)
./scripts/docker/build-with-docker.sh
```

#### **Docker para Desenvolvimento**
```bash
# Build da imagem para desenvolvimento
./scripts/docker/build-image.sh

# Testar
docker run --rm aws-lambda-apim-sdk:latest java -version
```
