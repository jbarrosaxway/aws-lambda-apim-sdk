# Verificação IRSA - PowerShell
Write-Host "=== Verificação de IRSA ===" -ForegroundColor Cyan

# Verificar se o pod tem as variáveis de ambiente do IRSA
Write-Host "📋 Verificando variáveis de ambiente IRSA:" -ForegroundColor Green
Write-Host "AWS_WEB_IDENTITY_TOKEN_FILE: $env:AWS_WEB_IDENTITY_TOKEN_FILE" -ForegroundColor White
Write-Host "AWS_ROLE_ARN: $env:AWS_ROLE_ARN" -ForegroundColor White
Write-Host "AWS_REGION: $env:AWS_REGION" -ForegroundColor White

# Verificar se o token existe
if ($env:AWS_WEB_IDENTITY_TOKEN_FILE -and (Test-Path $env:AWS_WEB_IDENTITY_TOKEN_FILE)) {
    Write-Host "✅ Token IRSA encontrado: $env:AWS_WEB_IDENTITY_TOKEN_FILE" -ForegroundColor Green
    Write-Host "📄 Conteúdo do token (primeiras linhas):" -ForegroundColor Yellow
    Get-Content $env:AWS_WEB_IDENTITY_TOKEN_FILE -Head 5
} else {
    Write-Host "❌ Token IRSA não encontrado" -ForegroundColor Red
}

# Verificar se o AWS CLI está configurado para usar IRSA
Write-Host "`n📋 Verificando configuração AWS:" -ForegroundColor Green
try {
    $callerIdentity = aws sts get-caller-identity 2>$null | ConvertFrom-Json
    if ($callerIdentity) {
        Write-Host "✅ AWS CLI configurado" -ForegroundColor Green
        Write-Host "User ARN: $($callerIdentity.Arn)" -ForegroundColor White
        
        # Verificar se está usando IRSA
        if ($callerIdentity.Arn -like "*assumed-role/axway-lambda-role*") {
            Write-Host "✅ Usando IRSA ServiceAccount" -ForegroundColor Green
        } elseif ($callerIdentity.Arn -like "*assumed-role/axway-first-ng-role*") {
            Write-Host "❌ Usando EC2 Instance Profile (node group)" -ForegroundColor Red
        } else {
            Write-Host "⚠️ Usando outras credenciais" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ AWS CLI não configurado ou sem permissões" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ AWS CLI não configurado ou sem permissões" -ForegroundColor Red
}

# Testar acesso ao Lambda
Write-Host "`n📋 Testando acesso ao Lambda:" -ForegroundColor Green
try {
    $lambdaFunctions = aws lambda list-functions --region sa-east-1 --max-items 1 2>$null
    if ($lambdaFunctions) {
        Write-Host "✅ Acesso ao Lambda OK" -ForegroundColor Green
    } else {
        Write-Host "❌ Sem acesso ao Lambda" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Sem acesso ao Lambda" -ForegroundColor Red
}

# Verificar se o código Java está usando as credenciais corretas
Write-Host "`n📋 Verificando credenciais no código Java:" -ForegroundColor Green
Write-Host "O código deve usar DefaultAWSCredentialsProviderChain para detectar IRSA automaticamente" -ForegroundColor White

Write-Host "`n=== Debug do Código ===" -ForegroundColor Cyan
Write-Host "Para verificar se o IRSA está funcionando, adicione logs no código:" -ForegroundColor Yellow
Write-Host @"
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
"@ -ForegroundColor White

Write-Host "`n=== Próximos Passos ===" -ForegroundColor Cyan
Write-Host "1. ✅ Verificar se o ServiceAccount está configurado com annotation eks.amazonaws.com/role-arn" -ForegroundColor Green
Write-Host "2. ✅ Verificar se o pod está usando o ServiceAccount correto" -ForegroundColor Green
Write-Host "3. ✅ Verificar se o IAM Role tem as permissões corretas" -ForegroundColor Green
Write-Host "4. ✅ Verificar se o OIDC provider está configurado no cluster" -ForegroundColor Green
Write-Host "5. ✅ Rebuild e redeploy do pod com as configurações corretas" -ForegroundColor Green

Write-Host "`n=== Comandos para Executar ===" -ForegroundColor Cyan
Write-Host "# Verificar ServiceAccount:" -ForegroundColor Yellow
Write-Host "kubectl get serviceaccount -n axway -o yaml" -ForegroundColor White

Write-Host "`n# Verificar pod:" -ForegroundColor Yellow
Write-Host "kubectl get pod -n axway -l app=apim-digio-gateway-apitraffic -o yaml" -ForegroundColor White

Write-Host "`n# Verificar variáveis de ambiente:" -ForegroundColor Yellow
Write-Host "kubectl exec -n axway deployment/apim-digio-gateway-apitraffic -- env | grep -E '(AWS_|IRSA)'" -ForegroundColor White

Write-Host "`n# Verificar logs:" -ForegroundColor Yellow
Write-Host "kubectl logs -n axway deployment/apim-digio-gateway-apitraffic | grep -i 'IRSA|AWS_WEB_IDENTITY|AWS_ROLE_ARN'" -ForegroundColor White 