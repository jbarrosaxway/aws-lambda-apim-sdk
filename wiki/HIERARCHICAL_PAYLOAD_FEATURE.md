# Hierarchical Payload Feature

## Visão Geral

A funcionalidade de payload hierárquico permite criar estruturas JSON complexas e aninhadas no payload do Lambda, usando notação de pontos para definir caminhos de campos.

## Funcionalidades

### 1. Estrutura Hierárquica
- **Campos aninhados**: Use notação de pontos para criar objetos aninhados
- **Criação automática**: Objetos intermediários são criados automaticamente conforme necessário
- **Flexibilidade**: Suporte para qualquer nível de aninhamento

### 2. Payload Inicial
- **Base existente**: Se `lambda.body` estiver definido no contexto da mensagem, será usado como base inicial
- **Fallback**: Se não existir ou não for JSON válido, inicia com payload vazio
- **Merge inteligente**: Novos campos são adicionados à estrutura existente

## Exemplos de Uso

### Exemplo 1: Estrutura Simples
```yaml
payloadMethodField: "method"
payloadHeadersField: "headers"
payloadBodyField: "body"
```

**Resultado:**
```json
{
  "method": "GET",
  "headers": {
    "content-type": "application/json",
    "authorization": "Bearer token"
  },
  "body": "{\"key\": \"value\"}"
}
```

### Exemplo 2: Estrutura Hierárquica
```yaml
payloadMethodField: "options.method"
payloadHeadersField: "options.headers"
payloadBodyField: "options.body"
payloadUriField: "options.path"
payloadQueryStringField: "options.query"
payloadParamsPathField: "options.params"
```

**Resultado:**
```json
{
  "options": {
    "method": "GET",
    "headers": {
      "content-type": "application/json"
    },
    "body": "{\"key\": \"value\"}",
    "path": "/api/endpoint",
    "query": "param1=value1&param2=value2",
    "params": {
      "id": "proposal_id"
    }
  }
}
```

### Exemplo 3: Estrutura Complexa
```yaml
payloadMethodField: "request.method"
payloadHeadersField: "request.headers"
payloadBodyField: "request.body"
payloadUriField: "request.uri"
payloadQueryStringField: "request.query"
```

**Resultado:**
```json
{
  "request": {
    "method": "POST",
    "headers": {
      "content-type": "application/json"
    },
    "body": "{\"data\": \"example\"}",
    "uri": "/api/data",
    "query": "filter=active"
  }
}
```

### Exemplo 4: Múltiplos Níveis
```yaml
payloadMethodField: "api.v1.request.method"
payloadHeadersField: "api.v1.request.headers"
payloadBodyField: "api.v1.request.body"
```

**Resultado:**
```json
{
  "api": {
    "v1": {
      "request": {
        "method": "GET",
        "headers": {
          "content-type": "application/json"
        },
        "body": "{\"id\": 123}"
      }
    }
  }
}
```

## Uso com lambda.body

### Cenário 1: Payload Base Existente
Se `lambda.body` contém:
```json
{
  "https": true,
  "crypto": {
    "request": true,
    "response": true
  }
}
```

E os campos configurados são:
```yaml
payloadMethodField: "options.method"
payloadHeadersField: "options.headers"
payloadBodyField: "options.body"
payloadParamsPathField: "options.params"
```

**Resultado final:**
```json
{
  "https": true,
  "crypto": {
    "request": true,
    "response": true
  },
  "options": {
    "method": "GET",
    "headers": {
      "content-type": "application/json"
    },
    "body": "{\"key\": \"value\"}",
    "params": {
      "id": "proposal_id"
    }
  }
}
```

### Cenário 2: Sem Payload Base
Se `lambda.body` não estiver definido, o resultado será apenas os campos configurados:
```json
{
  "options": {
    "method": "GET",
    "headers": {
      "content-type": "application/json"
    },
    "body": "{\"key\": \"value\"}",
    "params": {
      "id": "proposal_id"
    }
  }
}
```

## Configuração no Policy Studio

### 1. Aba "Configurable Payload"
Configure os campos de payload na nova aba:

- **Method Field Name**: Campo para método HTTP (ex: `options.method`)
- **Headers Field Name**: Campo para headers (ex: `options.headers`)
- **Body Field Name**: Campo para corpo da requisição (ex: `options.body`)
- **URI Field Name**: Campo para URI (ex: `options.path`)
- **Query String Field Name**: Campo para query string (ex: `options.query`)
- **Path Parameters Field Name**: Campo para parâmetros de path (ex: `options.params`)

### 2. Valores Padrão
Os valores padrão já estão configurados para criar uma estrutura `options.*`:
```yaml
payloadMethodField: "options.method"
payloadHeadersField: "options.headers"
payloadBodyField: "options.body"
payloadUriField: "options.path"
payloadQueryStringField: "options.query"
payloadParamsPathField: "options.params"
```

## Vantagens

1. **Flexibilidade**: Suporte para qualquer estrutura JSON
2. **Reutilização**: Pode usar payloads base existentes
3. **Organização**: Estrutura hierárquica clara e organizada
4. **Compatibilidade**: Mantém compatibilidade com implementações existentes
5. **Automação**: Criação automática de objetos intermediários

## Casos de Uso

- **APIs REST**: Estrutura `options.*` para parâmetros de requisição
- **Microserviços**: Estrutura `service.*` para dados específicos do serviço
- **Auditoria**: Estrutura `audit.*` para logs e rastreamento
- **Segurança**: Estrutura `security.*` para tokens e permissões
- **Integração**: Estrutura `integration.*` para dados de sistemas externos

## Logs e Debug

O sistema fornece logs detalhados para debug:
- ✅ Criação de objetos aninhados
- ✅ Valores definidos em campos hierárquicos
- ❌ Erros na criação de estrutura
- 📊 Payload final gerado

## Compatibilidade

- **Retrocompatível**: Campos simples ainda funcionam
- **Gradual**: Pode migrar gradualmente para estrutura hierárquica
- **Híbrido**: Pode misturar campos simples e hierárquicos
