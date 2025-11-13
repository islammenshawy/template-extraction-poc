#!/usr/bin/env python3
import requests
import json

# Get all messages from MongoDB through backend API
print("Fetching all clustered messages...")

# Get all message IDs using a database query - fetch all messages with CLUSTERED status
# Since we don't have a direct endpoint, let's fetch messages one page at a time
all_messages = []
page = 0
page_size = 100

while True:
    response = requests.get(f"http://localhost:8080/api/messages?page={page}&size={page_size}")
    if response.status_code != 200:
        print(f"Failed to fetch messages: {response.status_code}")
        break

    data = response.json()
    if 'content' in data:
        # Paginated response
        messages = data['content']
        all_messages.extend([m for m in messages if m.get('status') == 'CLUSTERED'])

        # Check if this is the last page
        if data.get('last', True):
            break
        page += 1
    else:
        # Direct array response
        all_messages.extend([m for m in data if m.get('status') == 'CLUSTERED'])
        break

print(f"Found {len(all_messages)} clustered messages")

# Match each message
success_count = 0
failed_count = 0

for idx, message in enumerate(all_messages, 1):
    message_id = message['id']
    print(f"[{idx}/{len(all_messages)}] Matching message {message_id}...", end=" ")

    try:
        match_response = requests.post(f"http://localhost:8080/api/transactions/match/{message_id}")

        if match_response.status_code == 200 or match_response.status_code == 201:
            print("✓ Success")
            success_count += 1
        else:
            print(f"✗ Failed ({match_response.status_code})")
            failed_count += 1
    except Exception as e:
        print(f"✗ Error: {e}")
        failed_count += 1

print("\n" + "="*60)
print(f"Matching Summary:")
print(f"  Success: {success_count}")
print(f"  Failed:  {failed_count}")
print("="*60)
