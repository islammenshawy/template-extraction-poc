#!/usr/bin/env python3
"""
Concentrated SWIFT Message Dataset Generator
Generates messages focused on specific trading pairs to enable meaningful clustering
"""

import requests
import json
import random
from datetime import datetime, timedelta

API_BASE_URL = "http://localhost:8080/api/v2"
AUTH_API_URL = "http://localhost:8080/api/auth"

JWT_TOKEN = None

# Define specific trading pairs with realistic scenarios
TRADING_PAIRS = [
    # Major US-Europe pairs
    {"sender": "CITIUS33", "receiver": "DEUTDEFF", "name": "Citibank → Deutsche Bank"},
    {"sender": "CHASUS33", "receiver": "BNPAFRPP", "name": "JPMorgan → BNP Paribas"},
    {"sender": "BOFAUS3N", "receiver": "HSBCGB2L", "name": "BoA → HSBC London"},

    # Major Asia-US pairs
    {"sender": "DBSSSGSG", "receiver": "CITIUS33", "name": "DBS → Citibank"},
    {"sender": "MHCBJPJT", "receiver": "CHASUS33", "name": "Mizuho → JPMorgan"},
    {"sender": "ICBKCNBJ", "receiver": "BOFAUS3N", "name": "ICBC → Bank of America"},

    # Major intra-Asia pairs
    {"sender": "OCBCSGSG", "receiver": "HSBCHKHH", "name": "OCBC → HSBC HK"},
    {"sender": "BOTKJPJT", "receiver": "DBSSSGSG", "name": "MUFG → DBS"},

    # Europe-Asia pairs
    {"sender": "HSBCGB2L", "receiver": "SCBLSGSG", "name": "HSBC London → StanChart SG"},
    {"sender": "DEUTDEFF", "receiver": "OCBCSGSG", "name": "Deutsche Bank → OCBC"},

    # Middle East pairs
    {"sender": "NBADAEAA", "receiver": "CITIUS33", "name": "NBAD → Citibank"},
    {"sender": "EBILAEAD", "receiver": "HSBCGB2L", "name": "Emirates NBD → HSBC London"},
]

COMPANIES = [
    "Global Tech Industries", "International Trade Corp", "Pacific Imports Ltd",
    "Euro Manufacturing GmbH", "Asia Commerce Partners", "Atlantic Shipping Inc",
    "Continental Exports SA", "Universal Logistics LLC", "Prime Trading Company",
    "Eastern Supply Chain Solutions"
]

GOODS_DETAILED = [
    {"name": "Electronic Components", "desc": "Microprocessors, semiconductors and integrated circuits", "hs": "8542"},
    {"name": "Textile Materials", "desc": "Cotton fabrics, synthetic yarns and textile machinery", "hs": "5201"},
    {"name": "Machinery Parts", "desc": "Industrial machinery components and spare parts", "hs": "8483"},
    {"name": "Computer Equipment", "desc": "Desktop computers, laptops and servers", "hs": "8471"},
    {"name": "Automotive Parts", "desc": "Engine components, transmissions and electrical systems", "hs": "8708"},
]

CURRENCIES = ["USD", "EUR", "GBP", "JPY", "SGD"]
PORTS = ["Shanghai", "Singapore", "Rotterdam", "Hamburg", "Los Angeles", "Hong Kong", "Dubai", "Tokyo"]


def format_swift_date(days_ahead=0):
    """Format date as YYMMDD"""
    date = datetime.now() + timedelta(days=days_ahead)
    return date.strftime("%y%m%d")


def generate_reference(prefix):
    """Generate a reference number"""
    return f"{prefix}{random.randint(1000000, 9999999)}"


def generate_amount(min_val, max_val):
    """Generate an amount with 2 decimal places"""
    return f"{random.uniform(min_val, max_val):.2f}"


def login():
    """Authenticate and get JWT token"""
    global JWT_TOKEN

    credentials = {
        "email": "admin@swift.com",
        "password": "SwiftAdmin2024"
    }

    try:
        response = requests.post(f"{AUTH_API_URL}/login", json=credentials)
        if response.status_code == 200:
            JWT_TOKEN = response.json()['token']
            print("✓ Authentication successful")
            return True
        else:
            print(f"✗ Authentication failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"✗ Authentication error: {e}")
        return False


def get_headers():
    """Get headers with JWT token"""
    return {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {JWT_TOKEN}"
    }


# MT700 Template (Documentary Credit)
MT700_TEMPLATE = """{{1:F01{sender_bank}XXXX0000000000}}{{2:I700{receiver_bank}XXXXN}}{{4:
:20:{lc_number}
:23:ISSUANCE
:31C:{issue_date}
:40A:IRREVOCABLE
:20:{lc_number}
:31D:{place_date}
:50:APPLICANT
{applicant_name}
{applicant_address}
:59:BENEFICIARY
{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39A:{percentage}
:41D:{available_bank}
:42C:{draft_date}
:42A:{draft_bank}
:43P:{partial_shipment}
:43T:{transhipment}
:44A:LOADING ON BOARD
:44B:FOR TRANSPORTATION TO
{discharge_port}
:44C:{shipment_date}
:45A:GOODS OR SERVICES
{goods_description}
HS CODE: {hs_code}
:46A:DOCUMENTS REQUIRED
+ SIGNED COMMERCIAL INVOICE IN TRIPLICATE
+ PACKING LIST
+ FULL SET CLEAN ON BOARD BILLS OF LADING
+ CERTIFICATE OF ORIGIN
+ INSURANCE CERTIFICATE OR POLICY
:47A:ADDITIONAL CONDITIONS
+ ALL CHARGES OUTSIDE {issuing_country} ARE FOR BENEFICIARY ACCOUNT
+ DOCUMENTS TO BE PRESENTED WITHIN {presentation_days} DAYS AFTER SHIPMENT
+ REIMBURSEMENT BANK: {reimbursing_bank}
:71B:CHARGES
:48:PERIOD FOR PRESENTATION
{presentation_days} DAYS AFTER SHIPMENT DATE
:49:CONFIRMATION INSTRUCTIONS
WITHOUT
:53A:{advising_bank}
:78:INSTRUCTIONS TO PAY/ACCEPT/NEGOTIATE BANK
DOCUMENTS TO BE FORWARDED IN ONE LOT BY COURIER
:72:SENDER TO RECEIVER INFO
/CONTRACT/{contract_no}
/INCOTERM/{incoterm}
-}}"""

# MT710 Template (Advice of Third Bank)
MT710_TEMPLATE = """{{1:F01{sender_bank}XXXX0000000000}}{{2:I710{receiver_bank}XXXXN}}{{4:
:27:1/1
:20:{lc_number}
:21:{related_lc}
:23:ADVICE
:31C:{issue_date}
:40A:IRREVOCABLE
:31D:{place_date}
:51A:{issuing_bank}
:50:APPLICANT
{applicant_name}
{applicant_address}
:59:BENEFICIARY
{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:41D:{available_bank}
:43P:{partial_shipment}
:43T:{transhipment}
:44A:{loading_port}
:44B:{discharge_port}
:44C:{shipment_date}
:45A:{goods_description}
:46A:DOCUMENTS REQUIRED
+ SIGNED COMMERCIAL INVOICE
+ PACKING LIST
+ FULL SET BILLS OF LADING
+ CERTIFICATE OF ORIGIN
:47A:ADDITIONAL CONDITIONS
ALL BANKING CHARGES OUTSIDE ISSUING BANK COUNTRY FOR BENEFICIARY ACCOUNT
:71B:CHARGES
:53A:{reimbursing_bank}
:72:SENDER TO RECEIVER INFO
OPERATIVE INSTRUMENT
-}}"""

# MT760 Template (Guarantee)
MT760_TEMPLATE = """{{1:F01{sender_bank}XXXX0000000000}}{{2:I760{receiver_bank}XXXXN}}{{4:
:20:{guarantee_ref}
:23:ISSUE
:30:{issue_date}
:31E:{expiry_date}
:52A:{applicant_bank}
:50:PRINCIPAL
{applicant_name}
{applicant_address}
:59:BENEFICIARY
{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:41D:{available_bank}
:72:SENDER TO RECEIVER INFORMATION
/PURPOSE/{guarantee_purpose}
/CONTRACT/{contract_no}
/DATED/{contract_date}
/GUARANTEE TYPE/UNCONDITIONAL AND IRREVOCABLE
/CLAIM DOCS/BENEFICIARY SIGNED STATEMENT
/PARTIAL CLAIMS/ALLOWED
/MULTIPLE CLAIMS/ALLOWED
/CHARGES/ALL CHARGES FOR PRINCIPAL ACCOUNT
/GOVERNING LAW/INTERNATIONAL CHAMBER OF COMMERCE RULES
-}}"""

# MT799 Template (Free Format)
MT799_TEMPLATE = """{{1:F01{sender_bank}XXXX0000000000}}{{2:I799{receiver_bank}XXXXN}}{{4:
:20:{reference}
:21:{related_reference}
:79:FREE FORMAT TEXT
TO: {receiver_bank}
FROM: {sender_bank}
DATE: {message_date}
RE: {subject}

{message_body}

REGARDS,
{sender_name}
{sender_title}
-}}"""


def generate_mt700_messages_for_pair(pair, count=15):
    """Generate MT700 messages for a specific trading pair with variations"""
    messages = []

    # Define consistent base values for this trading pair (80% of messages use these)
    base_applicant = COMPANIES[0]
    base_beneficiary = COMPANIES[1]
    base_goods = GOODS_DETAILED[0]
    base_currency = CURRENCIES[0]
    base_partial = "ALLOWED"
    base_transhipment = "NOT ALLOWED"
    base_presentation_days = "21"

    # Create variations within the same trading pair
    for i in range(count):
        # 80% of messages use consistent values, 20% have variations
        if i < count * 0.8:
            applicant = base_applicant
            beneficiary = base_beneficiary
            goods = base_goods
            currency = base_currency
            partial = base_partial
            transhipment = base_transhipment
            presentation_days = base_presentation_days
        else:  # 20% variation
            applicant = random.choice(COMPANIES[:3])  # Only vary among first 3
            beneficiary = random.choice([c for c in COMPANIES[:3] if c != applicant])
            goods = random.choice(GOODS_DETAILED[:2])  # Only vary among first 2
            currency = random.choice(CURRENCIES[:2])  # Only vary among first 2
            partial = random.choice(["ALLOWED", "NOT ALLOWED"])
            transhipment = random.choice(["ALLOWED", "NOT ALLOWED"])
            presentation_days = random.choice(["15", "21", "30"])

        params = {
            "sender_bank": pair["sender"],
            "receiver_bank": pair["receiver"],
            "lc_number": generate_reference("LC"),
            "issue_date": format_swift_date(0),
            "place_date": format_swift_date(random.randint(3, 7)),
            "applicant_name": applicant,
            "applicant_address": f"{random.randint(100, 999)} Business Ave, New York",
            "beneficiary_name": beneficiary,
            "beneficiary_address": f"{random.randint(100, 999)} Trade St, London",
            "currency": currency,
            "amount": generate_amount(100000, 2000000),
            "percentage": "100",
            "available_bank": pair["receiver"],
            "draft_date": format_swift_date(random.randint(30, 60)),
            "draft_bank": pair["receiver"],
            "partial_shipment": partial,
            "transhipment": transhipment,
            "discharge_port": random.choice(PORTS),
            "shipment_date": format_swift_date(random.randint(30, 60)),
            "goods_description": f"{goods['name']} - {goods['desc']}",
            "hs_code": goods['hs'],
            "issuing_country": "USA",
            "presentation_days": presentation_days,
            "reimbursing_bank": "CITIUS33",
            "advising_bank": pair["receiver"],
            "contract_no": generate_reference("CTR"),
            "incoterm": random.choice(["FOB", "CIF", "CFR"])
        }

        message = MT700_TEMPLATE.format(**params)

        messages.append({
            "messageType": "MT700",
            "rawContent": message,
            "senderId": pair["sender"],
            "receiverId": pair["receiver"]
        })

    return messages


def generate_mt710_messages_for_pair(pair, count=10):
    """Generate MT710 messages for a specific trading pair"""
    messages = []

    # Define consistent base values (80% consistency)
    base_applicant = COMPANIES[0]
    base_beneficiary = COMPANIES[1]
    base_goods = GOODS_DETAILED[0]
    base_currency = CURRENCIES[0]
    base_partial = "ALLOWED"
    base_transhipment = "NOT ALLOWED"

    for i in range(count):
        # 80% use consistent values
        if i < count * 0.8:
            applicant = base_applicant
            beneficiary = base_beneficiary
            goods = base_goods
            currency = base_currency
            partial = base_partial
            transhipment = base_transhipment
        else:
            applicant = random.choice(COMPANIES[:3])
            beneficiary = random.choice([c for c in COMPANIES[:3] if c != applicant])
            goods = random.choice(GOODS_DETAILED[:2])
            currency = random.choice(CURRENCIES[:2])
            partial = random.choice(["ALLOWED", "NOT ALLOWED"])
            transhipment = random.choice(["ALLOWED", "NOT ALLOWED"])

        message = MT710_TEMPLATE.format(
            sender_bank=pair["sender"],
            receiver_bank=pair["receiver"],
            lc_number=generate_reference("LC"),
            related_lc=generate_reference("LC"),
            issue_date=format_swift_date(0),
            place_date=format_swift_date(random.randint(1, 3)),
            issuing_bank=pair["sender"],
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Corporate Blvd",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Export Lane",
            currency=currency,
            amount=generate_amount(50000, 1500000),
            available_bank=pair["receiver"],
            partial_shipment=partial,
            transhipment=transhipment,
            loading_port=random.choice(PORTS),
            discharge_port=random.choice(PORTS),
            shipment_date=format_swift_date(random.randint(30, 90)),
            goods_description=f"{goods['name']} - {goods['desc']}",
            reimbursing_bank=pair["receiver"]
        )

        messages.append({
            "messageType": "MT710",
            "rawContent": message,
            "senderId": pair["sender"],
            "receiverId": pair["receiver"]
        })

    return messages


def generate_mt760_messages_for_pair(pair, count=8):
    """Generate MT760 messages for a specific trading pair"""
    messages = []

    # Define consistent base values (80% consistency)
    base_applicant = COMPANIES[0]
    base_beneficiary = COMPANIES[1]
    base_currency = CURRENCIES[0]
    base_purpose = "PERFORMANCE OF CONTRACT"

    for i in range(count):
        # 80% use consistent values
        if i < count * 0.8:
            applicant = base_applicant
            beneficiary = base_beneficiary
            currency = base_currency
            purpose = base_purpose
        else:
            applicant = random.choice(COMPANIES[:3])
            beneficiary = random.choice([c for c in COMPANIES[:3] if c != applicant])
            currency = random.choice(CURRENCIES[:2])
            purpose = random.choice(["PERFORMANCE OF CONTRACT", "ADVANCE PAYMENT", "RETENTION MONEY"])

        message = MT760_TEMPLATE.format(
            sender_bank=pair["sender"],
            receiver_bank=pair["receiver"],
            guarantee_ref=generate_reference("GTY"),
            issue_date=format_swift_date(0),
            expiry_date=format_swift_date(random.randint(180, 365)),
            applicant_bank=pair["sender"],
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Business Park",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Financial District",
            currency=currency,
            amount=generate_amount(500000, 5000000),
            available_bank=pair["receiver"],
            contract_no=generate_reference("CTR"),
            contract_date=format_swift_date(-random.randint(30, 90)),
            guarantee_purpose=purpose
        )

        messages.append({
            "messageType": "MT760",
            "rawContent": message,
            "senderId": pair["sender"],
            "receiverId": pair["receiver"]
        })

    return messages


def generate_mt799_messages_for_pair(pair, count=5):
    """Generate MT799 messages for a specific trading pair"""
    messages = []

    # Define consistent base subject (80% consistency)
    base_subject = "PRE-ADVICE OF DOCUMENTARY CREDIT"

    subjects = [
        "PRE-ADVICE OF DOCUMENTARY CREDIT",
        "QUERY ON LC TERMS",
        "CONFIRMATION OF PAYMENT ARRANGEMENTS",
        "NOTIFICATION OF SHIPMENT DELAY",
        "REQUEST FOR AMENDMENT"
    ]

    for i in range(count):
        # 80% use consistent subject
        if i < count * 0.8:
            subject = base_subject
        else:
            subject = random.choice(subjects)

        if "PRE-ADVICE" in subject:
            body = f"WE WISH TO INFORM YOU THAT A DOCUMENTARY CREDIT IN YOUR FAVOR\nWILL BE ISSUED SHORTLY FOR APPROXIMATELY {random.choice(CURRENCIES)} {generate_amount(100000, 1000000)}\nFURTHER DETAILS WILL FOLLOW VIA MT700"
        elif "QUERY" in subject:
            body = f"REFERENCE YOUR LC {generate_reference('LC')}\nPLEASE CLARIFY THE FOLLOWING TERMS:\n- SHIPMENT DATE REQUIREMENTS\n- ACCEPTABLE DOCUMENTS\nAWAITING YOUR SWIFT RESPONSE"
        elif "CONFIRMATION" in subject:
            body = f"WE CONFIRM RECEIPT OF YOUR MT760 GUARANTEE {generate_reference('GTY')}\nALL TERMS ARE ACCEPTABLE\nWE WILL PROCEED AS AGREED"
        elif "SHIPMENT" in subject:
            body = f"REGARDING CONTRACT {generate_reference('CTR')}\nSHIPMENT WILL BE DELAYED BY {random.randint(5, 15)} DAYS DUE TO PORT CONGESTION\nREVISED SHIPMENT DATE: {format_swift_date(random.randint(40, 60))}\nPLEASE ADVISE IF AMENDMENT REQUIRED"
        else:
            body = f"WE REQUEST AMENDMENT TO LC {generate_reference('LC')}\nEXTEND SHIPMENT DATE TO {format_swift_date(random.randint(45, 75))}\nAWAIT YOUR CONFIRMATION"

        message = MT799_TEMPLATE.format(
            sender_bank=pair["sender"],
            receiver_bank=pair["receiver"],
            reference=generate_reference("REF"),
            related_reference=generate_reference("LC"),
            message_date=format_swift_date(0),
            subject=subject,
            message_body=body,
            sender_name="TRADE FINANCE DEPARTMENT",
            sender_title="SENIOR MANAGER"
        )

        messages.append({
            "messageType": "MT799",
            "rawContent": message,
            "senderId": pair["sender"],
            "receiverId": pair["receiver"]
        })

    return messages


def upload_message(message):
    """Upload a single message"""
    try:
        response = requests.post(
            f"{API_BASE_URL}/messages",
            headers=get_headers(),
            json=message
        )

        if response.status_code in [200, 201]:
            return True, None
        else:
            return False, f"Status {response.status_code}"
    except Exception as e:
        return False, str(e)


def main():
    """Main execution"""
    print("=" * 60)
    print("CONCENTRATED SWIFT MESSAGE DATASET GENERATOR")
    print("=" * 60)

    if not login():
        print("Failed to authenticate. Exiting.")
        return

    print(f"\nGenerating messages for {len(TRADING_PAIRS)} trading pairs...")
    print("Each pair will have multiple messages per type to enable clustering\n")

    all_messages = []

    # Generate messages for each trading pair
    for pair in TRADING_PAIRS:
        print(f"Generating messages for {pair['name']}...")

        # Generate different message types for each pair
        all_messages.extend(generate_mt700_messages_for_pair(pair, count=15))
        all_messages.extend(generate_mt710_messages_for_pair(pair, count=10))
        all_messages.extend(generate_mt760_messages_for_pair(pair, count=8))
        all_messages.extend(generate_mt799_messages_for_pair(pair, count=5))

    total_messages = len(all_messages)
    print(f"\n✓ Generated {total_messages} messages")
    print(f"  - {len([m for m in all_messages if m['messageType'] == 'MT700'])} MT700 (Documentary Credits)")
    print(f"  - {len([m for m in all_messages if m['messageType'] == 'MT710'])} MT710 (Advice Messages)")
    print(f"  - {len([m for m in all_messages if m['messageType'] == 'MT760'])} MT760 (Guarantees)")
    print(f"  - {len([m for m in all_messages if m['messageType'] == 'MT799'])} MT799 (Free Format)")
    print(f"\nAcross {len(TRADING_PAIRS)} trading pairs")
    print(f"Average {total_messages // len(TRADING_PAIRS)} messages per trading pair")

    print(f"\nUploading {total_messages} messages...")

    success_count = 0
    error_count = 0

    for i, message in enumerate(all_messages, 1):
        success, error = upload_message(message)

        if success:
            success_count += 1
            if i % 50 == 0:
                print(f"  Uploaded {i}/{total_messages} messages...")
        else:
            error_count += 1
            if error_count <= 5:  # Only print first 5 errors
                print(f"  ✗ Failed to upload message {i}: {error}")

    print("\n" + "=" * 60)
    print("UPLOAD COMPLETE")
    print("=" * 60)
    print(f"✓ Successfully uploaded: {success_count}/{total_messages}")
    if error_count > 0:
        print(f"✗ Failed: {error_count}/{total_messages}")

    print("\nNEXT STEPS:")
    print("1. Run template extraction: POST /api/templates/extract")
    print("2. View templates by trading pair in the UI")
    print("3. Check clustered message count on dashboard")


if __name__ == "__main__":
    main()
