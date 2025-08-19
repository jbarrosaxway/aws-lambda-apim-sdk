# AWS Lambda Configurable Payload Feature

## 📋 Visão Geral

Esta funcionalidade permite configurar dinamicamente quais campos serão enviados no payload para a função Lambda, permitindo personalizar o evento para diferentes Lambdas sem alterar código.

## 🔧 Configuração

### Campos Disponíveis

Na configuração do filtro AWS Lambda, você pode especificar os nomes dos campos que deseja incluir no payload:

| **Campo de Configuração** | **Dados Enviados** | **Exemplo** |
|---------------------------|-------------------|-------------|
| `payloadMethodField` | Método HTTP | `"request_method": "POST"` |
| `payloadHeadersField` | Headers HTTP como Map | `"request_headers": {"content-type": "application/json"}` |
| `payloadBodyField` | Body da requisição como String | `"request_body": "{\"user\":\"joao\"}"` |
| `payloadUriField` | URI da requisição | `"request_uri": "/api/users"` |
| `payloadQueryStringField` | Query string | `"request_querystring": "filter=active"` |

### 📝 Como Configurar

1. **Se o campo estiver VAZIO ou NULL** → O campo NÃO será incluído no payload
2. **Se o campo tiver um NOME** → O campo será incluído com esse nome

## 🎯 Exemplos de Configuração

### **Exemplo 1: Apenas Headers e Body**
```
payloadMethodField: (vazio)
payloadHeadersField: headers
payloadBodyField: payload
payloadUriField: (vazio)
payloadQueryStringField: (vazio)
```

**Resultado:**
```json
{
  "headers": {
    "content-type": "application/json",
    "authorization": "Bearer token123"
  },
  "payload": "{\"user\":\"joao\"}"
}
```

### **Exemplo 2: Compatível com Kong Plugin**
```
payloadMethodField: request_method
payloadHeadersField: request_headers
payloadBodyField: request_body
payloadUriField: request_uri
payloadQueryStringField: request_querystring
```

**Resultado:**
```json
{
  "request_method": "POST",
  "request_headers": {
    "content-type": "application/json",
    "authorization": "Bearer token123"
  },
  "request_body": "{\"user\":\"joao\"}",
  "request_uri": "/api/users",
  "request_querystring": "filter=active"
}
```

### **Exemplo 3: Apenas Body**
```
payloadMethodField: (vazio)
payloadHeadersField: (vazio)
payloadBodyField: data
payloadUriField: (vazio)
payloadQueryStringField: (vazio)
```

**Resultado:**
```json
{
  "data": "{\"user\":\"joao\"}"
}
```

### **Exemplo 4: Nomes Customizados**
```
payloadMethodField: httpMethod
payloadHeadersField: httpHeaders
payloadBodyField: requestData
payloadUriField: endpoint
payloadQueryStringField: queryParams
```

**Resultado:**
```json
{
  "httpMethod": "POST",
  "httpHeaders": {
    "content-type": "application/json"
  },
  "requestData": "{\"user\":\"joao\"}",
  "endpoint": "/api/users",
  "queryParams": "filter=active"
}
```

## 💡 Casos de Uso

### **🎯 1. Lambda Simples (Apenas Dados)**
- Configure apenas `payloadBodyField: "data"`
- Lambda recebe apenas o body da requisição

### **🎯 2. Lambda Compatível com API Gateway**
- Use os nomes padrão do API Gateway: `httpMethod`, `headers`, `body`, etc.
- Lambda pode ser usado tanto via API Gateway quanto via Axway

### **🎯 3. Lambda Compatível com Kong**
- Use os nomes do Kong: `request_method`, `request_headers`, etc.
- Facilita migração entre Kong e Axway

### **🎯 4. Lambda Customizado**
- Use nomes específicos da sua aplicação
- Maior flexibilidade para diferentes tipos de Lambda

## 🔄 Comportamento

1. **Fallback**: Se nenhum campo for configurado, usa o comportamento original (apenas `content.body`)
2. **Headers**: Automaticamente convertidos para lowercase para consistência
3. **Body**: Sempre enviado como string, mesmo se for JSON
4. **Campos vazios**: São incluídos como string vazia ou objeto vazio `{}`

## 🚀 Compatibilidade

- ✅ **Retrocompatível**: Funciona com configurações existentes
- ✅ **Flexível**: Permite qualquer combinação de campos
- ✅ **Performático**: Só processa campos configurados
- ✅ **Seguro**: Fallback em caso de erro

## 📊 Logs

A funcionalidade adiciona logs de debug para acompanhar:
- Quantos campos foram configurados
- Qual payload foi gerado
- Erros na extração de dados

```
DEBUG: Configurable payload built with 3 fields: {"headers":{...},"payload":"...","method":"POST"}
```