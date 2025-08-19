# Guia de Validação IRSA - Axway Gateway

## 📋 Informações do Ambiente
- **Namespace**: `axway`
- **Deployment**: `apim-digio-gateway-apitraffic`
- **Account ID**: `785212834098`
- **Region**: `sa-east-1`
- **Lambda Function**: `uat-corporate-services-cpv`

## 🔍 Validação Sem Acesso ao Cluster

### 1️⃣ Verificar IAM Role
```bash
# Verificar se o role existe
aws iam get-role --role-name axway-lambda-role

# Verificar trust policy
aws iam get-role --role-name axway-lambda-role --query 'Role.AssumeRolePolicyDocument' --output json

# Verificar policies anexadas
aws iam list-attached-role-policies --role-name axway-lambda-role
```

**PowerShell:**
```powershell
# Verificar se o role existe
aws iam get-role --role-name axway-lambda-role

# Verificar trust policy
aws iam get-role --role-name axway-lambda-role --query 'Role.AssumeRolePolicyDocument' --output json

# Verificar policies anexadas
aws iam list-attached-role-policies --role-name axway-lambda-role
```

### 2️⃣ Verificar Lambda Function
```bash
# Verificar se a function existe
aws lambda get-function --function-name uat-corporate-services-cpv --region sa-east-1

# Testar permissões (sem executar)
aws lambda invoke \
  --function-name uat-corporate-services-cpv \
  --payload '{"test": "permission"}' \
  --region sa-east-1 \
  /tmp/test-output.json
```

**PowerShell:**
```powershell
# Verificar se a function existe
aws lambda get-function --function-name uat-corporate-services-cpv --region sa-east-1

# Testar permissões (sem executar)
aws lambda invoke --function-name uat-corporate-services-cpv --payload '{"test": "permission"}' --region sa-east-1 /tmp/test-output.json
```

### 3️⃣ Verificar via Logs do Axway

#### A. Logs que Indicam IRSA Funcionando:
```
INFO === IRSA Debug ===
INFO AWS_WEB_IDENTITY_TOKEN_FILE: /var/run/secrets/eks.amazonaws.com/serviceaccount/token
INFO AWS_ROLE_ARN: arn:aws:iam::785212834098:role/axway-lambda-role
INFO Access Key: ASIA...
INFO Secret Key: ***
```

#### B. Logs que Indicam Problema:
```
ERROR User: arn:aws:sts::785212834098:assumed-role/axway-first-ng-role/i-0e3457ebb8b1a37ee is not authorized
```

### 4️⃣ Comandos para Quem Tem Acesso ao Cluster

#### Verificar ServiceAccount:
```bash
kubectl get serviceaccount -n axway -o yaml
```

**PowerShell:**
```powershell
kubectl get serviceaccount -n axway -o yaml
```

**Deve conter:**
```yaml
metadata:
  annotations:
    eks.amazonaws.com/role-arn: "arn:aws:iam::785212834098:role/axway-lambda-role"
```

#### Verificar Pod:
```bash
kubectl get pod -n axway -l app=apim-digio-gateway-apitraffic -o yaml
```

**PowerShell:**
```powershell
kubectl get pod -n axway -l app=apim-digio-gateway-apitraffic -o yaml
```

**Deve conter:**
```yaml
spec:
  serviceAccountName: axway-lambda-sa  # ou nome do ServiceAccount
```

#### Verificar Variáveis de Ambiente:
```bash
kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- env | grep -E "(AWS_|IRSA)"
```

**PowerShell:**
```powershell
kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- env | Select-String "(AWS_|IRSA)"
```

**Deve mostrar:**
```
AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token
AWS_ROLE_ARN=arn:aws:iam::785212834098:role/axway-lambda-role
```

## 🛠️ Configuração Necessária

### 1️⃣ Criar IAM Role (se não existir)
```bash
# Criar policy
cat > lambda-policy.json << EOF
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
EOF

aws iam create-policy --policy-name AxwayLambdaPolicy --policy-document file://lambda-policy.json
```

### 2️⃣ Criar Trust Policy
```bash
# Obter OIDC provider do cluster
CLUSTER_NAME=$(aws eks list-clusters --query 'clusters[0]' --output text)
OIDC_PROVIDER=$(aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.identity.oidc.issuer' --output text | sed 's/https:\/\///')

cat > trust-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::785212834098:oidc-provider/$OIDC_PROVIDER"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "$OIDC_PROVIDER:aud": "sts.amazonaws.com",
          "$OIDC_PROVIDER:sub": "system:serviceaccount:axway:axway-lambda-sa"
        }
      }
    }
  ]
}
EOF

aws iam create-role --role-name axway-lambda-role --assume-role-policy-document file://trust-policy.json
aws iam attach-role-policy --role-name axway-lambda-role --policy-arn arn:aws:iam::785212834098:policy/AxwayLambdaPolicy
```

### 3️⃣ Configurar ServiceAccount
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: axway-lambda-sa
  namespace: axway
  annotations:
    eks.amazonaws.com/role-arn: "arn:aws:iam::785212834098:role/axway-lambda-role"
```

### 4️⃣ Atualizar Deployment
```yaml
spec:
  template:
    spec:
      serviceAccountName: axway-lambda-sa  # IMPORTANTE!
```

## ✅ Indicadores de Sucesso

1. **Logs mostram IRSA Debug** com variáveis de ambiente corretas
2. **User ARN contém** `assumed-role/axway-lambda-role`
3. **Lambda invoke funciona** sem erro 403
4. **Variáveis de ambiente** `AWS_WEB_IDENTITY_TOKEN_FILE` e `AWS_ROLE_ARN` estão definidas

## ❌ Indicadores de Problema

1. **User ARN contém** `assumed-role/axway-first-ng-role`
2. **Erro 403** AccessDeniedException
3. **Variáveis de ambiente IRSA** não estão definidas
4. **ServiceAccount** não tem annotation `eks.amazonaws.com/role-arn`

## 🚀 Próximos Passos

1. **Executar validação remota**: 
   - **Bash**: `./validate-irsa-remote.sh`
   - **PowerShell**: `.\validate-irsa-remote.ps1`
2. **Verificar logs do Axway** para debug IRSA
3. **Configurar ServiceAccount** se necessário
4. **Redeploy** com ServiceAccount correto
5. **Testar** Lambda invoke 