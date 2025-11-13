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
  resource_group_name = azurerm_resource_group.main.name
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

# Storage Account for Elasticsearch data persistence
resource "azurerm_storage_account" "elasticsearch" {
  count                    = var.enable_elasticsearch ? 1 : 0
  name                     = "elasticstorage${random_string.suffix.result}"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  tags = merge(var.tags, { Environment = var.environment })
}

resource "azurerm_storage_share" "elasticsearch" {
  count                = var.enable_elasticsearch ? 1 : 0
  name                 = "elasticsearch-data"
  storage_account_name = azurerm_storage_account.elasticsearch[0].name
  quota                = 5 # 5 GB minimal quota
}

# Container Group for Elasticsearch
resource "azurerm_container_group" "elasticsearch" {
  count               = var.enable_elasticsearch ? 1 : 0
  name                = "elasticsearch-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  ip_address_type     = "Public"
  dns_name_label      = "elasticsearch-${var.environment}-${random_string.suffix.result}"
  os_type             = "Linux"

  container {
    name   = "elasticsearch"
    image  = "docker.elastic.co/elasticsearch/elasticsearch:8.11.0"
    cpu    = var.elasticsearch_container_cpu
    memory = var.elasticsearch_container_memory

    ports {
      port     = var.elasticsearch_port
      protocol = "TCP"
    }

    environment_variables = {
      "discovery_type"         = "single-node"
      "xpack_security_enabled" = "false"
      "ES_JAVA_OPTS"           = "-Xms512m -Xmx512m"
    }

    volume {
      name                 = "elasticsearch-data"
      mount_path           = "/usr/share/elasticsearch/data"
      storage_account_name = azurerm_storage_account.elasticsearch[0].name
      storage_account_key  = azurerm_storage_account.elasticsearch[0].primary_access_key
      share_name           = azurerm_storage_share.elasticsearch[0].name
    }
  }

  tags = merge(var.tags, { Environment = var.environment })
}

# Container Group for Backend
resource "azurerm_container_group" "backend" {
  name                = "backend-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  ip_address_type     = "Public"
  dns_name_label      = "backend-${var.environment}-${random_string.suffix.result}"
  os_type             = "Linux"

  container {
    name   = "backend"
    image  = "${azurerm_container_registry.acr.login_server}/template-extraction-backend:${var.backend_image_tag}"
    cpu    = var.backend_container_cpu
    memory = var.backend_container_memory

    ports {
      port     = var.backend_port
      protocol = "TCP"
    }

    environment_variables = {
      "SPRING_DATA_MONGODB_DATABASE"     = var.cosmosdb_database_name
      "SPRING_ELASTICSEARCH_URIS"        = var.enable_elasticsearch ? "http://${azurerm_container_group.elasticsearch[0].fqdn}:${var.elasticsearch_port}" : ""
      "SERVER_PORT"                      = tostring(var.backend_port)
      "CORS_ALLOWED_ORIGINS"             = "*"
    }

    secure_environment_variables = {
      "SPRING_DATA_MONGODB_URI" = azurerm_cosmosdb_account.mongodb.connection_strings[0]
      "ANTHROPIC_API_KEY"       = var.anthropic_api_key
      "JWT_SECRET"              = var.jwt_secret != "" ? var.jwt_secret : random_string.suffix.result
    }

    liveness_probe {
      http_get {
        path   = "/actuator/health"
        port   = var.backend_port
        scheme = "Http"
      }
      initial_delay_seconds = 60
      period_seconds        = 30
      failure_threshold     = 3
      timeout_seconds       = 10
    }

    readiness_probe {
      http_get {
        path   = "/actuator/health/readiness"
        port   = var.backend_port
        scheme = "Http"
      }
      initial_delay_seconds = 30
      period_seconds        = 10
      failure_threshold     = 3
      timeout_seconds       = 5
    }
  }

  image_registry_credential {
    server   = azurerm_container_registry.acr.login_server
    username = azurerm_container_registry.acr.admin_username
    password = azurerm_container_registry.acr.admin_password
  }

  tags = merge(var.tags, { Environment = var.environment })

  depends_on = [
    azurerm_cosmosdb_mongo_collection.swift_messages,
    azurerm_cosmosdb_mongo_collection.templates,
    azurerm_cosmosdb_mongo_collection.transactions
  ]
}

# Container Group for Frontend
resource "azurerm_container_group" "frontend" {
  name                = "frontend-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  ip_address_type     = "Public"
  dns_name_label      = "frontend-${var.environment}-${random_string.suffix.result}"
  os_type             = "Linux"

  container {
    name   = "frontend"
    image  = "${azurerm_container_registry.acr.login_server}/template-extraction-frontend:${var.frontend_image_tag}"
    cpu    = var.frontend_container_cpu
    memory = var.frontend_container_memory

    ports {
      port     = var.frontend_port
      protocol = "TCP"
    }

    environment_variables = {
      "VITE_API_BASE_URL" = "http://${azurerm_container_group.backend.fqdn}:${var.backend_port}/api"
    }
  }

  image_registry_credential {
    server   = azurerm_container_registry.acr.login_server
    username = azurerm_container_registry.acr.admin_username
    password = azurerm_container_registry.acr.admin_password
  }

  tags = merge(var.tags, { Environment = var.environment })
}
