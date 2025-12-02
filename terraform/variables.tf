variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
  default     = "template-extraction-prod-rg"
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "eastus"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default = {
    Project     = "Template Extraction"
    ManagedBy   = "Terraform"
    Application = "SWIFT Template Extraction POC"
  }
}

# Azure Container Registry
variable "acr_name" {
  description = "Name of the Azure Container Registry"
  type        = string
  default     = "" # Will be auto-generated if empty
}

variable "acr_sku" {
  description = "SKU for Azure Container Registry"
  type        = string
  default     = "Basic" # Free tier
}

# Cosmos DB (MongoDB API) - Free Tier
variable "cosmosdb_account_name" {
  description = "Name of the Cosmos DB account"
  type        = string
  default     = "" # Will be auto-generated if empty
}

variable "cosmosdb_database_name" {
  description = "Name of the MongoDB database"
  type        = string
  default     = "swift-templates"
}

variable "cosmosdb_enable_free_tier" {
  description = "Enable Cosmos DB free tier (400 RU/s, 5GB storage)"
  type        = bool
  default     = true
}

variable "cosmosdb_max_throughput" {
  description = "Maximum throughput for Cosmos DB"
  type        = number
  default     = 1000 # Free tier limit
}

# Container Configuration
variable "backend_container_cpu" {
  description = "CPU cores for backend container"
  type        = number
  default     = 1
}

variable "backend_container_memory" {
  description = "Memory (GB) for backend container"
  type        = number
  default     = 2
}

variable "frontend_container_cpu" {
  description = "CPU cores for frontend container"
  type        = number
  default     = 0.5
}

variable "frontend_container_memory" {
  description = "Memory (GB) for frontend container"
  type        = number
  default     = 1
}

variable "elasticsearch_container_cpu" {
  description = "CPU cores for Elasticsearch container"
  type        = number
  default     = 1
}

variable "elasticsearch_container_memory" {
  description = "Memory (GB) for Elasticsearch container"
  type        = number
  default     = 2
}

# Application Configuration
variable "backend_port" {
  description = "Port for backend application"
  type        = number
  default     = 8080
}

variable "frontend_port" {
  description = "Port for frontend application"
  type        = number
  default     = 80
}

variable "elasticsearch_port" {
  description = "Port for Elasticsearch"
  type        = number
  default     = 9200
}

# Docker Images - These will come from ACR
variable "backend_image_tag" {
  description = "Tag for backend Docker image"
  type        = string
  default     = "latest"
}

variable "frontend_image_tag" {
  description = "Tag for frontend Docker image"
  type        = string
  default     = "latest"
}

# Secrets and API Keys
variable "anthropic_api_key" {
  description = "Anthropic (Claude) API key for AI processing"
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "JWT secret for authentication"
  type        = string
  sensitive   = true
  default     = ""
}

# Enable/Disable Features
variable "enable_elasticsearch" {
  description = "Enable Elasticsearch deployment"
  type        = bool
  default     = true
}

variable "enable_custom_domain" {
  description = "Enable custom domain configuration"
  type        = bool
  default     = false
}

variable "custom_domain" {
  description = "Custom domain for the application"
  type        = string
  default     = ""
}

variable "enable_cloudflare_tunnel" {
  description = "Enable Cloudflare Tunnel for secure HTTPS access"
  type        = bool
  default     = false
}

variable "cloudflare_tunnel_token" {
  description = "Cloudflare Tunnel token for named tunnel with custom domain"
  type        = string
  sensitive   = true
  default     = ""
}

# Admin Provisioning Variables
variable "admin_provisioning_enabled" {
  description = "Enable admin user provisioning on startup"
  type        = string
  default     = "false"
}

variable "admin_default_email" {
  description = "Default admin email for initial setup"
  type        = string
  default     = "admin@template-extraction.com"
}

variable "admin_default_password" {
  description = "Default admin password for initial setup"
  type        = string
  sensitive   = true
  default     = "AdminPass123!"
}

variable "admin_provisioning_token" {
  description = "Secret token for admin provisioning endpoint"
  type        = string
  sensitive   = true
  default     = ""
}
