output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "acr_login_server" {
  description = "Azure Container Registry login server"
  value       = data.azurerm_container_registry.acr.login_server
}

output "acr_admin_username" {
  description = "Azure Container Registry admin username"
  value       = data.azurerm_container_registry.acr.admin_username
  sensitive   = true
}

output "acr_admin_password" {
  description = "Azure Container Registry admin password"
  value       = data.azurerm_container_registry.acr.admin_password
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

output "backend_fqdn" {
  description = "Backend application FQDN"
  value       = azurerm_container_app.backend.ingress[0].fqdn
}

output "backend_url" {
  description = "Backend application URL"
  value       = "https://${azurerm_container_app.backend.ingress[0].fqdn}"
}

output "frontend_fqdn" {
  description = "Frontend application FQDN"
  value       = azurerm_container_app.frontend.ingress[0].fqdn
}

output "frontend_url" {
  description = "Frontend application URL"
  value       = "https://${azurerm_container_app.frontend.ingress[0].fqdn}"
}

output "deployment_summary" {
  description = "Deployment summary"
  value = {
    environment  = var.environment
    location     = var.location
    frontend_url = "https://${azurerm_container_app.frontend.ingress[0].fqdn}"
    backend_url  = "https://${azurerm_container_app.backend.ingress[0].fqdn}"
  }
}
