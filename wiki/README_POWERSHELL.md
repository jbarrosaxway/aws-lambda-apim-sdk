# Scripts PowerShell para Validação IRSA

## 📋 Scripts Disponíveis

### 1️⃣ `validate-irsa-remote.ps1`
Validação remota sem acesso ao cluster EKS.

**Executar:**
```powershell
.\validate-irsa-remote.ps1
```

**Funcionalidades:**
- ✅ Verifica AWS CLI instalado
- ✅ Verifica credenciais AWS configuradas
- ✅ Verifica IAM Role `axway-lambda-role`
- ✅ Verifica cluster EKS e OIDC provider
- ✅ Verifica Lambda function e permissões
- ✅ Fornece comandos para verificação no cluster

### 2️⃣ `verify-irsa.ps1`
Verificação IRSA no pod (executar dentro do container).

**Executar:**
```powershell
.\verify-irsa.ps1
```

**Funcionalidades:**
- ✅ Verifica variáveis de ambiente IRSA
- ✅ Verifica token IRSA
- ✅ Verifica credenciais AWS atuais
- ✅ Testa acesso ao Lambda
- ✅ Fornece debug para código Java

## 🚀 Como Usar

### Validação Remota (Sem Acesso ao Cluster)
```powershell
# 1. Navegar para o diretório
cd aws-lambda-apim-sdk

# 2. Executar validação remota
.\validate-irsa-remote.ps1
```

### Validação no Pod (Com Acesso ao Cluster)
```powershell
# 1. Copiar script para o pod
kubectl cp verify-irsa.ps1 axway/apim-digio-gateway-apitraffic-xxx:/tmp/

# 2. Executar no pod
kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- powershell -File /tmp/verify-irsa.ps1
```

## 📊 Interpretação dos Resultados

### ✅ IRSA Funcionando
```
✅ AWS CLI configurado
User ARN: arn:aws:sts::785212834098:assumed-role/axway-lambda-role/...
✅ Usando IRSA ServiceAccount
✅ Token IRSA encontrado: /var/run/secrets/eks.amazonaws.com/serviceaccount/token
✅ Acesso ao Lambda OK
```

### ❌ IRSA Não Funcionando
```
❌ Usando EC2 Instance Profile (node group)
User ARN: arn:aws:sts::785212834098:assumed-role/axway-first-ng-role/...
❌ Token IRSA não encontrado
❌ Sem acesso ao Lambda
```

## 🔧 Comandos Úteis

### Verificar ServiceAccount
```powershell
kubectl get serviceaccount -n axway -o yaml
```

### Verificar Pod
```powershell
kubectl get pod -n axway -l app=apim-digio-gateway-apitraffic -o yaml
```

### Verificar Variáveis de Ambiente
```powershell
kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- env | Select-String "(AWS_|IRSA)"
```

### Verificar Logs
```powershell
kubectl logs -n axway deployment/apim-digio-gateway-apitraffic | Select-String "IRSA|AWS_WEB_IDENTITY|AWS_ROLE_ARN"
```

## 🛠️ Configuração Necessária

### 1️⃣ Criar IAM Role (PowerShell)
```powershell
# Criar policy
$policy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "lambda:InvokeFunction",
        "lambda:GetFunction",
        "lambda:ListFunctions"
      ],
      "Resource": [
        "arn:aws:lambda:sa-east-1:785212834098:function:uat-corporate-services-cpv",
        "arn:aws:lambda:sa-east-1:785212834098:function:*"
      ]
    }
  ]
}
"@

$policy | Out-File -FilePath "lambda-policy.json" -Encoding UTF8
aws iam create-policy --policy-name AxwayLambdaPolicy --policy-document file://lambda-policy.json
```

### 2️⃣ Criar Trust Policy (PowerShell)
```powershell
# Obter OIDC provider do cluster
$clusters = aws eks list-clusters --query 'clusters[0]' --output text
$oidcProvider = aws eks describe-cluster --name $clusters --query 'cluster.identity.oidc.issuer' --output text
$oidcProvider = $oidcProvider.Replace("https://", "")

$trustPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::785212834098:oidc-provider/$oidcProvider"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "$oidcProvider`:aud": "sts.amazonaws.com",
          "$oidcProvider`:sub": "system:serviceaccount:axway:axway-lambda-sa"
        }
      }
    }
  ]
}
"@

$trustPolicy | Out-File -FilePath "trust-policy.json" -Encoding UTF8
aws iam create-role --role-name axway-lambda-role --assume-role-policy-document file://trust-policy.json
aws iam attach-role-policy --role-name axway-lambda-role --policy-arn arn:aws:iam::785212834098:policy/AxwayLambdaPolicy
```

## 📝 Logs Esperados

### Logs do Código Java (IRSA Funcionando)
```
INFO === IRSA Debug ===
INFO AWS_WEB_IDENTITY_TOKEN_FILE: /var/run/secrets/eks.amazonaws.com/serviceaccount/token
INFO AWS_ROLE_ARN: arn:aws:iam::785212834098:role/axway-lambda-role
INFO Access Key: ASIA...
INFO Secret Key: ***
```

### Logs do Código Java (IRSA Não Funcionando)
```
ERROR User: arn:aws:sts::785212834098:assumed-role/axway-first-ng-role/i-0e3457ebb8b1a37ee is not authorized
```

## 🎯 Próximos Passos

1. **Execute a validação remota**: `.\validate-irsa-remote.ps1`
2. **Verifique os logs do Axway** para debug IRSA
3. **Configure ServiceAccount** se necessário
4. **Redeploy** com configuração correta
5. **Teste** Lambda invoke 