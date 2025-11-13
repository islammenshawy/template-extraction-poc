#!/bin/bash

################################################################################
# Azure Deployment Setup Script
# This script sets up Azure resources and GitHub secrets for CI/CD deployment
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0,31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration Variables
PROJECT_NAME="template-extraction"
LOCATION="eastus"
GITHUB_REPO="islammenshawy/template-extraction-poc"

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

################################################################################
# Step 1: Prerequisites Check
################################################################################
print_info "Checking prerequisites..."

if ! command_exists az; then
    print_error "Azure CLI is not installed. Please install it first."
    exit 1
fi

if ! command_exists gh; then
    print_error "GitHub CLI is not installed. Please install it first."
    exit 1
fi

# Check if logged into Azure
if ! az account show >/dev/null 2>&1; then
    print_error "Not logged into Azure. Please run 'az login' first."
    exit 1
fi

# Check if logged into GitHub
if ! gh auth status >/dev/null 2>&1; then
    print_error "Not logged into GitHub. Please run 'gh auth login' first."
    exit 1
fi

print_info "All prerequisites met!"

################################################################################
# Step 2: Get Azure Subscription Info
################################################################################
print_info "Getting Azure subscription information..."

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)

print_info "Subscription ID: $SUBSCRIPTION_ID"
print_info "Tenant ID: $TENANT_ID"

################################################################################
# Step 3: Create Service Principal for GitHub Actions
################################################################################
print_info "Creating Azure Service Principal for GitHub Actions..."

SP_NAME="${PROJECT_NAME}-github-sp"

# Check if SP already exists
if az ad sp list --display-name "$SP_NAME" --query "[0].appId" -o tsv 2>/dev/null | grep -q .; then
    print_warn "Service Principal '$SP_NAME' already exists. Skipping creation."
    CLIENT_ID=$(az ad sp list --display-name "$SP_NAME" --query "[0].appId" -o tsv)
else
    # Create service principal with Contributor role
    SP_OUTPUT=$(az ad sp create-for-rbac \
        --name "$SP_NAME" \
        --role Contributor \
        --scopes "/subscriptions/$SUBSCRIPTION_ID" \
        --sdk-auth)

    CLIENT_ID=$(echo $SP_OUTPUT | jq -r '.clientId')
    CLIENT_SECRET=$(echo $SP_OUTPUT | jq -r '.clientSecret')

    print_info "Service Principal created successfully!"
fi

# Get client secret if SP was already created (you'll need to reset it)
if [ -z "$CLIENT_SECRET" ]; then
    print_warn "Resetting Service Principal credentials..."
    CLIENT_SECRET=$(az ad sp credential reset --id "$CLIENT_ID" --query password -o tsv)
fi

################################################################################
# Step 4: Create Terraform State Storage
################################################################################
print_info "Creating Terraform state storage resources..."

TF_STATE_RG="terraform-state-rg"
TF_STATE_STORAGE="tfstate$(openssl rand -hex 4)"
TF_STATE_CONTAINER="tfstate"

# Create resource group for Terraform state
if az group exists --name "$TF_STATE_RG" | grep -q "true"; then
    print_warn "Resource group '$TF_STATE_RG' already exists."
else
    az group create --name "$TF_STATE_RG" --location "$LOCATION"
    print_info "Created resource group: $TF_STATE_RG"
fi

# Create storage account
if az storage account show --name "$TF_STATE_STORAGE" --resource-group "$TF_STATE_RG" >/dev/null 2>&1; then
    print_warn "Storage account '$TF_STATE_STORAGE' already exists."
else
    az storage account create \
        --name "$TF_STATE_STORAGE" \
        --resource-group "$TF_STATE_RG" \
        --location "$LOCATION" \
        --sku Standard_LRS \
        --encryption-services blob \
        --min-tls-version TLS1_2

    print_info "Created storage account: $TF_STATE_STORAGE"
fi

# Get storage account key
STORAGE_KEY=$(az storage account keys list \
    --resource-group "$TF_STATE_RG" \
    --account-name "$TF_STATE_STORAGE" \
    --query '[0].value' -o tsv)

# Create container for Terraform state
if az storage container exists \
    --name "$TF_STATE_CONTAINER" \
    --account-name "$TF_STATE_STORAGE" \
    --account-key "$STORAGE_KEY" | grep -q "true"; then
    print_warn "Storage container '$TF_STATE_CONTAINER' already exists."
else
    az storage container create \
        --name "$TF_STATE_CONTAINER" \
        --account-name "$TF_STATE_STORAGE" \
        --account-key "$STORAGE_KEY"

    print_info "Created storage container: $TF_STATE_CONTAINER"
fi

################################################################################
# Step 5: Create Azure Container Registry
################################################################################
print_info "Creating Azure Container Registry..."

ACR_RG="${PROJECT_NAME}-acr-rg"
# Remove dashes from project name for ACR (ACR names must be alphanumeric only)
ACR_NAME="$(echo ${PROJECT_NAME} | tr -d '-')acr$(openssl rand -hex 3)"

# Create resource group for ACR
if az group exists --name "$ACR_RG" | grep -q "true"; then
    print_warn "Resource group '$ACR_RG' already exists."
else
    az group create --name "$ACR_RG" --location "$LOCATION"
    print_info "Created resource group: $ACR_RG"
fi

# Create ACR (Basic SKU - free tier)
if az acr show --name "$ACR_NAME" --resource-group "$ACR_RG" >/dev/null 2>&1; then
    print_warn "Container registry '$ACR_NAME' already exists."
else
    az acr create \
        --name "$ACR_NAME" \
        --resource-group "$ACR_RG" \
        --location "$LOCATION" \
        --sku Basic \
        --admin-enabled true

    print_info "Created Azure Container Registry: $ACR_NAME"
fi

################################################################################
# Step 6: Configure GitHub Secrets
################################################################################
print_info "Configuring GitHub secrets..."

# Set GitHub secrets using gh CLI
gh secret set AZURE_CLIENT_ID --body "$CLIENT_ID" --repo "$GITHUB_REPO"
gh secret set AZURE_CLIENT_SECRET --body "$CLIENT_SECRET" --repo "$GITHUB_REPO"
gh secret set AZURE_SUBSCRIPTION_ID --body "$SUBSCRIPTION_ID" --repo "$GITHUB_REPO"
gh secret set AZURE_TENANT_ID --body "$TENANT_ID" --repo "$GITHUB_REPO"

gh secret set TF_STATE_RESOURCE_GROUP --body "$TF_STATE_RG" --repo "$GITHUB_REPO"
gh secret set TF_STATE_STORAGE_ACCOUNT --body "$TF_STATE_STORAGE" --repo "$GITHUB_REPO"
gh secret set TF_STATE_CONTAINER --body "$TF_STATE_CONTAINER" --repo "$GITHUB_REPO"

gh secret set ACR_NAME --body "$ACR_NAME" --repo "$GITHUB_REPO"

print_info "GitHub secrets configured successfully!"

################################################################################
# Step 7: Prompt for Application Secrets
################################################################################
print_info "Setting application secrets..."

# Anthropic API Key
read -p "Enter your Anthropic API Key (or press Enter to skip): " ANTHROPIC_KEY
if [ -n "$ANTHROPIC_KEY" ]; then
    gh secret set ANTHROPIC_API_KEY --body "$ANTHROPIC_KEY" --repo "$GITHUB_REPO"
    print_info "Anthropic API Key set!"
else
    print_warn "Skipping Anthropic API Key. You can set it later in GitHub Secrets."
fi

# JWT Secret (auto-generate if not provided)
read -p "Enter JWT Secret (or press Enter to auto-generate): " JWT_SECRET
if [ -z "$JWT_SECRET" ]; then
    JWT_SECRET=$(openssl rand -hex 32)
    print_info "Auto-generated JWT Secret"
fi
gh secret set JWT_SECRET --body "$JWT_SECRET" --repo "$GITHUB_REPO"

################################################################################
# Step 8: Create terraform.tfvars
################################################################################
print_info "Creating terraform/terraform.tfvars..."

cat > terraform/terraform.tfvars <<EOF
# Auto-generated terraform.tfvars
resource_group_name = "${PROJECT_NAME}-rg"
location            = "$LOCATION"
environment         = "dev"

# Container Registry
acr_name = "$ACR_NAME"

# Cosmos DB
cosmosdb_account_name     = ""  # Auto-generated
cosmosdb_database_name    = "swift-templates"
cosmosdb_enable_free_tier = true

# Feature Flags
enable_elasticsearch = true

# Tags
tags = {
  Project     = "Template Extraction"
  ManagedBy   = "Terraform"
  Application = "SWIFT Template Extraction POC"
}
EOF

print_info "terraform.tfvars created successfully!"

################################################################################
# Step 9: Summary
################################################################################
echo ""
echo "================================================================================"
print_info "Setup Complete! 🎉"
echo "================================================================================"
echo ""
echo "Azure Resources Created:"
echo "  - Service Principal: $SP_NAME"
echo "  - Terraform State RG: $TF_STATE_RG"
echo "  - Terraform State Storage: $TF_STATE_STORAGE"
echo "  - Container Registry: $ACR_NAME"
echo ""
echo "GitHub Secrets Configured:"
echo "  - AZURE_CLIENT_ID"
echo "  - AZURE_CLIENT_SECRET"
echo "  - AZURE_SUBSCRIPTION_ID"
echo "  - AZURE_TENANT_ID"
echo "  - TF_STATE_RESOURCE_GROUP"
echo "  - TF_STATE_STORAGE_ACCOUNT"
echo "  - TF_STATE_CONTAINER"
echo "  - ACR_NAME"
echo "  - ANTHROPIC_API_KEY (if provided)"
echo "  - JWT_SECRET"
echo ""
echo "Next Steps:"
echo "  1. Review terraform/terraform.tfvars"
echo "  2. Push your code to the main branch to trigger deployment:"
echo "     git add ."
echo "     git commit -m 'Add Azure deployment configuration'"
echo "     git push origin main"
echo "  3. Monitor the GitHub Actions workflow"
echo ""
echo "================================================================================"

# Save credentials to a secure file (for reference)
cat > .azure-credentials.txt <<EOF
# IMPORTANT: Keep this file secure and do not commit to git!
# Add to .gitignore immediately

Azure Subscription ID: $SUBSCRIPTION_ID
Azure Tenant ID: $TENANT_ID
Service Principal Client ID: $CLIENT_ID
Service Principal Client Secret: $CLIENT_SECRET

Terraform State Storage Account: $TF_STATE_STORAGE
Terraform State Container: $TF_STATE_CONTAINER

Azure Container Registry: $ACR_NAME
ACR Login Server: $ACR_NAME.azurecr.io
EOF

print_warn "Credentials saved to .azure-credentials.txt - Keep this file secure!"
print_warn "Make sure .azure-credentials.txt is in your .gitignore"

# Add to .gitignore if it exists
if [ -f .gitignore ]; then
    if ! grep -q ".azure-credentials.txt" .gitignore; then
        echo ".azure-credentials.txt" >> .gitignore
        print_info "Added .azure-credentials.txt to .gitignore"
    fi
fi
