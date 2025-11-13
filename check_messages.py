#!/usr/bin/env python3
"""Check messages and their buyer/seller IDs"""

import requests
import json
from collections import Counter

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
    token = get_auth_token()
    if not token:
        print("Failed to authenticate")
        return

    headers = {"Authorization": f"Bearer {token}"}

    # Fetch messages
    response = requests.get(f"{BASE_URL}/messages?size=500", headers=headers)

    if response.status_code == 200:
        data = response.json()

        # Handle both array and paginated responses
        if isinstance(data, list):
            messages = data
            total = len(data)
        else:
            messages = data.get('content', [])
            total = data.get('totalElements', len(messages))

        print("=" * 80)
        print(f"MESSAGES (Total: {total})")
        print("=" * 80)
        print()

        if not messages:
            print("No messages found.")
            return

        # Count by trading pair and message type
        pairs = Counter()
        types = Counter()

        for msg in messages:
            buyer = msg.get('buyerId', 'N/A')
            seller = msg.get('sellerId', 'N/A')
            msg_type = msg.get('messageType', 'N/A')

            pairs[f"{buyer} → {seller}"] += 1
            types[msg_type] += 1

        print("Messages by Trading Pair:")
        for pair, count in pairs.most_common():
            print(f"  {pair}: {count} messages")

        print()
        print("Messages by Type:")
        for msg_type, count in types.most_common():
            print(f"  {msg_type}: {count} messages")

        print()
        print("Sample message IDs:")
        for i, msg in enumerate(messages[:5]):
            print(f"  {i+1}. ID: {msg.get('id')}, Type: {msg.get('messageType')}, "
                  f"Pair: {msg.get('buyerId')} → {msg.get('sellerId')}")

    else:
        print(f"Error fetching messages: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    main()
