#!/usr/bin/env python3
"""
Test Sentence-BERT semantic clustering by creating messages with variations
"""

import requests
import json
from datetime import datetime

API_BASE_URL = "http://localhost:8080/api/v2"

def create_semantic_test_messages():
    """Create pairs of semantically similar but textually different messages"""

    print("=" * 80)
    print("Creating Semantic Test Messages")
    print("=" * 80)
    print()

    # Base template with semantic variation 1
    template_variation_1 = """{{1:F01TESTUS33AXXX0000000000}}{{2:I700TESTGB22XXXXN}}{{4:
:20:SEMANTIC001
:23:ISSUANCE
:31C:251111
:40A:IRREVOCABLE
:50:Test Applicant Corp
123 Business Street, New York
:59:Test Beneficiary Ltd
456 Commerce Ave, London
:32B:USD50000,00
:41A:TESTGB22
:42C:DRAFTS AT SIGHT
:42D:BY PAYMENT
:43P:PARTIAL SHIPMENTS PROHIBITED
:43T:TRANSHIPMENT PROHIBITED
:44C:251230
:44D:Electronic Components
:45A:COMMERCIAL INVOICE IN TRIPLICATE
PACKING LIST IN DUPLICATE
CERTIFICATE OF ORIGIN
:71B:ALL CHARGES FOR BENEFICIARY ACCOUNT
-}}"""

    # Base template with semantic variation 2 (same meaning, different words)
    template_variation_2 = """{{1:F01TESTUS33AXXX0000000000}}{{2:I700TESTGB22XXXXN}}{{4:
:20:SEMANTIC002
:23:ISSUANCE
:31C:251111
:40A:IRREVOCABLE
:50:Test Applicant Corp
123 Business Street, New York
:59:Test Beneficiary Ltd
456 Commerce Ave, London
:32B:USD50000,00
:41A:TESTGB22
:42C:AT SIGHT DRAFTS
:42D:BY PAYMENT
:43P:PARTIAL SHIPMENTS NOT ALLOWED
:43T:TRANSHIPMENT NOT ALLOWED
:44C:251230
:44D:Electronic Components
:45A:INVOICE COMMERCIAL IN THREE COPIES
LIST OF PACKING IN TWO COPIES
ORIGIN CERTIFICATE
:71B:BENEFICIARY ACCOUNT FOR ALL CHARGES
-}}"""

    # Different template (should NOT cluster with above)
    different_template = """{{1:F01DEUTDEFFAXXX0000000000}}{{2:I700BNPAFRPPXXXXN}}{{4:
:20:DIFFERENT001
:23:ISSUANCE
:31C:251111
:40A:IRREVOCABLE
:50:Different Applicant GmbH
789 Frankfurt Street, Frankfurt
:59:Different Beneficiary SA
321 Paris Boulevard, Paris
:32B:EUR75000,00
:41A:BNPAFRPP
:42C:PAYABLE 90 DAYS AFTER BILL OF LADING DATE
:42D:BY DEF PAYMENT
:43P:PARTIAL SHIPMENTS ALLOWED
:43T:TRANSHIPMENT ALLOWED
:44C:251230
:44D:Machinery Parts
:45A:SIGNED INVOICE IN 4 COPIES
FULL SET 3/3 ORIGINAL BILLS OF LADING
PACKING LIST IN DUPLICATE
:71B:ALL BANKING CHARGES FOR BENEFICIARY
-}}"""

    messages = []

    # Create 10 variations of each semantic template
    for i in range(10):
        # Variation 1 group
        msg1 = {
            "messageType": "MT700",
            "rawContent": template_variation_1.replace("SEMANTIC001", f"SEMANTIC{i:03d}A"),
            "senderId": "TESTUS33",
            "receiverId": "TESTGB22"
        }
        messages.append(("Variation 1 (PROHIBITED)", msg1))

        # Variation 2 group (should cluster WITH variation 1 due to semantic similarity)
        msg2 = {
            "messageType": "MT700",
            "rawContent": template_variation_2.replace("SEMANTIC002", f"SEMANTIC{i:03d}B"),
            "senderId": "TESTUS33",
            "receiverId": "TESTGB22"
        }
        messages.append(("Variation 2 (NOT ALLOWED)", msg2))

        # Different template (should cluster SEPARATELY)
        if i < 5:  # Only create 5 of these
            msg3 = {
                "messageType": "MT700",
                "rawContent": different_template.replace("DIFFERENT001", f"DIFFERENT{i:03d}"),
                "senderId": "DEUTDEFF",
                "receiverId": "BNPAFRPP"
            }
            messages.append(("Different Template", msg3))

    # Upload messages
    print(f"Uploading {len(messages)} test messages...\n")
    success = 0
    failed = 0

    for label, msg in messages:
        try:
            response = requests.post(
                f"{API_BASE_URL}/messages",
                json=msg,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            if response.status_code in [200, 201]:
                msg_id = response.json().get('id', 'unknown')
                print(f"✓ [{label}] {msg_id[:12]}...")
                success += 1
            else:
                print(f"✗ [{label}] HTTP {response.status_code}")
                failed += 1
        except Exception as e:
            print(f"✗ [{label}] Error: {str(e)[:50]}")
            failed += 1

    print()
    print("-" * 80)
    print(f"Upload Complete: {success} success, {failed} failed")
    print("-" * 80)
    print()
    print("Expected clustering results:")
    print("  - Variation 1 + Variation 2 should cluster TOGETHER (20 messages)")
    print("    Despite using 'PROHIBITED' vs 'NOT ALLOWED'")
    print("    Despite using 'DRAFTS AT SIGHT' vs 'AT SIGHT DRAFTS'")
    print("  - Different Template should cluster SEPARATELY (5 messages)")
    print()
    print("Next steps:")
    print("  1. Go to http://localhost:3000")
    print("  2. Click 'Extract Templates'")
    print("  3. Verify Variation 1 and 2 cluster together (Sentence-BERT working!)")
    print()
    print("=" * 80)

if __name__ == "__main__":
    create_semantic_test_messages()
