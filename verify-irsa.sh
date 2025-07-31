#!/bin/bash

echo "=== Verificação de IRSA ==="

# Verificar se o pod tem as variáveis de ambiente do IRSA
echo "📋 Verificando variáveis de ambiente IRSA:"
echo "AWS_WEB_IDENTITY_TOKEN_FILE: $AWS_WEB_IDENTITY_TOKEN_FILE"
echo "AWS_ROLE_ARN: $AWS_ROLE_ARN"
echo "AWS_REGION: $AWS_REGION"

# Verificar se o token existe
if [ -n "$AWS_WEB_IDENTITY_TOKEN_FILE" ] && [ -f "$AWS_WEB_IDENTITY_TOKEN_FILE" ]; then
    echo "✅ Token IRSA encontrado: $AWS_WEB_IDENTITY_TOKEN_FILE"
    echo "📄 Conteúdo do token (primeiras linhas):"
    head -5 "$AWS_WEB_IDENTITY_TOKEN_FILE"
else
    echo "❌ Token IRSA não encontrado"
fi

# Verificar se o AWS CLI está configurado para usar IRSA
echo -e "\n📋 Verificando configuração AWS:"
aws sts get-caller-identity 2>/dev/null || echo "❌ AWS CLI não configurado ou sem permissões"

# Testar acesso ao Lambda
echo -e "\n📋 Testando acesso ao Lambda:"
aws lambda list-functions --region sa-east-1 --max-items 1 2>/dev/null || echo "❌ Sem acesso ao Lambda"

# Verificar se o código Java está usando as credenciais corretas
echo -e "\n📋 Verificando credenciais no código Java:"
echo "O código deve usar DefaultAWSCredentialsProviderChain para detectar IRSA automaticamente"

echo -e "\n=== Debug do Código ==="
echo "Para verificar se o IRSA está funcionando, adicione logs no código:"
cat << 'EOF'
// No método getCredentialsProvider()
Trace.info("=== IRSA Debug ===");
Trace.info("AWS_WEB_IDENTITY_TOKEN_FILE: " + System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
Trace.info("AWS_ROLE_ARN: " + System.getenv("AWS_ROLE_ARN"));
Trace.info("AWS_REGION: " + System.getenv("AWS_REGION"));

// Testar credenciais
try {
    AWSCredentials credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
    Trace.info("Access Key: " + credentials.getAWSAccessKeyId());
    Trace.info("Secret Key: " + (credentials.getAWSSecretKey() != null ? "***" : "null"));
} catch (Exception e) {
    Trace.error("Erro ao obter credenciais: " + e.getMessage());
}
EOF

echo -e "\n=== Próximos Passos ==="
echo "1. ✅ Verificar se o ServiceAccount está configurado com annotation eks.amazonaws.com/role-arn"
echo "2. ✅ Verificar se o pod está usando o ServiceAccount correto"
echo "3. ✅ Verificar se o IAM Role tem as permissões corretas"
echo "4. ✅ Verificar se o OIDC provider está configurado no cluster"
echo "5. ✅ Rebuild e redeploy do pod com as configurações corretas" 