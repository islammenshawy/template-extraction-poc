#!/usr/bin/env python3
"""Test cluster visualization endpoint"""

import requests
import json

BASE_URL = "http://localhost:8080/api"

def get_auth_token():
    """Get authentication token"""
    response = requests.post(
        f"{BASE_URL}/auth/login",
        json={"email": "admin@swift.com", "password": "admin123"}
    )
    if response.status_code == 200:
        return response.json().get('token')
    return None

def main():
    print("=" * 80)
    print("TESTING CLUSTER VISUALIZATION ENDPOINT")
    print("=" * 80)
    print()

    token = get_auth_token()
    if not token:
        print("✗ Failed to authenticate")
        return

    print("✓ Authentication successful")
    print()

    headers = {"Authorization": f"Bearer {token}"}

    # Test cluster visualization endpoint
    print("Testing GET /api/clusters/visualize...")
    response = requests.get(f"{BASE_URL}/clusters/visualize", headers=headers)

    print(f"Response Status: {response.status_code}")
    print()

    if response.status_code == 200:
        data = response.json()

        if "error" in data:
            print(f"✗ Error in response: {data.get('error')}")
            print(f"  Message: {data.get('message')}")
        else:
            print("✓ Cluster visualization successful!")
            print()

            # Show statistics
            stats = data.get('statistics', {})
            print(f"Total Messages: {stats.get('totalMessages', 0)}")
            print(f"Total Clusters: {stats.get('totalClusters', 0)}")
            print()

            # Show cluster sizes
            clusters = data.get('clusters', [])
            print(f"Cluster Breakdown:")
            for cluster in clusters:
                cluster_id = cluster.get('id')
                size = cluster.get('size', 0)
                type_dist = cluster.get('typeDistribution', {})
                print(f"  Cluster {cluster_id}: {size} messages")
                if type_dist:
                    print(f"    Types: {', '.join([f'{k}={v}' for k, v in type_dist.items()])}")

            print()
            print("=" * 80)
            print("✓ FIX VERIFIED: Cluster visualization is working correctly!")
            print("=" * 80)
    else:
        print(f"✗ Request failed with status {response.status_code}")
        print()
        print("Response body:")
        print(response.text)

if __name__ == "__main__":
    main()
