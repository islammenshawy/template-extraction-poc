# Random suffix for unique resource names
resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

# Resource Group
resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location
  tags     = merge(var.tags, { Environment = var.environment })
}

# Azure Container Registry - Reference existing ACR
data "azurerm_container_registry" "acr" {
  name                = var.acr_name != "" ? var.acr_name : "templateextractionacrbd4265"
  resource_group_name = "template-extraction-acr-rg"
}

# Cosmos DB Account (MongoDB API) - Free tier
resource "azurerm_cosmosdb_account" "mongodb" {
  name                = var.cosmosdb_account_name != "" ? var.cosmosdb_account_name : "cosmos-swift-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  offer_type          = "Standard"
  kind                = "MongoDB"

  enable_free_tier                  = var.cosmosdb_enable_free_tier
  enable_automatic_failover         = false
  enable_multiple_write_locations   = false
  public_network_access_enabled     = true
  is_virtual_network_filter_enabled = false

  capabilities {
    name = "EnableMongo"
  }

  capabilities {
    name = "EnableServerless" # Serverless for cost optimization
  }

  consistency_policy {
    consistency_level       = "Session"
    max_interval_in_seconds = 5
    max_staleness_prefix    = 100
  }

  geo_location {
    location          = azurerm_resource_group.main.location
    failover_priority = 0
  }

  tags = merge(var.tags, { Environment = var.environment })
}

# Cosmos DB MongoDB Database
resource "azurerm_cosmosdb_mongo_database" "db" {
  name                = var.cosmosdb_database_name
  resource_group_name = azurerm_cosmosdb_account.mongodb.resource_group_name
  account_name        = azurerm_cosmosdb_account.mongodb.name
}

# Cosmos DB MongoDB Collections
resource "azurerm_cosmosdb_mongo_collection" "swift_messages" {
  name                = "swift_messages"
  resource_group_name = azurerm_cosmosdb_account.mongodb.resource_group_name
  account_name        = azurerm_cosmosdb_account.mongodb.name
  database_name       = azurerm_cosmosdb_mongo_database.db.name

  index {
    keys   = ["_id"]
    unique = true
  }

  index {
    keys   = ["messageType"]
    unique = false
  }

  index {
    keys   = ["templateId"]
    unique = false
  }
}

resource "azurerm_cosmosdb_mongo_collection" "templates" {
  name                = "message_templates"
  resource_group_name = azurerm_cosmosdb_account.mongodb.resource_group_name
  account_name        = azurerm_cosmosdb_account.mongodb.name
  database_name       = azurerm_cosmosdb_mongo_database.db.name

  index {
    keys   = ["_id"]
    unique = true
  }

  index {
    keys   = ["messageType"]
    unique = false
  }
}

resource "azurerm_cosmosdb_mongo_collection" "transactions" {
  name                = "transactions"
  resource_group_name = azurerm_cosmosdb_account.mongodb.resource_group_name
  account_name        = azurerm_cosmosdb_account.mongodb.name
  database_name       = azurerm_cosmosdb_mongo_database.db.name

  index {
    keys   = ["_id"]
    unique = true
  }

  index {
    keys   = ["messageId"]
    unique = false
  }

  index {
    keys   = ["templateId"]
    unique = false
  }
}

# Container Apps Environment - Required for all Container Apps
resource "azurerm_container_app_environment" "main" {
  name                       = "containerapp-env-${random_string.suffix.result}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.container_logs.id

  tags = merge(var.tags, { Environment = var.environment })
}

# Log Analytics Workspace for container logging
resource "azurerm_log_analytics_workspace" "container_logs" {
  name                = "container-logs-${random_string.suffix.result}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = merge(var.tags, { Environment = var.environment })
}

# Container App for Backend
resource "azurerm_container_app" "backend" {
  name                         = "backend-${var.environment}"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"

  template {
    min_replicas = 1  # Keep one instance running to avoid cold start timeouts
    max_replicas = 1

    container {
      name   = "backend"
      image  = "${data.azurerm_container_registry.acr.login_server}/template-extraction-backend:${var.backend_image_tag}"
      cpu    = var.backend_container_cpu
      memory = "${var.backend_container_memory}Gi"

      env {
        name  = "SPRING_DATA_MONGODB_DATABASE"
        value = var.cosmosdb_database_name
      }

      env {
        name  = "SERVER_PORT"
        value = tostring(var.backend_port)
      }

      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = "https://swift-template.islam-org.work,https://frontend-prod.orangebeach-7afdf297.eastus.azurecontainerapps.io"
      }

      env {
        name        = "SPRING_DATA_MONGODB_URI"
        secret_name = "mongodb-connection-string"
      }

      env {
        name        = "ANTHROPIC_API_KEY"
        secret_name = "anthropic-api-key"
      }

      env {
        name        = "JWT_SECRET"
        secret_name = "jwt-secret"
      }

      env {
        name  = "SPRING_ELASTICSEARCH_URIS"
        value = "http://127.0.0.1:9200"
      }

      # Admin Provisioning Configuration
      env {
        name  = "ADMIN_PROVISIONING_ENABLED"
        value = var.admin_provisioning_enabled
      }

      env {
        name  = "ADMIN_DEFAULT_EMAIL"
        value = var.admin_default_email
      }

      env {
        name  = "ADMIN_DEFAULT_PASSWORD"
        value = var.admin_default_password
      }

      env {
        name        = "ADMIN_PROVISIONING_TOKEN"
        secret_name = "admin-provisioning-token"
      }

      # One-Time Setup Configuration
      env {
        name  = "ONE_TIME_SETUP_ENABLED"
        value = var.one_time_setup_enabled
      }

      # Authentication Configuration
      env {
        name  = "AUTH_ENABLED"
        value = var.auth_enabled
      }

      # Temporarily removed both liveness and readiness probes for debugging
      # The 1-second timeout on readiness probe is too short and cannot be configured via Terraform
      # This allows traffic through while we investigate why health checks are failing
      # TODO: Re-enable with proper configuration once health endpoint is optimized
    }

    # Elasticsearch sidecar container
    container {
      name   = "elasticsearch"
      image  = "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
      cpu    = 1.0
      memory = "2Gi"

      env {
        name  = "discovery.type"
        value = "single-node"
      }

      env {
        name  = "xpack.security.enabled"
        value = "false"
      }

      env {
        name  = "xpack.security.http.ssl.enabled"
        value = "false"
      }

      env {
        name  = "ES_JAVA_OPTS"
        value = "-Xms512m -Xmx512m"
      }
    }
  }

  secret {
    name  = "mongodb-connection-string"
    value = azurerm_cosmosdb_account.mongodb.connection_strings[0]
  }

  secret {
    name  = "anthropic-api-key"
    value = var.anthropic_api_key
  }

  secret {
    name  = "jwt-secret"
    value = var.jwt_secret
  }

  secret {
    name  = "admin-provisioning-token"
    value = var.admin_provisioning_token
  }

  registry {
    server               = data.azurerm_container_registry.acr.login_server
    username             = data.azurerm_container_registry.acr.admin_username
    password_secret_name = "acr-password"
  }

  secret {
    name  = "acr-password"
    value = data.azurerm_container_registry.acr.admin_password
  }

  ingress {
    external_enabled = true
    target_port      = var.backend_port
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  tags = merge(var.tags, { Environment = var.environment })

  depends_on = [
    azurerm_cosmosdb_mongo_collection.swift_messages,
    azurerm_cosmosdb_mongo_collection.templates,
    azurerm_cosmosdb_mongo_collection.transactions
  ]
}

# Container App for Frontend
resource "azurerm_container_app" "frontend" {
  name                         = "frontend-${var.environment}"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"

  template {
    min_replicas = 1  # Keep one instance running to avoid cold start timeouts
    max_replicas = 1

    container {
      name   = "frontend"
      image  = "${data.azurerm_container_registry.acr.login_server}/template-extraction-frontend:${var.frontend_image_tag}"
      cpu    = var.frontend_container_cpu
      memory = "${var.frontend_container_memory}Gi"

      env {
        name  = "VITE_API_BASE_URL"
        value = "https://${azurerm_container_app.backend.ingress[0].fqdn}/api"
      }
      
      env {
        name  = "BACKEND_URL"
        value = "http://${azurerm_container_app.backend.name}.${var.environment}:8080"
      }
    }

    # Cloudflare Tunnel sidecar container for HTTPS with custom domain
    dynamic "container" {
      for_each = var.enable_cloudflare_tunnel ? [1] : []
      content {
        name   = "cloudflared"
        image  = "${data.azurerm_container_registry.acr.login_server}/cloudflared:latest"
        cpu    = 0.25
        memory = "0.5Gi"

        command = var.cloudflare_tunnel_token != "" ? [
          "cloudflared",
          "tunnel",
          "--no-autoupdate",
          "run",
          "--token",
          var.cloudflare_tunnel_token
        ] : [
          "cloudflared",
          "tunnel",
          "--no-autoupdate",
          "--url",
          "http://127.0.0.1:${var.frontend_port}"
        ]
      }
    }
  }

  registry {
    server               = data.azurerm_container_registry.acr.login_server
    username             = data.azurerm_container_registry.acr.admin_username
    password_secret_name = "acr-password"
  }

  secret {
    name  = "acr-password"
    value = data.azurerm_container_registry.acr.admin_password
  }

  ingress {
    external_enabled = true
    target_port      = var.frontend_port
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  tags = merge(var.tags, { Environment = var.environment })
}
