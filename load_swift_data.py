#!/usr/bin/env python3
"""
SWIFT Message Dataset Generator and Loader
Generates realistic MT700 and MT710 messages and loads them into the system
"""

import requests
import json
import random
from datetime import datetime, timedelta

API_BASE_URL = "http://localhost:8080/api/v2"

# Sample data for generating realistic messages
BANKS = [
    {"code": "CITIUS33", "name": "Citibank N.A.", "city": "New York"},
    {"code": "CHASUS33", "name": "JPMorgan Chase", "city": "New York"},
    {"code": "BOFAUS3N", "name": "Bank of America", "city": "San Francisco"},
    {"code": "HSBCUS33", "name": "HSBC Bank USA", "city": "New York"},
    {"code": "DEUTDEFF", "name": "Deutsche Bank", "city": "Frankfurt"},
    {"code": "BNPAFRPP", "name": "BNP Paribas", "city": "Paris"},
    {"code": "BARCGB22", "name": "Barclays Bank", "city": "London"},
    {"code": "UBSWCHZH", "name": "UBS Switzerland", "city": "Zurich"},
    {"code": "SCBLSGSG", "name": "Standard Chartered Bank", "city": "Singapore"},
    {"code": "DBSSSGS", "name": "DBS Bank Ltd", "city": "Singapore"},
    {"code": "MHCBJPJT", "name": "Mizuho Bank", "city": "Tokyo"},
    {"code": "BOTKJPJT", "name": "MUFG Bank", "city": "Tokyo"},
    {"code": "ICBKUS33", "name": "Industrial and Commercial Bank of China", "city": "New York"},
    {"code": "BKCHCNBJ", "name": "Bank of China", "city": "Beijing"},
    {"code": "ABOCNBJ", "name": "Agricultural Bank of China", "city": "Beijing"},
    {"code": "AXISINBB", "name": "Axis Bank", "city": "Mumbai"},
]

COMPANIES = [
    "ABC Trading Corp", "Global Imports Ltd", "Tech Solutions Inc",
    "International Exports SA", "Pacific Trading Co", "Euro Commerce GmbH",
    "Asia Trade Partners", "Atlantic Shipping Inc", "Continental Trade Ltd",
    "Universal Logistics Corp", "Metro Trading LLC", "Prime Commodities Inc",
    "Eastern Goods Co", "Western Supply Chain", "Nordic Exports AS",
    "Southern Imports Ltd", "Midwest Manufacturing", "Coastal Distributors",
    "Highland Trade Group", "Valley Commerce Corp", "Summit Trading Partners"
]

GOODS = [
    "Electronic Components", "Textile Materials", "Machinery Parts",
    "Raw Materials", "Computer Equipment", "Industrial Chemicals",
    "Consumer Electronics", "Automotive Parts", "Medical Devices",
    "Agricultural Products"
]

CURRENCIES = ["USD", "EUR", "GBP", "CHF", "JPY"]

# MT700 Template Variations
MT700_TEMPLATES = [
    # Template 1: Standard documentary credit
    """{{1:F01CITIUS33AXXX0000000000}}{{2:I700BOFAUS3NXXXXN}}{{4:
:20:{ref_number}
:23:ISSUANCE
:31C:{issue_date}
:40A:IRREVOCABLE
:20:{sender_ref}
:31D:{place_date}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39A:APPROXIMATELY 05 PERCENT MORE OR LESS ACCEPTABLE
:41A:{available_bank}
:42C:DRAFTS AT SIGHT
:42D:MIXED PAYMENT
:43P:PARTIAL SHIPMENTS PROHIBITED
:43T:TRANSHIPMENT PROHIBITED
:44A:PORT OF LOADING
:44B:PORT OF DISCHARGE
:44C:LATEST DATE OF SHIPMENT {shipment_date}
:44D:{goods_description}
:45A:COMMERCIAL INVOICE IN TRIPLICATE
PACKING LIST IN DUPLICATE
CERTIFICATE OF ORIGIN
INSURANCE CERTIFICATE
:46A:DOCUMENTS MUST BE PRESENTED WITHIN 15 DAYS AFTER SHIPMENT DATE
:47A:ADDITIONAL CONDITIONS IF ANY
:71B:CHARGES OUTSIDE {currency} ARE FOR BENEFICIARY ACCOUNT
:48:PERIOD FOR PRESENTATION 15 DAYS
:49:CONFIRMATION INSTRUCTIONS
-}}""",

    # Template 2: Sight payment credit
    """{{1:F01HSBCUS33AXXX0000000000}}{{2:I700DEUTDEFFXXXXN}}{{4:
:20:{ref_number}
:23:ISSUANCE
:31C:{issue_date}
:40A:IRREVOCABLE
:20:{sender_ref}
:31D:{place_date}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:41D:{available_bank}
:42C:AT SIGHT
:42D:BY PAYMENT
:43P:PARTIAL SHIPMENTS ALLOWED
:43T:TRANSHIPMENT ALLOWED
:44E:PORT/AIRPORT OF LOADING
:44F:PORT/AIRPORT OF DISCHARGE
:44C:LATEST DATE {shipment_date}
:44D:{goods_description}
:45A:SIGNED COMMERCIAL INVOICE IN 3 COPIES
FULL SET CLEAN ON BOARD BILL OF LADING
CERTIFICATE OF ORIGIN
PACKING LIST
:46A:ALL DOCUMENTS TO BE ISSUED IN ENGLISH
:47A:BENEFICIARY CERTIFICATE STATING GOODS SHIPPED AS PER CONTRACT
:71B:ALL CHARGES OUTSIDE {currency} FOR ACCOUNT OF BENEFICIARY
:48:DOCUMENTS TO BE PRESENTED WITHIN 21 DAYS
-}}""",

    # Template 3: Deferred payment credit
    """{{1:F01BARCGB22AXXX0000000000}}{{2:I700BNPAFRPPXXXXN}}{{4:
:20:{ref_number}
:23:ISSUANCE
:31C:{issue_date}
:40A:IRREVOCABLE
:20:{sender_ref}
:31D:{place_date}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39A:TOLERANCE 10 PCT
:41A:{available_bank}
:42C:PAYABLE 90 DAYS AFTER BILL OF LADING DATE
:42D:BY DEF PAYMENT
:43P:PARTIAL SHIPMENTS NOT ALLOWED
:43T:TRANSHIPMENT NOT ALLOWED
:44A:ANY PORT IN {sender_country}
:44B:ANY PORT IN {receiver_country}
:44C:{shipment_date}
:44D:{goods_description}
TOTAL QUANTITY APPROX {quantity} UNITS
:45A:SIGNED INVOICE IN 4 COPIES
FULL SET 3/3 ORIGINAL CLEAN SHIPPED ON BOARD BILLS OF LADING
CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE
PACKING LIST IN DUPLICATE
:46A:THIRD PARTY DOCUMENTS NOT ACCEPTABLE
:47A:INSURANCE TO BE COVERED BY BUYER
:71B:ALL BANKING CHARGES OUTSIDE {currency} ARE FOR BENEFICIARY
:48:PRESENTATION PERIOD 21 DAYS
-}}"""
]

# MT710 Template (Advice of a third bank)
MT710_TEMPLATE = """{{1:F01{sender_bank}AXXX0000000000}}{{2:I710{receiver_bank}XXXXN}}{{4:
:20:{ref_number}
:21:{related_ref}
:23:THIRD BANK ADVICE
:31C:{issue_date}
:40A:IRREVOCABLE
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:41A:{available_bank}
:42C:DRAFTS AT SIGHT
:42D:BY PAYMENT
:43P:PARTIAL SHIPMENTS {partial_allowed}
:43T:TRANSHIPMENT {trans_allowed}
:44C:LATEST SHIPMENT DATE {shipment_date}
:44D:{goods_description}
:45A:COMMERCIAL INVOICE
BILL OF LADING
CERTIFICATE OF ORIGIN
:71B:ALL CHARGES FOR BENEFICIARY ACCOUNT
-}}"""


def random_date(start_date=None, days_ahead=30):
    """Generate a random date"""
    if start_date is None:
        start_date = datetime.now()
    random_days = random.randint(1, days_ahead)
    return (start_date + timedelta(days=random_days)).strftime("%y%m%d")


def format_swift_date(days_ahead=0):
    """Format date in SWIFT format (YYMMDD)"""
    return (datetime.now() + timedelta(days=days_ahead)).strftime("%y%m%d")


def generate_reference(prefix_type=None):
    """Generate a unique reference number with variation"""
    if prefix_type is None:
        prefix_type = random.choice(["LC", "DOC", "REF", "CR"])

    # Add variation in date formats
    date_format = random.choice([
        datetime.now().strftime('%Y%m%d'),  # YYYYMMDD
        datetime.now().strftime('%y%m%d'),   # YYMMDD
        datetime.now().strftime('%Y%m'),     # YYYYMM
        datetime.now().strftime('%Y'),       # YYYY
        ""  # No date
    ])

    return f"{prefix_type}{date_format}{random.randint(1000, 9999)}"


def generate_amount():
    """Generate a random amount"""
    return f"{random.randint(10000, 999999)},{random.randint(0, 99):02d}"


def generate_mt700_messages(count=20):
    """Generate MT700 messages with variations"""
    messages = []

    for i in range(count):
        template = random.choice(MT700_TEMPLATES)
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        applicant = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != applicant])
        goods = random.choice(GOODS)
        currency = random.choice(CURRENCIES)

        message = template.format(
            ref_number=generate_reference(),
            sender_ref=generate_reference(),
            issue_date=format_swift_date(0),
            place_date=format_swift_date(random.randint(1, 5)),
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Business St, {sender_bank['city']}",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Commerce Ave, {receiver_bank['city']}",
            currency=currency,
            amount=generate_amount(),
            available_bank=receiver_bank['code'],
            shipment_date=format_swift_date(random.randint(30, 90)),
            goods_description=goods,
            sender_country=sender_bank['city'].split()[-1],
            receiver_country=receiver_bank['city'].split()[-1],
            quantity=random.randint(100, 10000)
        )

        messages.append({
            "messageType": "MT700",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt710_messages(count=10):
    """Generate MT710 messages"""
    messages = []

    for i in range(count):
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        applicant = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != applicant])
        goods = random.choice(GOODS)
        currency = random.choice(CURRENCIES)

        message = MT710_TEMPLATE.format(
            sender_bank=sender_bank['code'],
            receiver_bank=receiver_bank['code'],
            ref_number=generate_reference(),
            related_ref=generate_reference(),
            issue_date=format_swift_date(0),
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Business St, {sender_bank['city']}",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Commerce Ave, {receiver_bank['city']}",
            currency=currency,
            amount=generate_amount(),
            available_bank=receiver_bank['code'],
            shipment_date=format_swift_date(random.randint(30, 90)),
            goods_description=goods,
            partial_allowed=random.choice(["ALLOWED", "NOT ALLOWED"]),
            trans_allowed=random.choice(["ALLOWED", "NOT ALLOWED"])
        )

        messages.append({
            "messageType": "MT710",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def upload_message(message):
    """Upload a single message to the API"""
    try:
        response = requests.post(
            f"{API_BASE_URL}/messages",
            json=message,
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error uploading message: {e}")
        return None


def main():
    print("=" * 60)
    print("SWIFT Message Dataset Generator and Loader")
    print("=" * 60)
    print()

    # Generate messages - INCREASED DATASET SIZE
    print("Generating SWIFT messages...")
    mt700_messages = generate_mt700_messages(200)
    mt710_messages = generate_mt710_messages(100)

    all_messages = mt700_messages + mt710_messages
    print(f"Generated {len(all_messages)} messages:")
    print(f"  - MT700: {len(mt700_messages)}")
    print(f"  - MT710: {len(mt710_messages)}")
    print()

    # Upload messages
    print("Uploading messages to the system...")
    print("-" * 60)

    success_count = 0
    fail_count = 0

    for i, message in enumerate(all_messages, 1):
        print(f"[{i}/{len(all_messages)}] Uploading {message['messageType']} from {message['senderId']}...", end=" ")

        result = upload_message(message)
        if result:
            print("✓ Success")
            success_count += 1
        else:
            print("✗ Failed")
            fail_count += 1

    print("-" * 60)
    print()
    print("Upload Summary:")
    print(f"  Success: {success_count}")
    print(f"  Failed:  {fail_count}")
    print()

    if success_count > 0:
        print("✓ Messages uploaded successfully!")
        print()
        print("Next steps:")
        print("1. Open http://localhost:3000 in your browser")
        print("2. Go to the Templates page")
        print("3. Click 'Extract Templates' to cluster and create templates")
        print("4. View the generated templates and match new transactions")

    print()
    print("=" * 60)


if __name__ == "__main__":
    main()
