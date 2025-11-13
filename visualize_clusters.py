#!/usr/bin/env python3
"""
SWIFT Message Cluster Visualization
Visualizes vector embeddings and clusters using dimensionality reduction
"""

import requests
import json
import numpy as np
from sklearn.manifold import TSNE
from sklearn.decomposition import PCA
import plotly.graph_objects as go
import plotly.express as px
from plotly.subplots import make_subplots
import argparse

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
            "clusterId": source.get("clusterId"),
            "referenceId": source["referenceId"],
            "preview": source.get("contentPreview", "")[:50]
        })

    print(f"Fetched {len(vectors)} vectors")
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
            "status": msg["status"],
            "templateId": msg.get("templateId"),
            "clusterId": msg.get("clusterId")
        }

    print(f"Fetched {len(message_map)} messages")
    return message_map

def reduce_dimensions(embeddings, method="tsne", n_components=2):
    """Reduce embeddings to 2D or 3D using t-SNE or PCA"""
    print(f"Reducing dimensions using {method.upper()}...")

    if method == "tsne":
        reducer = TSNE(
            n_components=n_components,
            random_state=42,
            perplexity=min(30, len(embeddings) - 1),
            n_iter=1000
        )
    elif method == "pca":
        reducer = PCA(n_components=n_components, random_state=42)
    else:
        raise ValueError(f"Unknown method: {method}")

    reduced = reducer.fit_transform(embeddings)
    print(f"Reduced to {n_components}D space")
    return reduced

def create_2d_visualization(vectors_data, messages_map, method="tsne"):
    """Create interactive 2D scatter plot"""

    # Prepare data
    embeddings = np.array([v["embedding"] for v in vectors_data])
    reduced = reduce_dimensions(embeddings, method=method, n_components=2)

    # Prepare plot data
    x = reduced[:, 0]
    y = reduced[:, 1]

    colors = []
    hover_texts = []
    symbols = []
    sizes = []

    for i, vec in enumerate(vectors_data):
        msg_id = vec["referenceId"]
        msg = messages_map.get(msg_id, {})

        # Color by message type
        msg_type = msg.get("messageType", "UNKNOWN")
        colors.append(msg_type)

        # Symbol by document type (message vs template)
        if vec["type"] == "TEMPLATE":
            symbols.append("star")
            sizes.append(20)
        else:
            symbols.append("circle")
            sizes.append(10)

        # Hover text
        hover_text = f"""
        <b>ID:</b> {msg_id[:12]}...<br>
        <b>Type:</b> {msg_type}<br>
        <b>Sender:</b> {msg.get("senderId", "N/A")}<br>
        <b>Receiver:</b> {msg.get("receiverId", "N/A")}<br>
        <b>Status:</b> {msg.get("status", "N/A")}<br>
        <b>Cluster:</b> {vec.get("clusterId", "None")}<br>
        <b>Template:</b> {msg.get("templateId", "None")[:12] if msg.get("templateId") else "None"}
        """
        hover_texts.append(hover_text)

    # Create figure
    fig = go.Figure()

    # Add scatter plot
    fig.add_trace(go.Scatter(
        x=x,
        y=y,
        mode='markers',
        marker=dict(
            size=sizes,
            color=[hash(c) % 360 for c in colors],  # Color by type
            colorscale='Viridis',
            symbol=symbols,
            line=dict(width=1, color='white')
        ),
        text=colors,
        hovertext=hover_texts,
        hoverinfo='text',
        name='Messages'
    ))

    fig.update_layout(
        title=f"SWIFT Message Clusters Visualization ({method.upper()})",
        xaxis_title=f"{method.upper()} Component 1",
        yaxis_title=f"{method.upper()} Component 2",
        hovermode='closest',
        width=1200,
        height=800,
        template='plotly_white'
    )

    return fig

def create_3d_visualization(vectors_data, messages_map, method="tsne"):
    """Create interactive 3D scatter plot"""

    # Prepare data
    embeddings = np.array([v["embedding"] for v in vectors_data])
    reduced = reduce_dimensions(embeddings, method=method, n_components=3)

    # Prepare plot data
    x = reduced[:, 0]
    y = reduced[:, 1]
    z = reduced[:, 2]

    colors = []
    hover_texts = []
    symbols = []
    sizes = []

    for i, vec in enumerate(vectors_data):
        msg_id = vec["referenceId"]
        msg = messages_map.get(msg_id, {})

        # Color by message type
        msg_type = msg.get("messageType", "UNKNOWN")
        colors.append(msg_type)

        # Size by document type
        if vec["type"] == "TEMPLATE":
            sizes.append(15)
            symbols.append("diamond")
        else:
            sizes.append(8)
            symbols.append("circle")

        # Hover text
        hover_text = f"""
        ID: {msg_id[:12]}...<br>
        Type: {msg_type}<br>
        Sender: {msg.get("senderId", "N/A")}<br>
        Receiver: {msg.get("receiverId", "N/A")}<br>
        Status: {msg.get("status", "N/A")}<br>
        Cluster: {vec.get("clusterId", "None")}<br>
        Template: {msg.get("templateId", "None")[:12] if msg.get("templateId") else "None"}
        """
        hover_texts.append(hover_text)

    # Create figure
    fig = go.Figure()

    fig.add_trace(go.Scatter3d(
        x=x,
        y=y,
        z=z,
        mode='markers',
        marker=dict(
            size=sizes,
            color=[hash(c) % 360 for c in colors],
            colorscale='Viridis',
            symbol=symbols,
            line=dict(width=0.5, color='white')
        ),
        text=colors,
        hovertext=hover_texts,
        hoverinfo='text',
        name='Messages'
    ))

    fig.update_layout(
        title=f"SWIFT Message Clusters 3D Visualization ({method.upper()})",
        scene=dict(
            xaxis_title=f"{method.upper()} Component 1",
            yaxis_title=f"{method.upper()} Component 2",
            zaxis_title=f"{method.upper()} Component 3"
        ),
        width=1200,
        height=900,
        template='plotly_white'
    )

    return fig

def create_cluster_comparison(vectors_data, messages_map):
    """Create multiple views comparing different clustering methods"""

    embeddings = np.array([v["embedding"] for v in vectors_data])

    # Reduce using both methods
    tsne_2d = reduce_dimensions(embeddings, method="tsne", n_components=2)
    pca_2d = reduce_dimensions(embeddings, method="pca", n_components=2)

    # Create subplot
    fig = make_subplots(
        rows=1, cols=2,
        subplot_titles=("t-SNE Projection", "PCA Projection"),
        horizontal_spacing=0.1
    )

    # Prepare colors by message type
    colors = []
    for vec in vectors_data:
        msg_id = vec["referenceId"]
        msg = messages_map.get(msg_id, {})
        msg_type = msg.get("messageType", "UNKNOWN")
        colors.append(hash(msg_type) % 360)

    # Add t-SNE scatter
    fig.add_trace(
        go.Scatter(
            x=tsne_2d[:, 0],
            y=tsne_2d[:, 1],
            mode='markers',
            marker=dict(size=10, color=colors, colorscale='Viridis'),
            name='t-SNE',
            showlegend=False
        ),
        row=1, col=1
    )

    # Add PCA scatter
    fig.add_trace(
        go.Scatter(
            x=pca_2d[:, 0],
            y=pca_2d[:, 1],
            mode='markers',
            marker=dict(size=10, color=colors, colorscale='Viridis'),
            name='PCA',
            showlegend=False
        ),
        row=1, col=2
    )

    fig.update_layout(
        title="Cluster Comparison: t-SNE vs PCA",
        height=600,
        width=1400,
        template='plotly_white'
    )

    return fig

def create_statistics_view(vectors_data, messages_map):
    """Create statistics dashboard"""

    # Count by message type
    type_counts = {}
    cluster_counts = {}
    sender_counts = {}

    for vec in vectors_data:
        msg = messages_map.get(vec["referenceId"], {})
        msg_type = msg.get("messageType", "UNKNOWN")
        type_counts[msg_type] = type_counts.get(msg_type, 0) + 1

        cluster_id = vec.get("clusterId")
        if cluster_id is not None:
            cluster_counts[str(cluster_id)] = cluster_counts.get(str(cluster_id), 0) + 1

        sender = msg.get("senderId", "UNKNOWN")
        sender_counts[sender] = sender_counts.get(sender, 0) + 1

    # Create subplots
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=(
            "Messages by Type",
            "Messages by Cluster",
            "Messages by Sender",
            "Document Types"
        ),
        specs=[
            [{"type": "pie"}, {"type": "bar"}],
            [{"type": "bar"}, {"type": "pie"}]
        ]
    )

    # Message types pie chart
    fig.add_trace(
        go.Pie(
            labels=list(type_counts.keys()),
            values=list(type_counts.values()),
            name="Message Types"
        ),
        row=1, col=1
    )

    # Clusters bar chart
    fig.add_trace(
        go.Bar(
            x=list(cluster_counts.keys()),
            y=list(cluster_counts.values()),
            name="Clusters"
        ),
        row=1, col=2
    )

    # Senders bar chart
    top_senders = sorted(sender_counts.items(), key=lambda x: x[1], reverse=True)[:10]
    fig.add_trace(
        go.Bar(
            x=[s[0] for s in top_senders],
            y=[s[1] for s in top_senders],
            name="Top Senders"
        ),
        row=2, col=1
    )

    # Document types pie chart
    doc_types = {}
    for vec in vectors_data:
        doc_type = vec["type"]
        doc_types[doc_type] = doc_types.get(doc_type, 0) + 1

    fig.add_trace(
        go.Pie(
            labels=list(doc_types.keys()),
            values=list(doc_types.values()),
            name="Document Types"
        ),
        row=2, col=2
    )

    fig.update_layout(
        title="SWIFT Message Statistics",
        height=800,
        width=1400,
        showlegend=False
    )

    return fig

def main():
    parser = argparse.ArgumentParser(description="Visualize SWIFT message clusters")
    parser.add_argument("--method", choices=["tsne", "pca"], default="tsne",
                       help="Dimensionality reduction method (default: tsne)")
    parser.add_argument("--dimensions", choices=["2d", "3d"], default="2d",
                       help="Number of dimensions for visualization (default: 2d)")
    parser.add_argument("--view", choices=["scatter", "comparison", "stats", "all"], default="all",
                       help="Type of visualization (default: all)")
    parser.add_argument("--output", type=str, default="cluster_visualization.html",
                       help="Output HTML file (default: cluster_visualization.html)")

    args = parser.parse_args()

    print("=" * 70)
    print("SWIFT Message Cluster Visualization")
    print("=" * 70)
    print()

    # Fetch data
    vectors_data = fetch_vectors_from_elasticsearch()
    if not vectors_data:
        print("No vectors found. Please upload messages first.")
        return

    messages_map = fetch_messages_from_api()

    print()
    print(f"Total vectors: {len(vectors_data)}")
    print(f"Total messages: {len(messages_map)}")
    print()

    # Create visualizations
    if args.view == "scatter" or args.view == "all":
        if args.dimensions == "2d":
            fig = create_2d_visualization(vectors_data, messages_map, args.method)
        else:
            fig = create_3d_visualization(vectors_data, messages_map, args.method)

        output_file = args.output.replace(".html", f"_{args.method}_{args.dimensions}.html")
        fig.write_html(output_file)
        print(f"✓ Saved {args.dimensions.upper()} {args.method.upper()} visualization: {output_file}")

    if args.view == "comparison" or args.view == "all":
        fig = create_cluster_comparison(vectors_data, messages_map)
        output_file = args.output.replace(".html", "_comparison.html")
        fig.write_html(output_file)
        print(f"✓ Saved comparison visualization: {output_file}")

    if args.view == "stats" or args.view == "all":
        fig = create_statistics_view(vectors_data, messages_map)
        output_file = args.output.replace(".html", "_stats.html")
        fig.write_html(output_file)
        print(f"✓ Saved statistics dashboard: {output_file}")

    print()
    print("=" * 70)
    print("Visualization complete! Open the HTML files in your browser.")
    print("=" * 70)

if __name__ == "__main__":
    main()
