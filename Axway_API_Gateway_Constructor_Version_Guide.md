# 📋 **Guia de Replicação - Correção de Versionamento e Construtores para Projetos Axway API Gateway**

## 🎯 **Problemas Comuns Identificados**

### **1. Problema de Construtor Privado**
```java
// ❌ ERRADO - Causa erro no Axway
private GetSecretValueProcessor() {
    throw new UnsupportedOperationException("This class should not be instantiated directly");
}
```

### **2. Problema de Versionamento Hardcoded**
```gradle
// ❌ ERRADO - Versão sempre fixa
jar {
    archiveVersion = '1.0.11'  // Hardcoded!
    manifest {
        attributes(
            'Implementation-Version': '1.0.11'  // Hardcoded!
        )
    }
}
```

## ✅ **Soluções Aplicadas**

### **1. Correção do Construtor**

#### **Padrão Correto para Processors Axway:**
```java
public class GetSecretValueProcessor extends MessageProcessor {
    
    // Campos não-final para inicialização posterior
    private Selector<String> secretName;
    private Selector<String> secretRegion;
    // ... outros campos
    
    /**
     * Construtor público obrigatório para Axway API Gateway
     */
    public GetSecretValueProcessor() {
        // Construtor padrão para instanciação do Axway
    }
    
    /**
     * Método de configuração chamado pelo Axway
     */
    @Override
    public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
        super.filterAttached(ctx, entity);
        
        // Inicialização dos campos aqui
        this.secretName = new Selector<String>(entity.getStringValue("secretName"), String.class);
        this.secretRegion = new Selector<String>(entity.getStringValue("secretRegion"), String.class);
        // ... inicializar outros campos
        
        // Configuração adicional
        this.clientBuilder = createClientBuilder(ctx, entity);
    }
}
```

### **2. Correção do Versionamento**

#### **build.gradle - Versão Dinâmica:**
```gradle
group 'com.axway'
version '1.0.16'  // ← Única fonte da versão
sourceCompatibility = 1.8

jar {
    archiveBaseName = 'aws-secretsmanager-apim-sdk'
    archiveVersion = version  // ← Usa versão do projeto
    manifest {
        attributes(
            'Implementation-Title': 'AWS Secrets Manager APIM SDK',
            'Implementation-Version': version,  // ← Usa versão do projeto
            'Built-By': System.getProperty('user.name'),
            'Built-Date': new Date(),
            'Built-JDK': System.getProperty('java.version')
        )
    }
}
```

#### **Scripts - Detecção Dinâmica:**
```bash
# ❌ ANTES (hardcoded)
JAR_FILE="build/libs/aws-secretsmanager-apim-sdk-1.0.11.jar"

# ✅ DEPOIS (dinâmico)
JAR_FILE=$(find build/libs -name "aws-secretsmanager-apim-sdk-*.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Please run './gradlew build' first."
    exit 1
fi
```

#### **README.md - Referências Genéricas:**
```markdown
# ❌ ANTES
- Add the JAR: `aws-secretsmanager-apim-sdk-1.0.11.jar`

# ✅ DEPOIS
- Add the JAR: `aws-secretsmanager-apim-sdk-<version>.jar`
```

## 🔧 **Checklist de Replicação**

### **1. Verificar Construtores**
```bash
# Buscar construtores privados
grep -r "private.*Processor()" src/main/java/
grep -r "private.*Filter()" src/main/java/
```

### **2. Verificar Versionamento**
```bash
# Buscar versões hardcoded
grep -r "archiveVersion.*'[0-9]" build.gradle
grep -r "Implementation-Version.*'[0-9]" build.gradle
grep -r "1\.0\.[0-9]" scripts/ README.md
```

### **3. Aplicar Correções**

#### **A. Construtor (Processor.java)**
```java
// 1. Remover construtor privado
// 2. Adicionar construtor público
public GetSecretValueProcessor() {
    // Default constructor for Axway API Gateway instantiation
}

// 3. Mover inicialização para filterAttached()
@Override
public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
    super.filterAttached(ctx, entity);
    // Inicialização aqui
}

// 4. Remover final dos campos que precisam inicialização
private Selector<String> secretName;  // não final
```

#### **B. Versionamento (build.gradle)**
```gradle
// 1. Usar versão dinâmica
archiveVersion = version
'Implementation-Version': version

// 2. Atualizar referências em tasks
println "📁 JAR: build/libs/aws-secretsmanager-apim-sdk-${version}.jar"
def jarFile = new File("build/libs/aws-secretsmanager-apim-sdk-${version}.jar")
```

#### **C. Scripts**
```bash
# 1. Detecção dinâmica do JAR
JAR_FILE=$(find build/libs -name "aws-secretsmanager-apim-sdk-*.jar" | head -1)

# 2. Validação
if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Please run './gradlew build' first."
    exit 1
fi

# 3. Uso dinâmico
echo "📁 File: $JAR_FILE"
echo "📏 Size: $(du -h "$JAR_FILE" | cut -f1)"
```

#### **D. Documentação**
```markdown
# 1. Substituir versões hardcoded por genéricas
- Add the JAR: `aws-secretsmanager-apim-sdk-<version>.jar`

# 2. Atualizar exemplos
# Copy the file: build/libs/aws-secretsmanager-apim-sdk-<version>.jar
```

## 🧪 **Testes de Validação**

### **1. Teste de Compilação**
```bash
./gradlew clean build
```

### **2. Teste de Construtor**
```bash
# Verificar se não há erros de acesso
./gradlew compileJava
```

### **3. Teste de Versionamento**
```bash
# Verificar se JAR tem versão correta
ls -la build/libs/
jar -tf build/libs/aws-secretsmanager-apim-sdk-*.jar | grep Implementation-Version
```

### **4. Teste de Scripts**
```bash
# Verificar se scripts detectam JAR
./scripts/build-with-docker-image.sh
./scripts/linux/install-filter.sh
```

## 📋 **Padrões para Novos Projetos**

### **1. Estrutura de Processor**
```java
public class MyCustomProcessor extends MessageProcessor {
    
    // Campos não-final
    private Selector<String> field1;
    private Selector<String> field2;
    
    // Construtor público obrigatório
    public MyCustomProcessor() {
        // Default constructor for Axway API Gateway instantiation
    }
    
    // Inicialização no método correto
    @Override
    public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
        super.filterAttached(ctx, entity);
        
        // Inicialização aqui
        this.field1 = new Selector<String>(entity.getStringValue("field1"), String.class);
        this.field2 = new Selector<String>(entity.getStringValue("field2"), String.class);
    }
}
```

### **2. Configuração de Build**
```gradle
group 'com.axway'
version '1.0.0'  // ← Única fonte da versão

jar {
    archiveBaseName = 'my-custom-filter'
    archiveVersion = version  // ← Dinâmico
    manifest {
        attributes(
            'Implementation-Title': 'My Custom Filter',
            'Implementation-Version': version,  // ← Dinâmico
            'Built-By': System.getProperty('user.name'),
            'Built-Date': new Date(),
            'Built-JDK': System.getProperty('java.version')
        )
    }
}
```

### **3. Scripts Dinâmicos**
```bash
#!/bin/bash
# Detecção dinâmica do JAR
JAR_FILE=$(find build/libs -name "my-custom-filter-*.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Please run './gradlew build' first."
    exit 1
fi

echo "📁 JAR: $JAR_FILE"
```

## 📝 **Commit Template**

```bash
git commit -m "fix: resolve constructor access issue and version management

- Fix constructor access issue for Axway API Gateway compatibility
- Remove private constructor that was blocking instantiation
- Add public default constructor required by Axway
- Move initialization logic to filterAttached() method
- Remove final modifiers from fields that need initialization
- Fix version management to use dynamic version instead of hardcoded
- Update build.gradle to use project version dynamically
- Update scripts to use dynamic JAR file names
- Update README.md to use generic version references
- Improve compatibility with Axway API Gateway framework"
```

## ✅ **Benefícios da Replicação**

- ✅ **Compatibilidade garantida** com Axway API Gateway
- ✅ **Versionamento consistente** entre projeto e JAR
- ✅ **Scripts robustos** que funcionam com qualquer versão
- ✅ **Manutenção simplificada** - apenas um lugar para atualizar versão
- ✅ **Documentação atualizada** com referências genéricas
- ✅ **Padrões reutilizáveis** para novos projetos

## 🚀 **Como Usar Este Guia**

1. **Copie este guia** para seu projeto Axway API Gateway
2. **Adapte os nomes** de classes e pacotes para seu projeto
3. **Siga o checklist** de verificação
4. **Aplique as correções** na ordem especificada
5. **Execute os testes** de validação
6. **Use o template de commit** para documentar as mudanças

Este guia garante que projetos similares não enfrentem os mesmos problemas de construtor e versionamento! 🎉