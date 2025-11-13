#!/usr/bin/env python3
"""
Realistic SWIFT Message Dataset Generator
Creates diverse messages with natural variations to produce varying confidence scores
"""

import requests
import random
from datetime import datetime, timedelta

BASE_URL = "http://localhost:8080/api/v2"

# Realistic trading pairs with different characteristics
TRADING_PAIRS = [
    {"seller": "CITIBANK", "buyer": "DEUTDEFF", "region": "US-EU"},
    {"seller": "JPMORGAN", "buyer": "BNPPARFR", "region": "US-EU"},
    {"seller": "BOFA", "buyer": "HSBCGB2L", "region": "US-UK"},
    {"seller": "DBSSGSG", "buyer": "CITIBANK", "region": "ASIA-US"},
    {"seller": "MIZUHOJP", "buyer": "JPMORGAN", "region": "ASIA-US"},
    {"seller": "ICBCCNBJ", "buyer": "BOFA", "region": "ASIA-US"},
]

# Diverse commodity types
COMMODITIES = [
    {"name": "INDUSTRIAL MACHINERY", "typical_amount": (50000, 200000), "units": ["UNITS", "SETS"]},
    {"name": "ELECTRONIC COMPONENTS", "typical_amount": (30000, 150000), "units": ["UNITS", "PIECES"]},
    {"name": "TEXTILE GOODS", "typical_amount": (20000, 100000), "units": ["METERS", "ROLLS"]},
    {"name": "AUTOMOTIVE PARTS", "typical_amount": (40000, 180000), "units": ["UNITS", "SETS"]},
    {"name": "CHEMICAL PRODUCTS", "typical_amount": (25000, 120000), "units": ["TONS", "LITERS"]},
    {"name": "FOOD PRODUCTS", "typical_amount": (15000, 80000), "units": ["TONS", "CONTAINERS"]},
    {"name": "PHARMACEUTICAL SUPPLIES", "typical_amount": (60000, 250000), "units": ["UNITS", "BOXES"]},
    {"name": "CONSTRUCTION MATERIALS", "typical_amount": (35000, 160000), "units": ["TONS", "M3"]},
]

# Document variations
DOCUMENT_SETS = [
    # Standard set
    ["COMMERCIAL INVOICE IN TRIPLICATE", "PACKING LIST IN DUPLICATE", "CERTIFICATE OF ORIGIN", "INSURANCE CERTIFICATE", "BILL OF LADING"],
    # Extended set
    ["COMMERCIAL INVOICE IN QUADRUPLICATE", "PACKING LIST IN TRIPLICATE", "CERTIFICATE OF ORIGIN", "INSURANCE POLICY", "OCEAN BILL OF LADING", "INSPECTION CERTIFICATE"],
    # Minimal set
    ["COMMERCIAL INVOICE IN DUPLICATE", "PACKING LIST", "BILL OF LADING"],
    # Quality-focused
    ["COMMERCIAL INVOICE IN TRIPLICATE", "PACKING LIST IN DUPLICATE", "CERTIFICATE OF ORIGIN", "QUALITY CERTIFICATE", "BILL OF LADING", "TEST REPORT"],
    # Air shipment
    ["COMMERCIAL INVOICE IN TRIPLICATE", "PACKING LIST IN DUPLICATE", "CERTIFICATE OF ORIGIN", "AIR WAYBILL", "INSURANCE CERTIFICATE"],
]

# Payment terms variations
PAYMENT_TERMS = [
    "DRAFTS AT SIGHT",
    "DRAFTS AT 30 DAYS SIGHT",
    "DRAFTS AT 60 DAYS SIGHT",
    "DRAFTS AT 90 DAYS SIGHT",
    "DEFERRED PAYMENT AT 60 DAYS",
]

# Shipment policies
SHIPMENT_POLICIES = [
    {"partial": "PARTIAL SHIPMENTS PROHIBITED", "trans": "TRANSSHIPMENT NOT ALLOWED"},
    {"partial": "PARTIAL SHIPMENTS ALLOWED", "trans": "TRANSSHIPMENT PERMITTED"},
    {"partial": "PARTIAL SHIPMENTS PROHIBITED", "trans": "TRANSSHIPMENT PERMITTED"},
    {"partial": "PARTIAL SHIPMENTS ALLOWED", "trans": "TRANSSHIPMENT NOT ALLOWED"},
]

# Additional conditions variations
ADDITIONAL_CONDITIONS = [
    ["INSPECTION CERTIFICATE REQUIRED", "CERTIFICATE OF QUALITY REQUIRED"],
    ["QUALITY INSPECTION REQUIRED", "TEST REPORT MANDATORY"],
    ["PRE-SHIPMENT INSPECTION MANDATORY"],
    ["CERTIFICATE OF ANALYSIS REQUIRED", "QUALITY CERTIFICATE REQUIRED"],
    [],  # No additional conditions
]

# Banking charge variations
BANKING_CHARGES = [
    "ALL BANKING CHARGES OUTSIDE\\nOPENING BANK ARE FOR ACCOUNT\\nOF BENEFICIARY",
    "ALL BANK CHARGES OUTSIDE ISSUING\\nBANK FOR BENEFICIARY ACCOUNT",
    "ALL CHARGES OUTSIDE ISSUING BANK\\nFOR ACCOUNT OF APPLICANT",
]

# Presentation periods
PRESENTATION_PERIODS = [
    "PERIOD FOR PRESENTATION 15 DAYS",
    "DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS",
    "PRESENTATION PERIOD 10 DAYS",
    "DOCUMENTS TO BE PRESENTED WITHIN 30 DAYS",
]

# Confirmation instructions
CONFIRMATIONS = [
    "CONFIRMATION INSTRUCTIONS IF ANY",
    "WITHOUT CONFIRMATION",
    "CONFIRM",
]

def get_auth_token():
    """Get authentication token"""
    response = requests.post(
        "http://localhost:8080/api/auth/login",
        json={"email": "admin@swift.com", "password": "admin123"}
    )
    if response.status_code == 200:
        return response.json().get('token')
    return None

def generate_mt700_message(pair, commodity, variant_index):
    """Generate a diverse MT700 message"""

    # Random variations
    amount_range = commodity["typical_amount"]
    amount = random.randint(amount_range[0], amount_range[1])

    # Random quantity and unit price
    quantity = random.randint(10, 1000)
    unit_price = amount / quantity
    unit = random.choice(commodity["units"])

    # Random dates
    today = datetime.now()
    expiry_date = today + timedelta(days=random.randint(30, 180))
    latest_shipment = today + timedelta(days=random.randint(5, 60))
    issue_date = today + timedelta(days=random.randint(-10, 10))

    # Select variations
    docs = random.choice(DOCUMENT_SETS)
    payment = random.choice(PAYMENT_TERMS)
    shipment = random.choice(SHIPMENT_POLICIES)
    conditions = random.choice(ADDITIONAL_CONDITIONS)
    charges = random.choice(BANKING_CHARGES)
    presentation = random.choice(PRESENTATION_PERIODS)
    confirmation = random.choice(CONFIRMATIONS)

    # Random applicant and beneficiary details
    applicants = [
        f"{pair['buyer'].upper()} CORPORATION\\n{random.randint(100, 999)} MAIN STREET\\nNEW YORK NY 1000{random.randint(1, 9)}",
        f"{pair['buyer'].upper()} TRADING LTD\\n{random.randint(100, 999)} BUSINESS AVENUE\\nLONDON EC{random.randint(1, 4)}A {random.randint(1, 9)}AA",
        f"{pair['buyer'].upper()} HOLDINGS\\n{random.randint(100, 999)} COMMERCIAL ROAD\\nFRANKFURT 6001{random.randint(1, 9)}",
    ]

    beneficiaries = [
        f"{pair['seller'].upper()} MANUFACTURING\\nPUDONG DISTRICT\\nSHANGHAI CHINA",
        f"{pair['seller'].upper()} EXPORTS\\nINDUSTRIAL ZONE\\nSHENZHEN CHINA",
        f"{pair['seller'].upper()} INDUSTRIES\\nTECH PARK\\nBANGALORE INDIA",
    ]

    applicant = random.choice(applicants)
    beneficiary = random.choice(beneficiaries)

    # Random LC reference
    lc_ref = f"LC{random.randint(100000, 999999)}{variant_index}"

    # Random tolerance
    tolerance = random.choice(["05/05", "10/10", "03/03", "00/00"])

    # Build additional conditions section
    conditions_section = ""
    if conditions:
        conditions_section = f":47A:ADDITIONAL CONDITIONS:\\n" + "\\n".join(conditions) + "\\n"

    # Random PO number
    po_number = f"PO-{random.randint(2024, 2025)}-{random.randint(1000, 9999)}"

    content = f""":20:{lc_ref}
:23:ISSUANCE OF A DOCUMENTARY CREDIT
:31C:{expiry_date.strftime('%y%m%d')}
:40A:IRREVOCABLE
:31D:{issue_date.strftime('%y%m%d')}
:50:{applicant}
:59:{beneficiary}
:32B:USD {amount:.2f}
:39A:{tolerance}
:41D:{pair['seller'].upper()} INTERNATIONAL BANK
:42C:{payment}
:43P:{shipment['partial']}
:43T:{shipment['trans']}
:44A:COMMODITY: {commodity['name']}
QUANTITY: {quantity} {unit}
UNIT PRICE: USD {unit_price:.2f}
:44E:SHANGHAI PORT, CHINA
:44F:NEW YORK PORT, USA
:44C:{latest_shipment.strftime('%y%m%d')}
:45A:{chr(10).join(docs)}
:46A:{commodity['name']} AS PER PURCHASE
ORDER NO. {po_number}
{conditions_section}:71B:{charges}
:48:{presentation}
:49:{confirmation}"""

    return {
        "rawContent": content,
        "messageType": "MT700",
        "senderId": pair['seller'],
        "receiverId": pair['buyer']
    }

def generate_mt710_message(pair, commodity, variant_index):
    """Generate an MT710 advice message"""

    amount = random.randint(commodity["typical_amount"][0], commodity["typical_amount"][1])

    # Dates
    today = datetime.now()
    expiry_date = today + timedelta(days=random.randint(30, 150))
    latest_shipment = today + timedelta(days=random.randint(5, 50))

    lc_ref = f"ADV{random.randint(100000, 999999)}{variant_index}"
    sender_ref = f"LCAD{random.randint(10000, 99999)}"

    payment = random.choice(PAYMENT_TERMS)

    content = f""":20:{lc_ref}
:21:{sender_ref}
:23:ADVICE OF A DOCUMENTARY CREDIT
:40A:IRREVOCABLE
:31D:{expiry_date.strftime('%y%m%d')}
:32B:USD {amount:.2f}
:50:{pair['buyer'].upper()} LIMITED\\nBUSINESS DISTRICT\\nNEW YORK USA
:59:{pair['seller'].upper()} COMPANY\\nMANUFACTURING ZONE\\nSHANGHAI CHINA
:42C:{payment}
:43P:{random.choice(SHIPMENT_POLICIES)['partial']}
:43T:{random.choice(SHIPMENT_POLICIES)['trans']}
:44C:{latest_shipment.strftime('%y%m%d')}
:45A:{chr(10).join(random.choice(DOCUMENT_SETS)[:3])}
:78:WE HEREBY ADVISE YOU THAT WE HAVE
RECEIVED THE ABOVE MENTIONED DOCUMENTARY
CREDIT FOR YOUR ACCOUNT"""

    return {
        "rawContent": content,
        "messageType": "MT710",
        "senderId": pair['seller'],
        "receiverId": pair['buyer']
    }

def main():
    print("="*70)
    print("REALISTIC SWIFT MESSAGE DATASET GENERATOR")
    print("="*70)
    print()

    token = get_auth_token()
    if not token:
        print("✗ Failed to authenticate")
        return

    print("✓ Authentication successful")
    print()

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    messages = []

    # Generate diverse messages for each trading pair
    for pair in TRADING_PAIRS:
        # Each pair gets 20-40 MT700s with different commodities and variations
        mt700_count = random.randint(20, 40)
        for i in range(mt700_count):
            commodity = random.choice(COMMODITIES)
            msg = generate_mt700_message(pair, commodity, i)
            messages.append(msg)

        # Each pair gets 10-20 MT710s
        mt710_count = random.randint(10, 20)
        for i in range(mt710_count):
            commodity = random.choice(COMMODITIES)
            msg = generate_mt710_message(pair, commodity, i)
            messages.append(msg)

    print(f"Generated {len(messages)} diverse messages")
    print(f"  - ~{sum(1 for m in messages if m['messageType'] == 'MT700')} MT700 messages")
    print(f"  - ~{sum(1 for m in messages if m['messageType'] == 'MT710')} MT710 messages")
    print(f"  - Across {len(TRADING_PAIRS)} trading pairs")
    print()

    # Upload messages
    print("Uploading messages...")
    uploaded = 0
    for i, msg in enumerate(messages):
        try:
            response = requests.post(f"{BASE_URL}/messages", headers=headers, json=msg)
            if response.status_code in [200, 201]:
                uploaded += 1

            if (i + 1) % 50 == 0:
                print(f"  Uploaded {i + 1}/{len(messages)} messages...")
        except Exception as e:
            print(f"Error uploading message {i}: {e}")

    print()
    print("="*70)
    print("UPLOAD COMPLETE")
    print("="*70)
    print(f"✓ Successfully uploaded: {uploaded}/{len(messages)}")
    print()
    print("NEXT STEPS:")
    print("1. Run template extraction: POST /api/templates/extract")
    print("2. View confidence scores - they should now vary (60%-95%)")
    print("3. More diverse messages = more realistic confidence distribution")
    print("="*70)

if __name__ == "__main__":
    main()
