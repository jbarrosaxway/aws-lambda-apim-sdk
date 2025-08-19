# Comparação: DefaultAWSCredentialsProviderChain vs WebIdentityTokenCredentialsProvider

## 🔍 Problema Identificado

### **❌ DefaultAWSCredentialsProviderChain**
```java
// NÃO inclui IRSA na chain padrão
return new DefaultAWSCredentialsProviderChain();
```

**Ordem de Prioridade:**
1. Environment Variables
2. Java System Properties  
3. AWS Credentials File
4. AWS CLI Credentials File
5. **EC2 Instance Profile** ← Para aqui!
6. ECS Container Credentials

**Resultado:** Sempre usa EC2 Instance Profile (node group)

### **✅ WebIdentityTokenCredentialsProvider**
```java
// Detecta IRSA automaticamente
return new WebIdentityTokenCredentialsProvider();
```

**Ordem de Prioridade:**
1. **IRSA Token** (`AWS_WEB_IDENTITY_TOKEN_FILE`)
2. **IRSA Role** (`AWS_ROLE_ARN`)
3. Fallback para outras credenciais

**Resultado:** Usa ServiceAccount quando configurado

## 🚀 Correção Implementada

### **Antes (ERRADO):**
```java
// Sempre usa EC2 Instance Profile
return new DefaultAWSCredentialsProviderChain();
```

### **Depois (CORRETO):**
```java
// Detecta IRSA primeiro
if (webIdentityTokenFile != null && roleArn != null) {
    // IRSA disponível
    return new WebIdentityTokenCredentialsProvider();
} else {
    // Fallback para EC2 Instance Profile
    return new DefaultAWSCredentialsProviderChain();
}
```

## 📊 Como Testar

### **1️⃣ Verificar Variáveis de Ambiente**
```bash
# No pod do Axway
kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- env | grep -E "(AWS_WEB_IDENTITY|AWS_ROLE_ARN)"
```

**Esperado (IRSA configurado):**
```
AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
AWS_ROLE_ARN=arn:aws:iam::785212834098:role/axway-lambda-role
```

### **2️⃣ Verificar Logs do Código**
```bash
# Ver logs do Axway
kubectl logs -n axway deployment/apim-digio-gateway-apitraffic | grep -i "IRSA"
```

**Esperado (IRSA funcionando):**
```
INFO ✅ IRSA detected - using WebIdentityTokenCredentialsProvider
INFO Access Key: ASIA...
INFO Secret Key: ***
```

**Esperado (IRSA não configurado):**
```
INFO ⚠️ IRSA not available - using DefaultAWSCredentialsProviderChain (EC2 Instance Profile)
```

### **3️⃣ Verificar User ARN**
```bash
# Testar credenciais
aws sts get-caller-identity
```

**Esperado (IRSA funcionando):**
```json
{
  "UserId": "AROA...:botocore-session-1234567890",
  "Account": "785212834098",
  "Arn": "arn:aws:sts::785212834098:assumed-role/axway-lambda-role/botocore-session-1234567890"
}
```

**Esperado (IRSA não funcionando):**
```json
{
  "UserId": "AROA...:i-0e3457ebb8b1a37ee",
  "Account": "785212834098", 
  "Arn": "arn:aws:sts::785212834098:assumed-role/axway-first-ng-role/i-0e3457ebb8b1a37ee"
}
```

## 🔧 Configuração Necessária

### **1️⃣ ServiceAccount com IRSA**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: axway-lambda-sa
  namespace: axway
  annotations:
    eks.amazonaws.com/role-arn: "arn:aws:iam::785212834098:role/axway-lambda-role"
```

### **2️⃣ Deployment usando ServiceAccount**
```yaml
spec:
  template:
    spec:
      serviceAccountName: axway-lambda-sa  # IMPORTANTE!
```

### **3️⃣ IAM Role com Trust Policy**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::785212834098:oidc-provider/oidc.eks.sa-east-1.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "oidc.eks.sa-east-1.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE:aud": "sts.amazonaws.com",
          "oidc.eks.sa-east-1.amazonaws.com/id/EXAMPLED539D4633E53DE1B71EXAMPLE:sub": "system:serviceaccount:axway:axway-lambda-sa"
        }
      }
    }
  ]
}
```

## ✅ Indicadores de Sucesso

1. **Logs mostram:** `✅ IRSA detected - using WebIdentityTokenCredentialsProvider`
2. **User ARN contém:** `assumed-role/axway-lambda-role`
3. **Variáveis de ambiente:** `AWS_WEB_IDENTITY_TOKEN_FILE` e `AWS_ROLE_ARN` definidas
4. **Lambda invoke funciona:** Sem erro 403

## ❌ Indicadores de Problema

1. **Logs mostram:** `⚠️ IRSA not available - using DefaultAWSCredentialsProviderChain`
2. **User ARN contém:** `assumed-role/axway-first-ng-role`
3. **Erro 403:** AccessDeniedException
4. **Variáveis de ambiente IRSA:** Não definidas

## 🎯 Próximos Passos

1. **Redeploy** com código corrigido
2. **Configurar ServiceAccount** com annotation IRSA
3. **Verificar logs** para confirmar IRSA funcionando
4. **Testar** Lambda invoke 