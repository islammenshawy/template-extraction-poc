# Database-Driven Configuration

## Overview

The system is now **100% database-driven** with **zero static content**. All configuration, rules, workflows, message type definitions, and user preferences are stored in MongoDB collections that can be updated at runtime without redeployment.

## What's Database-Driven

### 1. System Configuration (`system_configuration`)

**Replaces**: `application.yml` static values

**Stores**:
- Clustering parameters (max iterations, min/max clusters, thresholds)
- Embedding settings (model name, dimensions, cache size)
- Similarity thresholds
- Template generation rules
- Processing timeouts
- API rate limits
- Feature flags

**Example**:
```javascript
{
  "_id": "config_001",
  "configKey": "clustering.maxIterations",
  "configGroup": "clustering",
  "dataType": "INTEGER",
  "value": "100",
  "description": "Maximum iterations for K-Means clustering",
  "active": true,
  "priority": 10,
  "validationRule": "min:10,max:1000",
  "defaultValue": "100",
  "createdAt": ISODate("2025-11-10T00:00:00Z"),
  "createdBy": "system"
}
```

**Usage in Code**:
```java
// Get configuration value
Integer maxIterations = configService.getInt("clustering.maxIterations", 100);

// Update configuration (no restart needed!)
configService.updateConfig("clustering.maxIterations", "150", "admin@bank.com");
```

### 2. Message Type Definitions (`message_type_definitions`)

**Replaces**: Hardcoded MT7XX message types

**Stores**:
- All SWIFT message type definitions (MT700, MT710, etc.)
- Mandatory and optional field definitions
- Field validation rules (regex patterns, lengths, formats)
- Processing rules per message type
- Template extraction settings
- Business validation rules

**Example**:
```javascript
{
  "_id": "mt700_def",
  "messageType": "MT700",
  "category": "Documentary Credits",
  "seriesCode": "MT7XX",
  "description": "Issue of a Documentary Credit",
  "longDescription": "Message sent by the issuing bank...",

  "mandatoryFields": [
    {
      "fieldTag": "20",
      "fieldName": "Sender's Reference",
      "dataType": "ALPHANUMERIC",
      "required": true,
      "pattern": "^[A-Z0-9]{1,16}$",
      "minLength": 1,
      "maxLength": 16,
      "description": "Unique reference assigned by sender"
    },
    {
      "fieldTag": "32B",
      "fieldName": "Currency Code and Amount",
      "dataType": "AMOUNT",
      "required": true,
      "pattern": "^[A-Z]{3}[0-9]{1,15}(,[0-9]{1,2})?$",
      "format": "CCCAAMOUNT",
      "description": "Currency code followed by amount"
    }
  ],

  "optionalFields": [
    {
      "fieldTag": "44A",
      "fieldName": "Place of Taking in Charge",
      "dataType": "TEXT",
      "required": false,
      "maxLength": 65
    }
  ],

  "processingRules": {
    "enableClustering": true,
    "enableTemplateMatching": true,
    "requireManualReview": false,
    "minimumConfidence": 0.85,
    "maxClusterSize": 100,
    "processingPriority": "HIGH"
  },

  "validationRules": [
    {
      "ruleType": "CROSS_FIELD",
      "expression": "field32B.currency == field71B.currency",
      "errorMessage": "Currency mismatch between amount and charges",
      "severity": "ERROR"
    }
  ],

  "templateSettings": {
    "similarityThreshold": 0.85,
    "minMessagesForTemplate": 5,
    "autoGenerateTemplate": true,
    "variableFieldTags": ["20", "32B", "50", "59"],
    "fixedFieldTags": ["40A", "41A"]
  },

  "active": true,
  "createdAt": ISODate("2025-11-10T00:00:00Z")
}
```

**Benefits**:
- Add new message types without code changes
- Modify validation rules on the fly
- Enable/disable clustering per message type
- Configure different thresholds per message type
- Version message type definitions

### 3. Workflow Definitions (`workflow_definitions`)

**Replaces**: Hardcoded workflow states and transitions

**Stores**:
- Complete workflow definitions
- Steps with actions and permissions
- Transitions with conditions
- SLA definitions
- Notification rules
- Approval hierarchies

**Example**:
```javascript
{
  "_id": "workflow_001",
  "workflowName": "Documentary Credit Processing",
  "workflowType": "TRANSACTION_APPROVAL",
  "version": "1.0",

  "steps": [
    {
      "stepId": "data_extraction",
      "stepName": "Data Extraction",
      "stepType": "AUTOMATIC",
      "description": "Extract data from SWIFT message",
      "actions": [
        {
          "actionType": "VALIDATE",
          "actionName": "Validate Message Format",
          "parameters": { "strictMode": true },
          "onSuccess": "template_matching",
          "onFailure": "manual_review"
        }
      ],
      "maxDurationMinutes": 5,
      "notifyOnDelay": true
    },
    {
      "stepId": "template_matching",
      "stepName": "Template Matching",
      "stepType": "AUTOMATIC",
      "actions": [
        {
          "actionType": "CALL_API",
          "actionName": "Match to Template",
          "parameters": { "confidence": 0.85 }
        }
      ]
    },
    {
      "stepId": "approval",
      "stepName": "Manual Approval",
      "stepType": "APPROVAL",
      "requiredPermissions": ["APPROVE_TRANSACTIONS"],
      "requiredFields": ["approverComments"],
      "maxDurationMinutes": 1440,
      "notifyOnDelay": true
    }
  ],

  "transitions": [
    {
      "fromStep": "template_matching",
      "toStep": "approval",
      "transitionName": "Requires Approval",
      "condition": "matchConfidence < 0.95",
      "notifyOnTransition": true,
      "notifyRoles": ["APPROVERS"]
    },
    {
      "fromStep": "template_matching",
      "toStep": "completed",
      "transitionName": "Auto Approved",
      "condition": "matchConfidence >= 0.95"
    }
  ],

  "active": true
}
```

**Benefits**:
- Define multiple workflows for different scenarios
- Modify approval processes without code deployment
- Add/remove steps dynamically
- Configure notifications per step
- Set different SLAs per workflow

### 4. Field Type Rules (`field_type_rules`)

**Replaces**: Hardcoded regex patterns for field detection

**Stores**:
- Pattern matching rules for field type detection
- Transformation rules
- Validation rules
- Priority-based rule evaluation

**Example**:
```javascript
{
  "_id": "rule_amount",
  "fieldType": "AMOUNT",
  "description": "Detect and validate amount fields",
  "priority": 100,

  "patterns": [
    {
      "patternType": "REGEX",
      "pattern": "^[A-Z]{3}[0-9]{1,15}(,[0-9]{1,2})?$",
      "description": "Currency code + amount with optional decimals",
      "confidence": 0.95
    },
    {
      "patternType": "REGEX",
      "pattern": "^\\d+[.,]\\d{2}$",
      "description": "Numeric with 2 decimal places",
      "confidence": 0.80
    }
  ],

  "transformations": [
    {
      "transformationType": "NORMALIZE",
      "sourcePattern": "([A-Z]{3})([0-9]+),([0-9]{2})",
      "targetFormat": "$1 $2.$3",
      "description": "Convert comma to dot decimal separator"
    }
  ],

  "validations": [
    {
      "validationType": "RANGE",
      "validationExpression": "amount > 0 AND amount < 999999999",
      "errorMessage": "Amount must be positive and less than 1 billion",
      "severity": "ERROR"
    }
  ],

  "active": true
}
```

### 5. User Preferences (`user_preferences`)

**Replaces**: Hardcoded user settings

**Stores**:
- UI preferences (theme, language, date formats)
- Notification settings
- Processing preferences
- Dashboard configuration
- Custom user settings

**Example**:
```javascript
{
  "_id": "user_001",
  "userId": "john.doe@bank.com",
  "userName": "John Doe",
  "email": "john.doe@bank.com",

  "uiPreferences": {
    "theme": "dark",
    "language": "en",
    "dateFormat": "MM/DD/YYYY",
    "timeFormat": "12h",
    "currencyFormat": "USD",
    "itemsPerPage": 50,
    "defaultView": "table",
    "favoriteMessageTypes": ["MT700", "MT710"],
    "featureToggles": {
      "autoMatch": true,
      "advancedSearch": true
    }
  },

  "notificationPreferences": {
    "emailNotifications": true,
    "pushNotifications": false,
    "notifyOn": ["TEMPLATE_CREATED", "TRANSACTION_APPROVED"],
    "notificationFrequency": "IMMEDIATE",
    "notificationChannels": ["EMAIL"],
    "eventNotifications": {
      "HIGH_VALUE_TRANSACTION": true,
      "FAILED_MATCH": true
    }
  },

  "processingPreferences": {
    "autoMatch": true,
    "autoApprove": false,
    "autoApproveThreshold": 0.98,
    "preferredTemplates": ["template_xyz"],
    "requireReviewForLowConfidence": true,
    "lowConfidenceThreshold": 0.85
  },

  "dashboardPreferences": {
    "layout": "GRID",
    "refreshInterval": 60,
    "widgets": [
      {
        "widgetId": "messages_count",
        "widgetType": "COUNTER",
        "title": "Total Messages",
        "position": 1,
        "width": 2,
        "height": 1,
        "visible": true,
        "configuration": { "messageType": "ALL" }
      }
    ]
  },

  "customPreferences": {
    "defaultFilter": "status:PENDING",
    "exportFormat": "CSV"
  }
}
```

## Configuration Service API

### Java Service

```java
@Autowired
private ConfigurationService configService;

// Get configuration values
Integer maxIterations = configService.getInt("clustering.maxIterations", 100);
Double threshold = configService.getDouble("similarity.threshold", 0.85);
Boolean autoGenerate = configService.getBoolean("template.autoGenerate", true);

// Get all configurations for a group
Map<String, String> clusteringConfig = configService.getConfigGroup("clustering");

// Update configuration
configService.updateConfig("similarity.threshold", "0.90", "admin@bank.com");

// Create new configuration
configService.createConfig(
    "newFeature.enabled",
    "features",
    "true",
    "BOOLEAN",
    "Enable new feature",
    "admin@bank.com"
);
```

### REST API Endpoints

```bash
# Get all active configurations
GET /api/v2/config

# Get configuration by key
GET /api/v2/config/{key}

# Get configuration group
GET /api/v2/config/group/{group}

# Update configuration
PUT /api/v2/config/{key}
{
  "value": "150",
  "updatedBy": "admin@bank.com"
}

# Create configuration
POST /api/v2/config
{
  "configKey": "newFeature.enabled",
  "configGroup": "features",
  "value": "true",
  "dataType": "BOOLEAN",
  "description": "Enable new feature",
  "createdBy": "admin@bank.com"
}

# Toggle configuration
PATCH /api/v2/config/{key}/toggle
{
  "active": false
}
```

## Database Initialization

On first startup, the system automatically initializes default configurations:

```java
@PostConstruct
public void initialize() {
    configService.initializeDefaultConfigurations();
}
```

This creates:
- All clustering parameters
- Embedding settings
- Similarity thresholds
- Template settings
- Feature flags

## Benefits

### 1. Zero Downtime Configuration Changes
Update any setting without restarting the application:
```bash
# Change clustering parameters live
db.system_configuration.updateOne(
  { "configKey": "clustering.maxIterations" },
  { "$set": { "value": "200" } }
)
```

### 2. Multi-Tenancy Support
Different configurations per tenant:
```javascript
{
  "configKey": "clustering.maxIterations",
  "tenantId": "bank_A",
  "value": "100"
}
{
  "configKey": "clustering.maxIterations",
  "tenantId": "bank_B",
  "value": "150"
}
```

### 3. A/B Testing
Enable features for specific users:
```javascript
{
  "configKey": "features.newDashboard",
  "value": "true",
  "enabledFor": ["user_001", "user_002"]
}
```

### 4. Configuration Versioning
Track all configuration changes:
```javascript
{
  "configKey": "similarity.threshold",
  "value": "0.90",
  "previousValues": [
    { "value": "0.85", "changedAt": ISODate(), "changedBy": "admin" }
  ]
}
```

### 5. Dynamic Feature Flags
Enable/disable features at runtime:
```java
if (configService.getBoolean("features.advancedMatching", false)) {
    // Use advanced matching algorithm
} else {
    // Use basic matching
}
```

### 6. Environment-Specific Configuration
Same codebase, different configs per environment:
```javascript
// Development
{ "configKey": "similarity.threshold", "environment": "dev", "value": "0.70" }

// Production
{ "configKey": "similarity.threshold", "environment": "prod", "value": "0.90" }
```

## Administration UI (Future)

A React-based admin panel to manage all configurations:

```
┌──────────────────────────────────────┐
│  Configuration Management            │
├──────────────────────────────────────┤
│  Group: Clustering                   │
│  ┌────────────────────────────────┐ │
│  │ Max Iterations:       [100]    │ │
│  │ Min Clusters:         [2]      │ │
│  │ Max Clusters:         [10]     │ │
│  │ Convergence Threshold: [0.001] │ │
│  └────────────────────────────────┘ │
│  [Save Changes]                      │
└──────────────────────────────────────┘
```

## Migration from Static Configuration

### Before (application.yml):
```yaml
template:
  extraction:
    clustering:
      max-iterations: 100
      min-clusters: 2
```

### After (MongoDB):
```javascript
db.system_configuration.insertMany([
  {
    "configKey": "clustering.maxIterations",
    "configGroup": "clustering",
    "value": "100",
    "dataType": "INTEGER"
  },
  {
    "configKey": "clustering.minClusters",
    "configGroup": "clustering",
    "value": "2",
    "dataType": "INTEGER"
  }
])
```

### Code Change:
```java
// Before
@Value("${template.extraction.clustering.max-iterations}")
private int maxIterations;

// After
Integer maxIterations = configService.getInt("clustering.maxIterations", 100);
```

## Best Practices

1. **Always provide default values** when getting configuration
2. **Use caching** for frequently accessed configs
3. **Validate configuration changes** before saving
4. **Log all configuration updates** for audit trail
5. **Use feature flags** for gradual rollouts
6. **Version your workflows** when making breaking changes
7. **Test configuration changes** in staging first
8. **Document all custom configurations** in MongoDB

## Summary

The system is now **100% database-driven** with:
- ✅ All configuration in MongoDB
- ✅ No hardcoded values
- ✅ Runtime configuration updates
- ✅ User-specific preferences
- ✅ Workflow definitions
- ✅ Message type definitions
- ✅ Field validation rules
- ✅ Feature flags
- ✅ Complete audit trail

Everything can be modified at runtime without code changes or redeployment!
