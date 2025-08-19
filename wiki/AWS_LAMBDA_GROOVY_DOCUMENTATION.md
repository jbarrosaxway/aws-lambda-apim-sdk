# Invoke Lambda Function Integration for Axway API Gateway

This document describes how to integrate AWS Lambda with Axway API Gateway using Groovy scripts, including configuration for Kubernetes environments.

## Overview

The integration allows invoking AWS Lambda functions directly from Axway API Gateway through Groovy script filters, offering flexibility for authentication and credential configuration.

## Tested Version

✅ **Tested and validated on Axway API Gateway version 7.7.0.20240830**

## Prerequisites

- Axway API Gateway 7.7.0.20240830 (tested)
- AWS SDK for Java 1.12.314 (aws-java-sdk-lambda, aws-java-sdk-core)
- Jackson (included in the gateway)
- Access to AWS Lambda functions
- Configured AWS credentials

## Configuration

### 1. Dependencies

The script uses the following dependencies that must be available in the classpath:

#### Required JARs (Tested Versions):
- `aws-java-sdk-lambda-1.12.314.jar`
- `aws-java-sdk-core-1.12.314.jar`
- Jackson (included in the gateway - no additional JARs required)

#### JAR Location:
The JARs must be in the gateway's `ext/lib` directory. Example structure:
```
<VORDEL_HOME>/groups/group-<X>/instance-<Y>/ext/lib/
```

**Example**: `/opt/axway/Axway-7.7.0.20240830/apigateway/groups/group-2/instance-1/ext/lib/`

**Note**: The path may vary depending on your installation. Adjust as needed.

### 2. Policy Studio Configuration

**IMPORTANT**: JARs in the `ext/lib` directory are not automatically included in the Policy Studio classpath. You must add them manually:

1. Open Policy Studio
2. Go to **Window > Preferences > Runtime Dependencies**
3. Click **Add** and navigate to the `ext/lib` directory
4. Select the required JARs:
   - `aws-java-sdk-lambda-1.12.314.jar`
   - `aws-java-sdk-core-1.12.314.jar`
5. Click **Apply** to save
6. Restart Policy Studio with the `-clean` option

### 3. Installation

1. Copy the contents of the `aws-lambda-filter.groovy` file to the Policy Studio script filter
2. Configure the required parameters
3. Configure AWS credentials
4. Test the integration

## Groovy Script for Invoke Lambda Function

### Main Script

The main script is available in the `aws-lambda-filter.groovy` file. This script implements:

- Flexible AWS authentication (environment variables, credentials file, IAM Roles)
- Dynamic configuration via message attributes
- Automatic retry system
- HTTP request processing
- Lambda function invocation
- JSON and non-JSON response handling
- Detailed logging for troubleshooting
- Proper resource management

To use the script:

1. Open the `aws-lambda-filter.groovy` file in a text editor
2. Copy all the contents of the file
3. In Policy Studio, create a script filter and paste the contents
4. Configure the required parameters
5. Test the integration

### Configuration Parameters

The script accepts the following parameters via message attributes:

#### Required Parameters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `aws.lambda.function.name` | String | Lambda function name |

#### Optional Parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `aws.lambda.region` | String | `AWS_DEFAULT_REGION` | AWS region |
| `aws.lambda.payload` | String | `content.body` or `{}` | Payload for the function |
| `aws.lambda.invocation.type` | String | `RequestResponse` | Invocation type |
| `aws.lambda.log.type` | String | `None` | Log type |
| `aws.lambda.qualifier` | String | - | Function version or alias |
| `aws.lambda.client.context` | String | - | Client context (JSON string) |
| `aws.lambda.custom.headers` | String | - | Custom headers (JSON string) |
| `aws.lambda.max.retries` | String | `3` | Maximum number of retries |
| `aws.lambda.retry.delay.ms` | String | `1000` | Delay between retries in ms |

### Output Attributes

The script sets the following attributes in the message:

| Attribute | Type | Description |
|-----------|------|-------------|
| `aws.lambda.response` | String | Lambda function response |
| `aws.lambda.http.status.code` | Integer | HTTP status code |
| `aws.lambda.executed.version` | String | Executed function version |
| `aws.lambda.log.result` | String | Log results |
| `aws.lambda.error` | String | Error (if any) |

## AWS Credentials Configuration

### 1. Credentials File (Recommended - Most Secure)

**⚠️ Security Recommendation**: Use a credentials file instead of environment variables, especially in Kubernetes, as environment variables can be easily intercepted or read by the application.

Configure the `~/.aws/credentials` file:

```ini
[default]
aws_access_key_id = your_access_key
aws_secret_access_key = your_secret_key
aws_session_token = your_session_token  # optional
```

### 2. Environment Variables (Less Secure)

**⚠️ Warning**: Environment variables in Kubernetes can be easily intercepted or read by the application. Use only for development/testing.

```bash
export AWS_ACCESS_KEY_ID="your_access_key"
export AWS_SECRET_ACCESS_KEY="your_secret_key"
export AWS_SESSION_TOKEN="your_session_token"  # optional
export AWS_DEFAULT_REGION="us-east-1"
```

### 3. IAM Roles (Most Secure - for EKS/EC2)

Configure IAM Roles for EC2 instances or EKS pods. This is the most secure option for production environments.

## Kubernetes Configuration

### 1. Secret for AWS Credentials

#### Option 1: Secret with Credentials File (Recommended - Most Secure)

```bash
kubectl create secret generic aws-credentials \
  --from-file=credentials=/home/USER/.aws/credentials \
  --namespace=axway
```

**Note**: Replace `/home/USER` with the full path to your home directory. `~` does not work with kubectl.

This option mounts the full AWS credentials file in the container, allowing the use of multiple profiles and is more secure than environment variables.

#### Option 2: Secret with Environment Variables (Less Secure)

**⚠️ Warning**: Environment variables can be easily intercepted. Use only for development/testing.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: aws-credentials
  namespace: axway
type: Opaque
data:
  AWS_ACCESS_KEY_ID: <base64-encoded-access-key>
  AWS_SECRET_ACCESS_KEY: <base64-encoded-secret-key>
  AWS_SESSION_TOKEN: <base64-encoded-session-token>  # optional
```

### 2. Configuration in values.yaml (Tested)

For Kubernetes environments with Helm, configure the APIM `values.yaml` with the following sections:

#### For apimgr and apitraffic:

```yaml
apimgr:
  # ... other settings ...
  extraVolumeMounts:
    # ... other volumes ...
    # AWS configuration - default .aws directory
    - name: aws-config-volume
      mountPath: /opt/axway/apigateway/system/conf/.aws
      readOnly: true
  extraVolumes:
    # ... other volumes ...
    # Volume for AWS credentials
    - name: aws-config-volume
      secret:
        secretName: aws-credentials
        items:
          - key: credentials
            path: credentials
  extraEnvVars:
    # ... other variables ...
    # AWS settings
    - name: AWS_SHARED_CREDENTIALS_FILE
      value: "/opt/axway/apigateway/system/conf/.aws/credentials"
    - name: AWS_DEFAULT_REGION
      value: "us-east-1"
    # Optional: Set specific profile if needed
    # - name: AWS_PROFILE
    #   value: "default"

apitraffic:
  # ... other settings ...
  extraVolumeMounts:
    # ... other volumes ...
    # AWS configuration - default .aws directory
    - name: aws-config-volume
      mountPath: /opt/axway/apigateway/system/conf/.aws
      readOnly: true
  extraVolumes:
    # ... other volumes ...
    # Volume for AWS credentials
    - name: aws-config-volume
      secret:
        secretName: aws-credentials
        items:
          - key: credentials
            path: credentials
  extraEnvVars:
    # ... other variables ...
    # AWS settings
    - name: AWS_SHARED_CREDENTIALS_FILE
      value: "/opt/axway/apigateway/system/conf/.aws/credentials"
    - name: AWS_DEFAULT_REGION
      value: "us-east-1"
    # Optional: Set specific profile if needed
    # - name: AWS_PROFILE
    #   value: "default"
```

#### Alternative Configuration: Simple Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: axway-api-gateway
  namespace: axway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: axway-api-gateway
  template:
    metadata:
      labels:
        app: axway-api-gateway
    spec:
      containers:
      - name: axway-gateway
        image: axway/api-gateway:latest
        env:
        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: AWS_ACCESS_KEY_ID
        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: AWS_SECRET_ACCESS_KEY
        - name: AWS_SESSION_TOKEN
          valueFrom:
            secretKeyRef:
              name: aws-credentials
              key: AWS_SESSION_TOKEN
        - name: AWS_DEFAULT_REGION
          value: "us-east-1"

### 3. Creating the Secret

#### For Secret with Credentials File (Recommended - Most Secure):

```bash
kubectl create secret generic aws-credentials \
  --from-file=credentials=/home/USER/.aws/credentials \
  --namespace=axway
```

**Note**: Replace `/home/USER` with the full path to your home directory. `~` does not work with kubectl.

#### For Secret with Environment Variables (Less Secure):

```bash
kubectl create secret generic aws-credentials \
  --from-literal=AWS_ACCESS_KEY_ID="your_access_key" \
  --from-literal=AWS_SECRET_ACCESS_KEY="your_secret_key" \
  --namespace=axway
```

## Alternative Configuration with IAM Roles

For EKS environments, you can use IAM Roles instead of credentials:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: axway-api-gateway
spec:
  template:
    spec:
      serviceAccountName: axway-gateway-sa
      containers:
      - name: axway-gateway
        image: axway/api-gateway:latest
        # No environment variables - uses IAM Role
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: axway-gateway-sa
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/axway-lambda-role
```

## Monitoring and Troubleshooting

### Logs

The script generates detailed logs that can be monitored:

- `Trace.info()`: Success and configuration information
- `Trace.warning()`: Configuration warnings
- `Trace.error()`: Execution errors

### Supported Environment Variables

- `AWS_ACCESS_KEY_ID`: AWS access key
- `AWS_SECRET_ACCESS_KEY`: AWS secret key
- `AWS_SESSION_TOKEN`: Session token (optional)
- `AWS_DEFAULT_REGION`: Default region
- `AWS_PROFILE`: AWS profile
- `AWS_SHARED_CREDENTIALS_FILE`: Path to credentials file

### Common Troubleshooting

1. **Credentials error**: Check if environment variables are set or if the credentials file exists
2. **Region error**: Check if the region is correct and the function exists
3. **Timeout error**: Increase the value of the `aws.lambda.retry.delay.ms` parameter
4. **Function not found error**: Check the function name and region
5. **Classpath error**: Check if the JARs were added to Policy Studio in **Window > Preferences > Runtime Dependencies**

## Security

- Use IAM Roles whenever possible instead of static credentials
- Rotate credentials regularly
- Use IAM policies with least privilege
- Monitor access and execution logs
- Consider using AWS Secrets Manager for sensitive credentials

## Usage Example

1. Configure the filter in Policy Studio with the required parameters
2. Configure AWS credentials (environment variables, file, or IAM Role)
3. Set message attributes with the desired parameters
4. Test the integration with an HTTP request
5. Monitor logs to verify operation

The integration is ready for use in production environments with appropriate security configurations. 