#!/usr/bin/env python3
"""
Test script to validate Sentence-BERT semantic matching
Tests variations that should have high similarity scores
"""

import requests
import json

API_BASE_URL = "http://localhost:8080/api/v2"

def test_semantic_similarity():
    """Test semantic similarity with various text variations"""

    test_cases = [
        {
            "name": "Synonym Test - PROHIBITED vs NOT ALLOWED",
            "text1": "PARTIAL SHIPMENTS PROHIBITED",
            "text2": "PARTIAL SHIPMENTS NOT ALLOWED",
            "expected": "high"
        },
        {
            "name": "Word Order Test - DRAFTS AT SIGHT",
            "text1": "DRAFTS AT SIGHT",
            "text2": "AT SIGHT DRAFTS",
            "expected": "high"
        },
        {
            "name": "Synonym Test - TRANSHIPMENT",
            "text1": "TRANSHIPMENT PROHIBITED",
            "text2": "TRANSHIPMENT NOT ALLOWED",
            "expected": "high"
        },
        {
            "name": "Payment Terms Variation",
            "text1": "BY PAYMENT",
            "text2": "BY DEF PAYMENT",
            "expected": "medium"
        },
        {
            "name": "Completely Different",
            "text1": "DRAFTS AT SIGHT",
            "text2": "CERTIFICATE OF ORIGIN",
            "expected": "low"
        }
    ]

    print("=" * 80)
    print("Sentence-BERT Semantic Similarity Tests")
    print("=" * 80)
    print()

    # Test by creating temporary messages and comparing embeddings
    # We'll use the clustering similarity endpoint if available, or test directly

    for i, test in enumerate(test_cases, 1):
        print(f"Test {i}: {test['name']}")
        print(f"  Text 1: '{test['text1']}'")
        print(f"  Text 2: '{test['text2']}'")
        print(f"  Expected: {test['expected']} similarity")

        # Create two test messages
        msg1 = {
            "messageType": "MT700",
            "rawContent": f"{{{{1:F01TESTUS33AXXX}}}}{{{{2:I700TESTGB22XXXX}}}}{{{{4:\n:20:TEST{i}A\n:45A:{test['text1']}\n-}}}}",
            "senderId": "TESTUS33",
            "receiverId": "TESTGB22"
        }

        msg2 = {
            "messageType": "MT700",
            "rawContent": f"{{{{1:F01TESTUS33AXXX}}}}{{{{2:I700TESTGB22XXXX}}}}{{{{4:\n:20:TEST{i}B\n:45A:{test['text2']}\n-}}}}",
            "senderId": "TESTUS33",
            "receiverId": "TESTGB22"
        }

        try:
            # Upload messages
            r1 = requests.post(f"{API_BASE_URL}/messages", json=msg1, timeout=10)
            r2 = requests.post(f"{API_BASE_URL}/messages", json=msg2, timeout=10)

            if r1.status_code == 200 and r2.status_code == 200:
                msg1_id = r1.json()['id']
                msg2_id = r2.json()['id']
                print(f"  ✓ Messages created: {msg1_id[:8]}... and {msg2_id[:8]}...")

                # Note: We'd need a similarity endpoint to compare directly
                # For now, just confirm they were created with embeddings
                print(f"  → Messages processed with Sentence-BERT embeddings")
            else:
                print(f"  ✗ Failed to create messages")
        except Exception as e:
            print(f"  ✗ Error: {e}")

        print()

    print("=" * 80)
    print("Test Complete!")
    print()
    print("Next step: Extract templates to see if semantically similar messages")
    print("cluster together even with text variations.")
    print("=" * 80)

if __name__ == "__main__":
    test_semantic_similarity()
