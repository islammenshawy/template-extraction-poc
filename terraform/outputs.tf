output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "acr_login_server" {
  description = "Azure Container Registry login server"
  value       = azurerm_container_registry.acr.login_server
}

output "acr_admin_username" {
  description = "Azure Container Registry admin username"
  value       = azurerm_container_registry.acr.admin_username
  sensitive   = true
}

output "acr_admin_password" {
  description = "Azure Container Registry admin password"
  value       = azurerm_container_registry.acr.admin_password
  sensitive   = true
}

output "cosmosdb_connection_string" {
  description = "Cosmos DB MongoDB connection string"
  value       = azurerm_cosmosdb_account.mongodb.connection_strings[0]
  sensitive   = true
}

output "cosmosdb_endpoint" {
  description = "Cosmos DB endpoint"
  value       = azurerm_cosmosdb_account.mongodb.endpoint
}

output "elasticsearch_fqdn" {
  description = "Elasticsearch FQDN"
  value       = var.enable_elasticsearch ? azurerm_container_group.elasticsearch[0].fqdn : "disabled"
}

output "elasticsearch_url" {
  description = "Elasticsearch URL"
  value       = var.enable_elasticsearch ? "http://${azurerm_container_group.elasticsearch[0].fqdn}:${var.elasticsearch_port}" : "disabled"
}

output "backend_fqdn" {
  description = "Backend application FQDN"
  value       = azurerm_container_group.backend.fqdn
}

output "backend_url" {
  description = "Backend application URL"
  value       = "http://${azurerm_container_group.backend.fqdn}:${var.backend_port}"
}

output "frontend_fqdn" {
  description = "Frontend application FQDN"
  value       = azurerm_container_group.frontend.fqdn
}

output "frontend_url" {
  description = "Frontend application URL"
  value       = "http://${azurerm_container_group.frontend.fqdn}"
}

output "deployment_summary" {
  description = "Deployment summary"
  value = {
    environment       = var.environment
    location          = var.location
    frontend_url      = "http://${azurerm_container_group.frontend.fqdn}"
    backend_url       = "http://${azurerm_container_group.backend.fqdn}:${var.backend_port}"
    elasticsearch_url = var.enable_elasticsearch ? "http://${azurerm_container_group.elasticsearch[0].fqdn}:${var.elasticsearch_port}" : "disabled"
  }
}
