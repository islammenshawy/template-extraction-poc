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

# Tags
tags = {
  Project     = "Template Extraction"
  ManagedBy   = "Terraform"
  Application = "SWIFT Template Extraction POC"
}
