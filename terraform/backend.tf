terraform {
  backend "azurerm" {
    # Backend configuration will be provided via backend config file or command line
    # resource_group_name  = "terraform-state-rg"
    # storage_account_name = "tfstate<unique-id>"
    # container_name       = "tfstate"
    # key                  = "template-extraction.tfstate"
  }
}
