#!/usr/bin/env python3
"""Check templates and confidence scores"""

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
    token = get_auth_token()
    if not token:
        print("Failed to authenticate")
        return

    headers = {"Authorization": f"Bearer {token}"}

    # Fetch templates
    response = requests.get(f"{BASE_URL}/templates?size=100", headers=headers)

    if response.status_code == 200:
        data = response.json()

        # Handle both array and paginated responses
        if isinstance(data, list):
            templates = data
            total = len(data)
        else:
            templates = data.get('content', [])
            total = data.get('totalElements', len(templates))

        print("=" * 80)
        print(f"TEMPLATES AND CONFIDENCE SCORES (Total: {total})")
        print("=" * 80)
        print()

        if not templates:
            print("No templates found. Run template extraction first.")
            return

        for template in templates:
            confidence = template.get('confidence', 0) * 100
            print(f"Template ID: {template.get('id')}")
            print(f"  Trading Pair: {template.get('buyerId')} → {template.get('sellerId')}")
            print(f"  Message Type: {template.get('messageType')}")
            print(f"  Confidence: {confidence:.0f}%")
            print(f"  Message Count: {template.get('messageCount', 'N/A')}")
            print()

        # Calculate confidence distribution
        confidences = [t.get('confidence', 0) * 100 for t in templates]
        if confidences:
            print("=" * 80)
            print("CONFIDENCE DISTRIBUTION:")
            print("=" * 80)
            print(f"  Min: {min(confidences):.0f}%")
            print(f"  Max: {max(confidences):.0f}%")
            print(f"  Avg: {sum(confidences)/len(confidences):.0f}%")
            print()

            # Group by confidence ranges
            ranges = {
                "100%": sum(1 for c in confidences if c == 100),
                "90-99%": sum(1 for c in confidences if 90 <= c < 100),
                "80-89%": sum(1 for c in confidences if 80 <= c < 90),
                "70-79%": sum(1 for c in confidences if 70 <= c < 80),
                "60-69%": sum(1 for c in confidences if 60 <= c < 70),
                "<60%": sum(1 for c in confidences if c < 60),
            }

            print("  Distribution by range:")
            for range_name, count in ranges.items():
                if count > 0:
                    pct = (count / len(confidences)) * 100
                    print(f"    {range_name}: {count} templates ({pct:.1f}%)")
    else:
        print(f"Error fetching templates: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    main()
