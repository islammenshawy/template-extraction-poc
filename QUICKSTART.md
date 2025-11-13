# Quick Start Guide

Get the SWIFT Template Extraction system running in under 5 minutes!

## Step 1: Start the Application

```bash
docker-compose up --build
```

Wait for all services to start (approximately 2-3 minutes). You'll see:
```
swift-elasticsearch | Cluster health status changed from [YELLOW] to [GREEN]
swift-backend        | Started TemplateExtractionApplication
swift-frontend       | Server running at http://localhost:3000
```

## Step 2: Access the Application

Open your browser and navigate to: **http://localhost:3000**

## Step 3: Upload Sample Messages

1. Click **"Upload"** in the navigation menu
2. Click **"Load Sample"** to load a sample MT700 message
3. Click **"Upload Message"**
4. Repeat this 3-4 times (you can modify sender/receiver IDs slightly to create variations)

## Step 4: Extract Templates

1. Click **"Templates"** in the navigation menu
2. Click **"Extract Templates"** button
3. Wait for the extraction process (10-30 seconds)
4. You'll see a success message with the number of templates created

## Step 5: View Templates

1. Still on the Templates page, you'll see a list of extracted templates
2. Click **"View"** on any template to see:
   - Template content with placeholders
   - Variable fields identified
   - Confidence score
   - Number of messages in the cluster

## Step 6: Process Transactions

1. Upload a few more messages (using the Upload page)
2. Navigate to **"Transactions"**
3. You'll see "Unmatched Messages" section
4. Click **"Match to Template"** for any message
5. View the transaction details to see extracted data and match confidence

## Step 7: View Dashboard

Click **"Dashboard"** to see:
- Total messages, templates, and transactions
- Distribution by message type
- Processing status breakdown
- Transaction status summary

## Common Workflows

### Workflow 1: Bulk Upload
1. Go to Upload page
2. Switch to "Upload File" tab
3. Upload a .txt file containing multiple SWIFT messages
4. All messages are processed automatically

### Workflow 2: Template Management
1. Go to Templates page
2. View all templates
3. Click "View" to see template details
4. Delete templates that aren't useful

### Workflow 3: Transaction Processing
1. Upload new messages
2. Go to Transactions page
3. Match unmatched messages to templates
4. View transaction details
5. Update with user-entered data if needed

## Sample SWIFT MT700 Message

Use this template for testing:

```
{1:F01BANKBEBBAXXX0000000000}{2:I700BANKUS33XXXXN}
:20:LC123456789
:31C:251231
:40A:IRREVOCABLE
:50:APPLICANT NAME
APPLICANT ADDRESS
:59:BENEFICIARY NAME
BENEFICIARY ADDRESS
:32B:USD100000,00
:41A:AVAILABLE WITH ANY BANK
:42C:DRAFTS AT SIGHT
:44A:LOADING ON BOARD
:44C:LATEST DATE OF SHIPMENT 251215
:45A:DESCRIPTION OF GOODS
:46A:DOCUMENTS REQUIRED
:47A:ADDITIONAL CONDITIONS
:71B:CHARGES
```

Modify amounts, dates, and names to create variations for better template extraction.

## Stopping the Application

```bash
docker-compose down
```

To remove all data (ElasticSearch index):
```bash
docker-compose down -v
```

## Troubleshooting

**Services won't start?**
- Check if ports 3000, 8080, 9200 are available
- Ensure Docker has enough memory (8GB recommended)

**ElasticSearch errors?**
- Wait 30 seconds after starting - it takes time to initialize
- Check logs: `docker-compose logs elasticsearch`

**Can't see uploaded messages?**
- Check backend logs: `docker-compose logs backend`
- Verify ElasticSearch is healthy: http://localhost:9200/_cluster/health

**Template extraction fails?**
- Ensure you have at least 2-3 messages uploaded
- Messages should be of the same type (e.g., all MT700)

## Next Steps

- Explore the API documentation in README.md
- Customize the clustering parameters in application.yml
- Add your own SWIFT message samples
- Experiment with different message types
