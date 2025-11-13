# SWIFT Message Cluster Visualization Guide

## Overview

This project includes multiple visualization tools to explore and analyze SWIFT message clusters across all clients using the vector embeddings stored in ElasticSearch.

---

## 🎨 Visualization Options

### 1. **Interactive HTML Visualizations** (Python + Plotly)

Three interactive HTML files are generated that you can open in any web browser:

#### A. **2D t-SNE Cluster View**
- **File**: `cluster_visualization_tsne_2d.html`
- **Description**: 2D projection of 384-dimensional vectors using t-SNE
- **Features**:
  - Interactive scatter plot
  - Hover to see message details (ID, type, sender, receiver, cluster)
  - Stars = Templates, Circles = Messages
  - Color-coded by message type

#### B. **t-SNE vs PCA Comparison**
- **File**: `cluster_visualization_comparison.html`
- **Description**: Side-by-side comparison of t-SNE and PCA projections
- **Features**:
  - Compare different dimensionality reduction methods
  - See how clusters form differently with each algorithm
  - Useful for understanding cluster quality

#### C. **Statistics Dashboard**
- **File**: `cluster_visualization_stats.html`
- **Description**: Comprehensive statistics and distribution charts
- **Features**:
  - Messages by type (pie chart)
  - Messages by cluster (bar chart)
  - Messages by sender (bar chart)
  - Document types distribution

**To view**: Simply open the HTML files in your browser:
```bash
open cluster_visualization_tsne_2d.html
open cluster_visualization_comparison.html
open cluster_visualization_stats.html
```

---

### 2. **Kibana Dashboard** (ElasticSearch Official Tool)

Kibana provides a web-based UI for creating custom dashboards, visualizations, and exploring ElasticSearch data.

**Access**: http://localhost:5601

#### Getting Started with Kibana:

1. **Create Data View**:
   - Navigate to: `Stack Management > Data Views`
   - Click "Create data view"
   - Name: `swift_vectors`
   - Index pattern: `vector_embeddings*`
   - Click "Save"

2. **Explore Data**:
   - Go to `Analytics > Discover`
   - Select the `swift_vectors` data view
   - Explore your 45 vector embeddings

3. **Create Visualizations**:
   - Go to `Analytics > Visualizations`
   - Click "Create visualization"
   - Choose visualization type:
     - **Pie Chart**: Message types distribution
     - **Bar Chart**: Clusters by size
     - **Data Table**: List all vectors
     - **Metric**: Total vectors count

4. **Build a Dashboard**:
   - Go to `Analytics > Dashboard`
   - Click "Create dashboard"
   - Add visualizations you created
   - Save as "SWIFT Message Clusters Dashboard"

#### Sample Kibana Visualizations:

**A. Message Type Distribution (Pie Chart)**
```
Data source: vector_embeddings
Slice by: documentType.keyword
Metrics: Count
```

**B. Clusters Size (Bar Chart)**
```
Data source: vector_embeddings
X-axis: clusterId
Y-axis: Count
```

**C. Vector Embeddings Table**
```
Columns: id, documentType, clusterId, referenceId
Filters: documentType = "MESSAGE"
```

---

## 🔧 Python Visualization Script

### Basic Usage

Generate all visualizations:
```bash
python3 visualize_clusters.py --view all
```

### Command Line Options

```bash
# Generate only 2D scatter plot with t-SNE
python3 visualize_clusters.py --view scatter --method tsne --dimensions 2d

# Generate only 3D visualization with PCA
python3 visualize_clusters.py --view scatter --method pca --dimensions 3d

# Generate comparison view (t-SNE vs PCA)
python3 visualize_clusters.py --view comparison

# Generate statistics dashboard
python3 visualize_clusters.py --view stats

# Custom output file
python3 visualize_clusters.py --view all --output my_clusters.html
```

### Available Options:

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `--method` | tsne, pca | tsne | Dimensionality reduction algorithm |
| `--dimensions` | 2d, 3d | 2d | Number of dimensions for visualization |
| `--view` | scatter, comparison, stats, all | all | Type of visualization to generate |
| `--output` | filename | cluster_visualization.html | Output HTML file name |

### Understanding the Visualizations:

**t-SNE (t-Distributed Stochastic Neighbor Embedding)**:
- Best for: Visualizing clusters and local structure
- Preserves: Local relationships between similar messages
- Use when: You want to see how messages group together

**PCA (Principal Component Analysis)**:
- Best for: Understanding global structure and variance
- Preserves: Global relationships and directions
- Use when: You want to see overall data distribution

**Markers**:
- ⭐ **Stars** = Template centroids
- ⚫ **Circles** = Individual messages

**Colors**: Different colors represent different message types (MT700, MT710)

**Hover Information**:
- Message ID
- Message type (MT700/MT710)
- Sender bank
- Receiver bank
- Processing status
- Cluster ID
- Template ID

---

## 📊 What Each Visualization Shows

### 1. Scatter Plot Visualizations
**What it shows**: How similar messages cluster together in 2D/3D space

**Key Insights**:
- **Tight clusters** = Very similar messages
- **Scattered points** = Diverse messages
- **Distance between points** = Dissimilarity
- **Templates (stars)** = Center of each cluster

**Use cases**:
- Identify similar messages across all clients
- Find outlier messages
- Validate clustering quality
- Understand message diversity

### 2. Comparison View (t-SNE vs PCA)
**What it shows**: Different perspectives of the same data

**Key Insights**:
- t-SNE emphasizes local clusters
- PCA shows global variance
- Compare to validate cluster separation

**Use cases**:
- Verify cluster quality
- Choose best visualization method
- Present to stakeholders

### 3. Statistics Dashboard
**What it shows**: Quantitative breakdown of your data

**Key Insights**:
- Message type distribution
- Cluster sizes
- Top senders/receivers
- Document type breakdown

**Use cases**:
- Reporting and metrics
- Understanding data composition
- Identifying dominant patterns

---

## 🎯 Common Use Cases

### Use Case 1: Find Similar Messages Across All Clients

1. Open `cluster_visualization_tsne_2d.html`
2. Look for tight clusters of points
3. Hover over points to see sender/receiver banks
4. Messages in the same cluster are similar across different clients

### Use Case 2: Identify Outliers

1. Open `cluster_visualization_tsne_2d.html`
2. Look for isolated points far from clusters
3. These are unique/unusual messages
4. Hover to see details

### Use Case 3: Validate Template Quality

1. Extract templates first: `curl -X POST http://localhost:8080/api/templates/extract`
2. Re-run: `python3 visualize_clusters.py --view all`
3. Look for stars (templates) at cluster centers
4. Verify messages cluster around their templates

### Use Case 4: Compare Message Types

1. Open `cluster_visualization_stats.html`
2. View "Messages by Type" pie chart
3. Compare MT700 vs MT710 distribution
4. View "Messages by Cluster" to see type segregation

### Use Case 5: Create Custom Kibana Dashboard

1. Access Kibana: http://localhost:5601
2. Create data view for `vector_embeddings`
3. Build custom visualizations:
   - Pie chart for message types
   - Bar chart for clusters
   - Data table for vectors
4. Combine into dashboard
5. Save and share

---

## 🔄 Workflow

### Step 1: Upload Messages
```bash
python3 load_swift_data.py
```
This generates and uploads 45 SWIFT messages.

### Step 2: Extract Templates
```bash
curl -X POST http://localhost:8080/api/templates/extract
```
This clusters messages and creates templates.

### Step 3: Visualize Clusters
```bash
python3 visualize_clusters.py --view all
```
This generates interactive HTML visualizations.

### Step 4: Explore in Kibana
- Open: http://localhost:5601
- Create data view
- Build custom dashboards

### Step 5: Analyze Results
- Open HTML files in browser
- Identify clusters
- Find similar messages across clients
- Validate template quality

---

## 📈 Advanced Usage

### Generate 3D Visualization

```bash
python3 visualize_clusters.py --view scatter --dimensions 3d --method tsne
```

Then open `cluster_visualization_tsne_3d.html` in your browser.

**3D Benefits**:
- More detailed cluster separation
- Better for large datasets
- Interactive rotation

### Use PCA Instead of t-SNE

```bash
python3 visualize_clusters.py --view scatter --method pca
```

**PCA Benefits**:
- Faster computation
- More stable (deterministic)
- Better for global structure

### Regenerate After Adding More Data

After uploading more messages:
```bash
# Upload more messages
python3 load_swift_data.py

# Re-extract templates
curl -X POST http://localhost:8080/api/templates/extract

# Regenerate visualizations
python3 visualize_clusters.py --view all
```

---

## 🛠️ Troubleshooting

### Issue: Visualizations show all points in one cluster

**Solution**: You need to extract templates first:
```bash
curl -X POST http://localhost:8080/api/templates/extract
```

### Issue: Kibana shows "No data"

**Solution**:
1. Verify ElasticSearch is running: `curl http://localhost:9200/_cluster/health`
2. Check vectors exist: `curl "http://localhost:9200/vector_embeddings/_count"`
3. Create data view in Kibana with pattern `vector_embeddings*`

### Issue: Python script fails with import errors

**Solution**: Install dependencies:
```bash
pip3 install scikit-learn plotly numpy
```

### Issue: HTML files won't open

**Solution**: Files are too large. Use smaller dataset or:
```bash
# Generate only scatter view
python3 visualize_clusters.py --view scatter
```

---

## 📦 Docker Services

All visualization tools connect to these services:

| Service | URL | Purpose |
|---------|-----|---------|
| ElasticSearch | http://localhost:9200 | Vector storage |
| Backend API | http://localhost:8080 | Message metadata |
| Kibana | http://localhost:5601 | Interactive dashboards |
| Frontend | http://localhost:3000 | Web UI |

Check status:
```bash
docker-compose ps
```

---

## 🎓 Understanding Vector Embeddings

### What are Vector Embeddings?
- 384-dimensional numerical representations of SWIFT messages
- Each dimension captures semantic features
- Similar messages have similar vectors

### How Dimensionality Reduction Works:
- **Original**: 384 dimensions (too high to visualize)
- **t-SNE/PCA**: Reduces to 2D or 3D
- **Preserves**: Relationships between points
- **Result**: Visual clustering

### How Clustering Works:
1. Messages converted to 384-dim vectors
2. K-Means groups similar vectors
3. Templates created from cluster centers
4. New messages matched using cosine similarity

---

## 📚 Additional Resources

### Python Libraries:
- **scikit-learn**: Machine learning algorithms (t-SNE, PCA, K-Means)
- **Plotly**: Interactive visualizations
- **NumPy**: Numerical computing

### ElasticSearch/Kibana:
- [Kibana Documentation](https://www.elastic.co/guide/en/kibana/current/index.html)
- [ElasticSearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)

### Algorithms:
- [t-SNE Explained](https://distill.pub/2016/misread-tsne/)
- [PCA Tutorial](https://en.wikipedia.org/wiki/Principal_component_analysis)
- [K-Means Clustering](https://en.wikipedia.org/wiki/K-means_clustering)

---

## 💡 Tips

1. **Start with 2D t-SNE** - Easiest to interpret
2. **Use PCA for validation** - Faster and deterministic
3. **Extract templates first** - Better cluster visualization
4. **Use Kibana for custom needs** - More flexible than static HTML
5. **Color-code by sender** - See cross-client patterns
6. **Look for tight clusters** - Indicates good template quality
7. **Identify outliers** - May need manual review
8. **Compare before/after clustering** - Validate algorithm effectiveness

---

## 🔍 Example Queries

### ElasticSearch Direct Queries:

```bash
# Count vectors by document type
curl -s "http://localhost:9200/vector_embeddings/_search?size=0" \
  -H "Content-Type: application/json" \
  -d '{"aggs": {"types": {"terms": {"field": "documentType.keyword"}}}}' \
  | jq '.aggregations.types.buckets'

# Get all template vectors
curl -s "http://localhost:9200/vector_embeddings/_search" \
  -H "Content-Type: application/json" \
  -d '{"query": {"term": {"documentType.keyword": "TEMPLATE"}}}' \
  | jq '.hits.hits[]._source | {id, clusterId, referenceId}'

# Count vectors by cluster
curl -s "http://localhost:9200/vector_embeddings/_search?size=0" \
  -H "Content-Type: application/json" \
  -d '{"aggs": {"clusters": {"terms": {"field": "clusterId"}}}}' \
  | jq '.aggregations.clusters.buckets'
```

---

## Summary

You now have **three powerful ways** to visualize SWIFT message clusters:

1. **📊 Interactive HTML** - Quick, portable, shareable
2. **📈 Kibana** - Customizable, enterprise-grade dashboards
3. **🐍 Python Script** - Flexible, reproducible, automated

All three access the same vector embeddings stored in ElasticSearch, giving you complete visibility into how messages cluster across all clients.

**Happy Visualizing! 🚀**
