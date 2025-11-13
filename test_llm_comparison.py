#!/usr/bin/env python3
"""
Test script to demonstrate LLM comparison feature
Posts a SWIFT message with slight differences from existing templates
"""

import requests
import json
import time

BASE_URL = "http://localhost:8080/api"

def get_auth_token():
    """Get authentication token"""
    try:
        # Try to login
        response = requests.post(
            f"{BASE_URL}/auth/login",
            json={"email": "admin@swift.com", "password": "admin123"}
        )
        if response.status_code == 200:
            token = response.json().get('token')
            if token:
                print(f"✓ Authenticated successfully")
                return token
    except Exception as e:
        print(f"Authentication failed: {e}")
    return None

def extract_templates(token):
    """Extract templates for a trading pair"""
    headers = {"Authorization": f"Bearer {token}"}

    print("\nExtracting templates for CITIBANK → DEUTDEFF...")
    try:
        response = requests.post(
            f"{BASE_URL}/templates/extract",
            headers=headers,
            params={"buyerId": "CITIBANK", "sellerId": "DEUTDEFF"}
        )
        if response.status_code == 200:
            data = response.json()
            print(f"✓ Extracted {data.get('templateCount', 0)} templates")
            return True
    except Exception as e:
        print(f"Template extraction failed: {e}")
    return False

def post_test_message(token):
    """Post a test message with slight differences"""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    # This message has several differences from typical MT700s:
    # - Different amount (USD 125,000 vs typical 75-100K)
    # - Electronic components vs machinery
    # - Partial shipments ALLOWED vs PROHIBITED
    # - Transshipment PERMITTED vs NOT ALLOWED
    # - 21 day presentation vs 15 days
    # - Quadruplicate invoice vs triplicate

    message_data = {
        "rawContent": ":20:TESTLC999888\\n:23:ISSUANCE OF A DOCUMENTARY CREDIT\\n:31C:260131\\n:40A:IRREVOCABLE\\n:31D:251220\\n:50:GLOBAL TECH INC\\n456 OAK AVENUE\\nSAN FRANCISCO CA 94102\\n:59:SHANGHAI MANUFACTURING LTD\\nPUDONG DISTRICT\\nSHANGHAI CHINA\\n:32B:USD 125000.00\\n:39A:10/10\\n:41D:CITIBANK NA NEW YORK\\n:42C:DRAFTS AT SIGHT\\n:43P:PARTIAL SHIPMENTS ALLOWED\\n:43T:TRANSSHIPMENT PERMITTED\\n:44A:COMMODITY: ELECTRONIC COMPONENTS\\nQUANTITY: 500 UNITS\\nUNIT PRICE: USD 250.00\\n:44E:SHANGHAI PORT, CHINA\\n:44F:LOS ANGELES PORT, USA\\n:44C:260115\\n:45A:COMMERCIAL INVOICE IN QUADRUPLICATE\\nPACKING LIST IN TRIPLICATE\\nCERTIFICATE OF ORIGIN\\nINSURANCE POLICY\\nOCEAN BILL OF LADING\\n:46A:ELECTRONIC PARTS AS PER PURCHASE\\nORDER NO. PO-2025-5432\\n:47A:SPECIAL CONDITIONS:\\nQUALITY INSPECTION REQUIRED\\nTEST REPORT MANDATORY\\n:71B:ALL BANK CHARGES OUTSIDE ISSUING\\nBANK FOR BENEFICIARY ACCOUNT\\n:48:DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS\\n:49:WITHOUT CONFIRMATION",
        "messageType": "MT700",
        "sender": "CITIUS33",
        "receiver": "DEUTDEFF",
        "buyerId": "CITIBANK",
        "sellerId": "DEUTDEFF"
    }

    print("\\n" + "="*70)
    print("POSTING TEST MT700 MESSAGE WITH SLIGHT DIFFERENCES")
    print("="*70)

    try:
        response = requests.post(
            f"{BASE_URL}/messages",
            headers=headers,
            json=message_data
        )

        if response.status_code in [200, 201]:
            result = response.json()

            print("\\n✓ Message posted successfully!")
            print("="*70)
            print(f"\\nTransaction ID: {result.get('transactionId', result.get('id', 'N/A'))}")
            print(f"Matched Template: {result.get('templateId', 'N/A')}")
            print(f"Match Confidence: {result.get('matchConfidence', 'N/A')}%")

            print("\\n" + "-"*70)
            print("LLM COMPARISON ANALYSIS:")
            print("-"*70)
            llm_comparison = result.get('llmComparison')
            if llm_comparison:
                print(llm_comparison)
            else:
                print("No LLM comparison available (Azure OpenAI not configured)")

            print("\\n" + "="*70)
            print("\\nKey Differences in this test message:")
            print("  • Amount: USD 125,000 (vs typical 75,000-100,000)")
            print("  • Commodity: Electronic Components (vs Machinery)")
            print("  • Quantity/Price: 500 units @ $250 each")
            print("  • Partial shipments: ALLOWED (vs typical PROHIBITED)")
            print("  • Transshipment: PERMITTED (vs typical NOT ALLOWED)")
            print("  • Documents: QUADRUPLICATE invoice (vs typical TRIPLICATE)")
            print("  • Presentation period: 21 days (vs typical 15 days)")
            print("="*70)
            print()

            return True
        else:
            print(f"\\nFailed to post message: {response.status_code}")
            print(response.text)
    except Exception as e:
        print(f"Error posting message: {e}")

    return False

def main():
    print("="*70)
    print("SWIFT MESSAGE LLM COMPARISON DEMO")
    print("="*70)

    # Get token
    token = get_auth_token()
    if not token:
        print("\\n✗ Failed to authenticate. Please run clean_and_seed.sh first")
        return

    # Extract templates
    extract_templates(token)

    # Wait a bit for extraction to complete
    time.sleep(5)

    # Post test message
    post_test_message(token)

if __name__ == "__main__":
    main()
