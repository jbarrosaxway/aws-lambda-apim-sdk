# Análise: DefaultAWSCredentialsProviderChain vs IRSA

## 🔍 Ordem de Prioridade do DefaultAWSCredentialsProviderChain

### **📋 Ordem Oficial (AWS SDK v1):**

1. **Environment Variables** (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. **Java System Properties** (`aws.accessKeyId`, `aws.secretKey`)
3. **AWS Credentials File** (`~/.aws/credentials`)
4. **AWS CLI Credentials File** (`~/.aws/credentials`)
5. **EC2 Instance Profile** (via metadata service)
6. **ECS Container Credentials** (via metadata service)

### **❌ PROBLEMA IDENTIFICADO:**

O `DefaultAWSCredentialsProviderChain` **NÃO** inclui IRSA automaticamente! 

## 🚨 Por Que Continua Usando Node Group

### **1️⃣ IRSA Não Está na Chain Padrão**
```java
// DefaultAWSCredentialsProviderChain NÃO inclui IRSA
// Ele só vai até EC2 Instance Profile
```

### **2️⃣ Solução: Usar WebIdentityTokenCredentialsProvider**

Para IRSA funcionar, precisamos usar o provider específico:

```java
// ❌ ERRADO - Não detecta IRSA
return new DefaultAWSCredentialsProviderChain();

// ✅ CORRETO - Detecta IRSA
return new WebIdentityTokenCredentialsProvider();
```

## 🔧 Correção Necessária
 