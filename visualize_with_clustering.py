#!/usr/bin/env python3
"""
SWIFT Message Cluster Visualization with K-Means Clustering
Performs clustering on vectors and visualizes the results
"""

import requests
import json
import numpy as np
from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
from sklearn.cluster import KMeans
import plotly.graph_objects as go
from plotly.subplots import make_subplots

# Configuration
ES_URL = "http://localhost:9200"
API_URL = "http://localhost:8080/api/v2"

def fetch_vectors_from_elasticsearch():
    """Fetch all vector embeddings from ElasticSearch"""
    print("Fetching vectors from ElasticSearch...")

    response = requests.post(
        f"{ES_URL}/vector_embeddings/_search",
        json={
            "size": 1000,
            "query": {"match_all": {}}
        },
        headers={"Content-Type": "application/json"}
    )

    if response.status_code != 200:
        print(f"Error fetching vectors: {response.text}")
        return []

    hits = response.json()["hits"]["hits"]

    vectors = []
    for hit in hits:
        source = hit["_source"]
        vectors.append({
            "id": source["id"],
            "type": source["documentType"],
            "embedding": np.array(source["embedding"]),
            "referenceId": source["referenceId"]
        })

    print(f"✓ Fetched {len(vectors)} vectors")
    return vectors

def fetch_messages_from_api():
    """Fetch message metadata from API"""
    print("Fetching message metadata from API...")

    response = requests.get(f"{API_URL}/messages")
    if response.status_code != 200:
        print(f"Error fetching messages: {response.text}")
        return {}

    messages = response.json()
    message_map = {}
    for msg in messages:
        message_map[msg["id"]] = {
            "messageType": msg["messageType"],
            "senderId": msg["senderId"],
            "receiverId": msg["receiverId"],
            "status": msg["status"]
        }

    print(f"✓ Fetched {len(message_map)} messages")
    return message_map

def perform_clustering(embeddings, n_clusters=None):
    """Perform K-Means clustering on embeddings"""

    if n_clusters is None:
        # Auto-determine optimal clusters (2-10)
        n_samples = len(embeddings)
        n_clusters = min(5, max(2, n_samples // 10))

    print(f"Performing K-Means clustering with {n_clusters} clusters...")

    kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
    cluster_labels = kmeans.fit_predict(embeddings)

    print(f"✓ Created {n_clusters} clusters")

    # Calculate cluster statistics
    unique, counts = np.unique(cluster_labels, return_counts=True)
    for cluster_id, count in zip(unique, counts):
        print(f"  Cluster {cluster_id}: {count} messages")

    return cluster_labels, kmeans.cluster_centers_

def create_cluster_visualization(vectors_data, messages_map, cluster_labels, method="tsne"):
    """Create 2D visualization with clusters"""

    # Prepare embeddings
    embeddings = np.array([v["embedding"] for v in vectors_data])

    # Reduce dimensions
    print(f"Reducing dimensions using {method.upper()}...")
    if method == "tsne":
        reducer = TSNE(n_components=2, random_state=42, perplexity=min(30, len(embeddings) - 1))
    else:
        reducer = PCA(n_components=2, random_state=42)

    reduced = reducer.fit_transform(embeddings)
    print(f"✓ Reduced to 2D space")

    # Prepare plot data
    x = reduced[:, 0]
    y = reduced[:, 1]

    # Create figure
    fig = go.Figure()

    # Get unique clusters
    unique_clusters = np.unique(cluster_labels)
    colors = ['red', 'blue', 'green', 'orange', 'purple', 'cyan', 'magenta', 'yellow']

    # Plot each cluster separately
    for cluster_id in unique_clusters:
        mask = cluster_labels == cluster_id
        cluster_indices = np.where(mask)[0]

        hover_texts = []
        for idx in cluster_indices:
            vec = vectors_data[idx]
            msg = messages_map.get(vec["referenceId"], {})

            hover_text = f"""
            <b>Cluster {cluster_id}</b><br>
            ID: {vec['referenceId'][:12]}...<br>
            Type: {msg.get('messageType', 'N/A')}<br>
            Sender: {msg.get('senderId', 'N/A')}<br>
            Receiver: {msg.get('receiverId', 'N/A')}<br>
            Status: {msg.get('status', 'N/A')}
            """
            hover_texts.append(hover_text)

        fig.add_trace(go.Scatter(
            x=x[mask],
            y=y[mask],
            mode='markers',
            marker=dict(
                size=12,
                color=colors[cluster_id % len(colors)],
                line=dict(width=1, color='white'),
                opacity=0.8
            ),
            name=f'Cluster {cluster_id}',
            hovertext=hover_texts,
            hoverinfo='text'
        ))

    fig.update_layout(
        title=f"SWIFT Message Clusters ({method.upper()} Projection)<br><sub>{len(unique_clusters)} clusters across {len(vectors_data)} messages</sub>",
        xaxis_title=f"{method.upper()} Component 1",
        yaxis_title=f"{method.upper()} Component 2",
        hovermode='closest',
        width=1400,
        height=900,
        template='plotly_white',
        legend=dict(
            yanchor="top",
            y=0.99,
            xanchor="left",
            x=0.01
        )
    )

    return fig

def create_cluster_details(vectors_data, messages_map, cluster_labels):
    """Create detailed cluster statistics"""

    unique_clusters = np.unique(cluster_labels)

    # Prepare data for each cluster
    cluster_stats = []
    for cluster_id in unique_clusters:
        mask = cluster_labels == cluster_id
        cluster_indices = np.where(mask)[0]

        # Count message types in this cluster
        type_counts = {}
        sender_counts = {}
        for idx in cluster_indices:
            vec = vectors_data[idx]
            msg = messages_map.get(vec["referenceId"], {})

            msg_type = msg.get('messageType', 'UNKNOWN')
            type_counts[msg_type] = type_counts.get(msg_type, 0) + 1

            sender = msg.get('senderId', 'UNKNOWN')
            sender_counts[sender] = sender_counts.get(sender, 0) + 1

        cluster_stats.append({
            'cluster_id': cluster_id,
            'size': len(cluster_indices),
            'types': type_counts,
            'senders': sender_counts
        })

    # Create visualization
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=(
            "Cluster Sizes",
            "Message Types per Cluster",
            "Top Senders per Cluster",
            "Cluster Distribution"
        ),
        specs=[
            [{"type": "bar"}, {"type": "bar"}],
            [{"type": "bar"}, {"type": "pie"}]
        ]
    )

    # Cluster sizes
    cluster_ids = [s['cluster_id'] for s in cluster_stats]
    sizes = [s['size'] for s in cluster_stats]

    fig.add_trace(
        go.Bar(
            x=[f"Cluster {cid}" for cid in cluster_ids],
            y=sizes,
            name="Size",
            marker_color='lightblue'
        ),
        row=1, col=1
    )

    # Message types per cluster
    all_types = set()
    for stat in cluster_stats:
        all_types.update(stat['types'].keys())

    for msg_type in all_types:
        counts = [stat['types'].get(msg_type, 0) for stat in cluster_stats]
        fig.add_trace(
            go.Bar(
                x=[f"Cluster {cid}" for cid in cluster_ids],
                y=counts,
                name=msg_type
            ),
            row=1, col=2
        )

    # Top senders
    for cluster_stat in cluster_stats:
        top_senders = sorted(cluster_stat['senders'].items(), key=lambda x: x[1], reverse=True)[:3]
        if top_senders:
            sender_names = [s[0] for s in top_senders]
            sender_counts = [s[1] for s in top_senders]

            fig.add_trace(
                go.Bar(
                    x=sender_names,
                    y=sender_counts,
                    name=f"Cluster {cluster_stat['cluster_id']}",
                    showlegend=False
                ),
                row=2, col=1
            )

    # Cluster distribution pie
    fig.add_trace(
        go.Pie(
            labels=[f"Cluster {cid}" for cid in cluster_ids],
            values=sizes,
            name="Distribution"
        ),
        row=2, col=2
    )

    fig.update_layout(
        title="Cluster Statistics and Distribution",
        height=900,
        width=1400,
        showlegend=True
    )

    return fig

def main():
    print("=" * 70)
    print("SWIFT Message Clustering and Visualization")
    print("=" * 70)
    print()

    # Fetch data
    vectors_data = fetch_vectors_from_elasticsearch()
    if not vectors_data:
        print("❌ No vectors found. Please upload messages first.")
        return

    messages_map = fetch_messages_from_api()

    print()
    print(f"Total vectors: {len(vectors_data)}")
    print(f"Total messages: {len(messages_map)}")
    print()

    # Perform clustering
    embeddings = np.array([v["embedding"] for v in vectors_data])
    cluster_labels, centroids = perform_clustering(embeddings)

    print()

    # Create visualizations
    print("Creating visualizations...")

    # t-SNE cluster view
    fig_tsne = create_cluster_visualization(vectors_data, messages_map, cluster_labels, method="tsne")
    fig_tsne.write_html("clusters_tsne.html")
    print("✓ Saved: clusters_tsne.html")

    # PCA cluster view
    fig_pca = create_cluster_visualization(vectors_data, messages_map, cluster_labels, method="pca")
    fig_pca.write_html("clusters_pca.html")
    print("✓ Saved: clusters_pca.html")

    # Cluster statistics
    fig_stats = create_cluster_details(vectors_data, messages_map, cluster_labels)
    fig_stats.write_html("clusters_statistics.html")
    print("✓ Saved: clusters_statistics.html")

    print()
    print("=" * 70)
    print("✅ Clustering complete!")
    print()
    print("Open these files to see your clusters:")
    print("  1. clusters_tsne.html     - t-SNE projection with color-coded clusters")
    print("  2. clusters_pca.html      - PCA projection with color-coded clusters")
    print("  3. clusters_statistics.html - Detailed cluster statistics")
    print()
    print("To open in browser:")
    print("  open clusters_tsne.html")
    print("=" * 70)

if __name__ == "__main__":
    main()
