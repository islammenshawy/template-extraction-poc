# Auto-generated terraform.tfvars
resource_group_name = "template-extraction-prod-rg"
location            = "eastus"
environment         = "prod"

# Container Registry
acr_name = "templateextractionacrbd4265"

# Cosmos DB
cosmosdb_account_name     = ""  # Auto-generated
cosmosdb_database_name    = "swift-templates"
cosmosdb_enable_free_tier = true

# Feature Flags
enable_elasticsearch = false
enable_cloudflare_tunnel = true

# Admin Provisioning
admin_provisioning_enabled = "true"
admin_default_email = "admin@template-extraction.com"
admin_default_password = "AdminPass123!"
admin_provisioning_token = ""  # Will be set via GitHub secret

# One-Time Setup
one_time_setup_enabled = "true"

# Authentication
auth_enabled = "false"

# Tags
tags = {
  Project     = "Template Extraction"
  ManagedBy   = "Terraform"
  Application = "SWIFT Template Extraction POC"
}
