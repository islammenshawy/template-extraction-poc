#!/usr/bin/env python3
"""
Enhanced SWIFT Message Dataset Generator
Generates high-quality, realistic SWIFT messages across multiple message types:
- MT103 (Customer Credit Transfers)
- MT202 (Bank-to-Bank Transfers)
- MT700 (Documentary Credits - Issue)
- MT710 (Documentary Credits - Advice of Third Bank)
- MT760 (Guarantees)
- MT799 (Free Format Messages)
"""

import requests
import json
import random
from datetime import datetime, timedelta

API_BASE_URL = "http://localhost:8080/api/v2"
AUTH_API_URL = "http://localhost:8080/api/auth"

# Global variable to store JWT token
JWT_TOKEN = None

# Enhanced bank list with more global coverage
BANKS = [
    # US Banks
    {"code": "CITIUS33", "name": "Citibank N.A.", "city": "New York", "country": "USA"},
    {"code": "CHASUS33", "name": "JPMorgan Chase", "city": "New York", "country": "USA"},
    {"code": "BOFAUS3N", "name": "Bank of America", "city": "San Francisco", "country": "USA"},
    {"code": "HSBCUS33", "name": "HSBC Bank USA", "city": "New York", "country": "USA"},
    {"code": "WFBIUS6S", "name": "Wells Fargo Bank", "city": "San Francisco", "country": "USA"},

    # European Banks
    {"code": "DEUTDEFF", "name": "Deutsche Bank AG", "city": "Frankfurt", "country": "Germany"},
    {"code": "COBADEFF", "name": "Commerzbank AG", "city": "Frankfurt", "country": "Germany"},
    {"code": "BNPAFRPP", "name": "BNP Paribas", "city": "Paris", "country": "France"},
    {"code": "CRLYFRPP", "name": "Credit Lyonnais", "city": "Paris", "country": "France"},
    {"code": "BARCGB22", "name": "Barclays Bank PLC", "city": "London", "country": "UK"},
    {"code": "HSBCGB2L", "name": "HSBC UK Bank PLC", "city": "London", "country": "UK"},
    {"code": "NWBKGB2L", "name": "National Westminster Bank", "city": "London", "country": "UK"},
    {"code": "UBSWCHZH", "name": "UBS Switzerland AG", "city": "Zurich", "country": "Switzerland"},
    {"code": "CRESCHZZ", "name": "Credit Suisse AG", "city": "Zurich", "country": "Switzerland"},

    # Asian Banks
    {"code": "SCBLSGSG", "name": "Standard Chartered Bank", "city": "Singapore", "country": "Singapore"},
    {"code": "DBSSSGSG", "name": "DBS Bank Ltd", "city": "Singapore", "country": "Singapore"},
    {"code": "OCBCSGSG", "name": "Oversea-Chinese Banking Corp", "city": "Singapore", "country": "Singapore"},
    {"code": "MHCBJPJT", "name": "Mizuho Bank Ltd", "city": "Tokyo", "country": "Japan"},
    {"code": "BOTKJPJT", "name": "MUFG Bank Ltd", "city": "Tokyo", "country": "Japan"},
    {"code": "SMITJPJT", "name": "Sumitomo Mitsui Banking Corp", "city": "Tokyo", "country": "Japan"},
    {"code": "HSBCHKHH", "name": "HSBC Hong Kong", "city": "Hong Kong", "country": "Hong Kong"},
    {"code": "BKCHHKHH", "name": "Bank of China Hong Kong", "city": "Hong Kong", "country": "Hong Kong"},
    {"code": "ICBKCNBJ", "name": "Industrial and Commercial Bank of China", "city": "Beijing", "country": "China"},
    {"code": "ABOCCNBJ", "name": "Agricultural Bank of China", "city": "Beijing", "country": "China"},
    {"code": "AXISINBB", "name": "Axis Bank Ltd", "city": "Mumbai", "country": "India"},
    {"code": "HDFCINBB", "name": "HDFC Bank Ltd", "city": "Mumbai", "country": "India"},

    # Middle East Banks
    {"code": "NBADAEAA", "name": "National Bank of Abu Dhabi", "city": "Abu Dhabi", "country": "UAE"},
    {"code": "EBILAEAD", "name": "Emirates NBD", "city": "Dubai", "country": "UAE"},
]

# Enhanced company list with more variety
COMPANIES = [
    "ABC Trading Corporation", "Global Imports Limited", "Tech Solutions Incorporated",
    "International Exports SA", "Pacific Trading Company", "Euro Commerce GmbH",
    "Asia Trade Partners Limited", "Atlantic Shipping Inc", "Continental Trade Limited",
    "Universal Logistics Corporation", "Metro Trading LLC", "Prime Commodities Inc",
    "Eastern Goods Company", "Western Supply Chain Solutions", "Nordic Exports AS",
    "Southern Imports Limited", "Midwest Manufacturing Corp", "Coastal Distributors Inc",
    "Highland Trade Group", "Valley Commerce Corporation", "Summit Trading Partners",
    "Apex International Trading", "Zenith Exports Limited", "Phoenix Import Export",
    "Crown Commodities Inc", "Diamond Trade Solutions", "Elite Global Trading",
    "Fusion Commerce Group", "Genesis Trade Partners", "Horizon International"
]

# More detailed goods descriptions
GOODS_DETAILED = [
    {"name": "Electronic Components", "desc": "Microprocessors, semiconductors and integrated circuits", "hs": "8542"},
    {"name": "Textile Materials", "desc": "Cotton fabrics, synthetic yarns and textile machinery", "hs": "5201"},
    {"name": "Machinery Parts", "desc": "Industrial machinery components and spare parts", "hs": "8483"},
    {"name": "Computer Equipment", "desc": "Desktop computers, laptops and servers", "hs": "8471"},
    {"name": "Automotive Parts", "desc": "Engine components, transmissions and electrical systems", "hs": "8708"},
    {"name": "Medical Devices", "desc": "Diagnostic equipment and surgical instruments", "hs": "9018"},
    {"name": "Agricultural Products", "desc": "Wheat, corn and soybean commodities", "hs": "1001"},
    {"name": "Pharmaceutical Products", "desc": "Active pharmaceutical ingredients and finished medicines", "hs": "3004"},
    {"name": "Industrial Chemicals", "desc": "Organic chemicals and chemical compounds", "hs": "2905"},
    {"name": "Consumer Electronics", "desc": "Smartphones, tablets and wearable devices", "hs": "8517"},
]

CURRENCIES = ["USD", "EUR", "GBP", "CHF", "JPY", "SGD", "AED", "CNY"]

PORTS = [
    "Shanghai, China", "Singapore", "Rotterdam, Netherlands", "Hamburg, Germany",
    "Los Angeles, USA", "Long Beach, USA", "Hong Kong", "Dubai, UAE",
    "Tokyo, Japan", "Mumbai, India", "London, UK", "New York, USA"
]


def format_swift_date(days_ahead=0):
    """Format date in SWIFT format (YYMMDD)"""
    return (datetime.now() + timedelta(days=days_ahead)).strftime("%y%m%d")


def format_full_swift_date(days_ahead=0):
    """Format date in full SWIFT format (YYYYMMDD)"""
    return (datetime.now() + timedelta(days=days_ahead)).strftime("%Y%m%d")


def generate_reference(prefix_type=None):
    """Generate a unique reference number"""
    if prefix_type is None:
        prefix_type = random.choice(["LC", "DOC", "REF", "CR", "TRF", "GTY"])

    date_format = datetime.now().strftime('%Y%m%d')
    return f"{prefix_type}{date_format}{random.randint(100000, 999999)}"


def generate_uetr():
    """Generate a Unique End-to-End Transaction Reference (UETR)"""
    import uuid
    return str(uuid.uuid4())


def generate_amount(min_amount=10000, max_amount=999999):
    """Generate a random amount with proper formatting"""
    amount = random.randint(min_amount, max_amount)
    cents = random.randint(0, 99)
    return f"{amount},{cents:02d}"


# MT103 Templates (Customer Credit Transfers)
MT103_TEMPLATES = [
    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I103{receiver_bank}XXXXN}}{{4:
:20:{transaction_ref}
:23B:CRED
:32A:{value_date}{currency}{amount}
:33B:{currency}{amount}
:50K:/{ordering_account}
{ordering_customer}
{ordering_address}
:52A:{ordering_bank}
:57A:{beneficiary_bank}
:59:/{beneficiary_account}
{beneficiary_name}
{beneficiary_address}
:70:{payment_details}
:71A:SHA
:77B:/UETR/{uetr}
-}}""",

    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I103{receiver_bank}XXXXN}}{{4:
:20:{transaction_ref}
:23B:CRED
:23E:SDVA
:32A:{value_date}{currency}{amount}
:50K:/{ordering_account}
{ordering_customer}
{ordering_address}
:52D:{ordering_bank_name}
{ordering_bank_address}
:56A:{intermediary_bank}
:57A:{beneficiary_bank}
:59:/{beneficiary_account}
{beneficiary_name}
{beneficiary_address}
:70:{payment_details}
INVOICE {invoice_ref}
:71A:OUR
:72:/INS/{intermediary_bank}
//BENEFICIARY BANK DETAILS
:77B:/UETR/{uetr}
-}}"""
]

# MT202 Templates (Bank-to-Bank Transfers)
MT202_TEMPLATE = """{{1:F01{sender_bank}AXXX0000000000}}{{2:I202{receiver_bank}XXXXN}}{{4:
:20:{transaction_ref}
:21:{related_ref}
:32A:{value_date}{currency}{amount}
:52A:{ordering_bank}
:53A:{sender_correspondent}
:54A:{receiver_correspondent}
:56A:{intermediary_bank}
:57A:{beneficiary_bank}
:58A:{beneficiary_institution}
:72:/INS/PAYMENT OF {payment_purpose}
/ACC/{account_ref}
-}}"""

# MT700 Templates (Documentary Credits)
MT700_TEMPLATES = [
    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I700{receiver_bank}XXXXN}}{{4:
:27:1/1
:40A:IRREVOCABLE
:20:{lc_number}
:31C:{issue_date}
:31D:{place_date}
:51A:{applicant_bank}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39A:APPROXIMATELY 05 PCT MORE OR LESS ACCEPTABLE
:39B:MAXIMUM CREDIT AMOUNT
:41A:{available_bank}
:42C:DRAFTS AT SIGHT
:42D:BY PAYMENT
:42P:DEFERRED PAYMENT
:42M:MIXED PAYMENT
:43P:PARTIAL SHIPMENTS {partial_shipment}
:43T:TRANSHIPMENT {transhipment}
:44E:{loading_port}
:44F:{discharge_port}
:44C:LATEST DATE OF SHIPMENT {shipment_date}
:44D:{goods_description}
HS CODE: {hs_code}
QUANTITY: {quantity} UNITS
:45A:+ SIGNED COMMERCIAL INVOICE IN 3 COPIES INDICATING
L/C NUMBER AND CONTRACT NUMBER
+ FULL SET 3/3 ORIGINAL CLEAN ON BOARD OCEAN BILLS
OF LADING MADE OUT TO ORDER OF {issuing_bank}
MARKED FREIGHT {freight_terms} AND NOTIFY APPLICANT
+ PACKING LIST IN 3 COPIES
+ CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE
+ INSURANCE POLICY OR CERTIFICATE IN DUPLICATE FOR
110 PCT OF INVOICE VALUE COVERING ALL RISKS AND WAR
RISKS SHOWING CLAIMS PAYABLE IN {country_currency}
:46A:+ THIRD PARTY DOCUMENTS NOT ACCEPTABLE
+ ALL DOCUMENTS MUST BE IN ENGLISH
+ DOCUMENTS MUST BE PRESENTED WITHIN 21 DAYS AFTER
SHIPMENT DATE BUT WITHIN L/C VALIDITY
:47A:+ INSURANCE TO BE EFFECTED BY BENEFICIARY
+ SHIPMENT TO BE EFFECTED IN CONTAINERS ONLY
+ ALL DOCUMENTS MUST INDICATE L/C NUMBER
+ BENEFICIARY CERTIFICATE CERTIFYING THAT ONE SET
OF NON NEGOTIABLE DOCUMENTS HAVE BEEN SENT TO
APPLICANT WITHIN 7 DAYS FROM SHIPMENT DATE
:71B:ALL BANKING CHARGES OUTSIDE {issuing_country} ARE FOR
BENEFICIARY ACCOUNT
:48:DOCUMENTS MUST BE PRESENTED FOR PAYMENT WITHIN 15
DAYS AFTER SHIPMENT DATE BUT WITHIN L/C VALIDITY
:49:CONFIRM
:53A:{reimbursing_bank}
:78:PLEASE ADVISE BENEFICIARY OF THIS CREDIT WITHOUT
ADDING YOUR CONFIRMATION
:57A:{advising_bank}
-}}""",

    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I700{receiver_bank}XXXXN}}{{4:
:27:1/1
:40A:IRREVOCABLE
:20:{lc_number}
:31C:{issue_date}
:40E:UCP LATEST VERSION
:31D:{place_date}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39C:ADDITIONAL AMOUNTS COVERED: {additional_amount} MAX
:41D:{available_bank}
:42C:AT SIGHT
:42D:BY NEGOTIATION
:43P:PARTIAL SHIPMENTS {partial_shipment}
:43T:TRANSHIPMENT {transhipment}
:44A:{loading_port}
:44B:{discharge_port}
:44C:{shipment_date}
:44D:{goods_description}
CONTRACT NUMBER: {contract_no}
:45A:SIGNED COMMERCIAL INVOICE IN TRIPLICATE
FULL SET CLEAN ON BOARD BILL OF LADING
CERTIFICATE OF ORIGIN
PACKING LIST
:46A:ALL DOCUMENTS TO BE ISSUED IN ENGLISH
DOCUMENTS MUST BE PRESENTED WITHIN 15 DAYS OF SHIPMENT
:47A:SHIPMENT TERMS: {incoterm}
ALL DOCUMENTS MUST INDICATE L/C NUMBER AND DATE
:71B:ALL CHARGES OUTSIDE {currency} FOR BENEFICIARY ACCOUNT
:48:PRESENTATION PERIOD 21 DAYS
-}}"""
]

# MT710 Template (Advice of Third Bank)
MT710_TEMPLATE = """{{1:F01{sender_bank}AXXX0000000000}}{{2:I710{receiver_bank}XXXXN}}{{4:
:27:1/1
:40A:IRREVOCABLE
:20:{lc_number}
:21:{related_lc}
:23:ADVICE OF DOCUMENTARY CREDIT
:31C:{issue_date}
:31D:{place_date}
:52A:{issuing_bank}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:41A:{available_bank}
:42C:DRAFTS AT SIGHT
:42D:BY PAYMENT
:43P:PARTIAL SHIPMENTS {partial_shipment}
:43T:TRANSHIPMENT {transhipment}
:44A:{loading_port}
:44B:{discharge_port}
:44C:{shipment_date}
:44D:{goods_description}
:45A:COMMERCIAL INVOICE IN TRIPLICATE
FULL SET CLEAN ON BOARD BILL OF LADING
CERTIFICATE OF ORIGIN
PACKING LIST
:46A:DOCUMENTS TO BE PRESENTED WITHIN 21 DAYS OF SHIPMENT
:47A:BENEFICIARY TO PROVIDE CERTIFICATE OF COMPLIANCE
:71B:ALL CHARGES FOR BENEFICIARY ACCOUNT
:49:WITHOUT OUR CONFIRMATION
:53A:{reimbursing_bank}
:72:SENDER TO RECEIVER INFORMATION:
THIS ADVICE IS SUBJECT TO UCP LATEST VERSION
-}}"""

# MT760 Template (Guarantees)
MT760_TEMPLATES = [
    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I760{receiver_bank}XXXXN}}{{4:
:27:1/1
:20:{guarantee_ref}
:23:ISSUANCE OF GUARANTEE
:30:{issue_date}
:31C:{expiry_date}
:51A:{applicant_bank}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:39A:GUARANTEE AMOUNT MAY NOT EXCEED {currency}{amount}
:40A:IRREVOCABLE STANDBY LETTER OF CREDIT
:41A:{available_bank}
:42C:AT SIGHT
:42D:BY PAYMENT
:77C:WE HEREBY ISSUE IN YOUR FAVOR OUR IRREVOCABLE STANDBY
LETTER OF CREDIT NO {guarantee_ref} FOR ACCOUNT OF
{applicant_name} IN THE AMOUNT OF {currency}{amount}
AVAILABLE BY YOUR DRAFT AT SIGHT ON US ACCOMPANIED BY
YOUR SIGNED AND DATED STATEMENT READING AS FOLLOWS:
WE CERTIFY THAT {applicant_name} HAS FAILED TO FULFILL
THEIR OBLIGATIONS UNDER CONTRACT NO {contract_no}
DATED {contract_date} AND THE AMOUNT CLAIMED REPRESENTS
THE ACTUAL DAMAGES SUFFERED BY US.
THIS STANDBY CREDIT IS SUBJECT TO ISP98 AND EXPIRES ON
{expiry_date} AT OUR COUNTERS.
:71B:ALL CHARGES ARE FOR ACCOUNT OF APPLICANT
:72:/REGARD/CONTRACT {contract_no}
/BENEF/BENEFICIARY BANK DETAILS
-}}""",

    """{{1:F01{sender_bank}AXXX0000000000}}{{2:I760{receiver_bank}XXXXN}}{{4:
:27:1/1
:20:{guarantee_ref}
:30:{issue_date}
:31C:{expiry_date}
:50:{applicant_name}
{applicant_address}
:59:{beneficiary_name}
{beneficiary_address}
:32B:{currency}{amount}
:40A:IRREVOCABLE
:77C:WE HEREBY ISSUE OUR IRREVOCABLE BANK GUARANTEE
NUMBER {guarantee_ref} FOR THE ACCOUNT OF {applicant_name}
IN FAVOR OF {beneficiary_name} FOR AN AMOUNT NOT EXCEEDING
{currency}{amount} TO GUARANTEE {guarantee_purpose}
UNDER CONTRACT NUMBER {contract_no} DATED {contract_date}
THIS GUARANTEE IS VALID UNTIL {expiry_date} AND SHALL BE
AUTOMATICALLY CANCELLED ON THAT DATE WITHOUT FURTHER NOTICE
CLAIMS UNDER THIS GUARANTEE MUST BE RECEIVED BY US IN
WRITING STATING THAT THE APPLICANT HAS FAILED TO FULFILL
THEIR CONTRACTUAL OBLIGATIONS
THIS GUARANTEE IS SUBJECT TO URDG 758
-}}"""
]

# MT799 Template (Free Format)
MT799_TEMPLATE = """{{1:F01{sender_bank}AXXX0000000000}}{{2:I799{receiver_bank}XXXXN}}{{4:
:20:{message_ref}
:21:{related_ref}
:79:TO: {receiver_name}
FROM: {sender_name}
RE: {subject}

{message_body}

REGARDS,
{sender_signature}
-}}"""


def generate_mt103_messages(count=50):
    """Generate realistic MT103 customer credit transfer messages"""
    messages = []

    for i in range(count):
        template = random.choice(MT103_TEMPLATES)
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        ordering_customer = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != ordering_customer])
        currency = random.choice(CURRENCIES)

        # Determine if we need intermediary bank
        use_intermediary = "intermediary_bank" in template
        intermediary_bank = random.choice([b for b in BANKS if b not in [sender_bank, receiver_bank]]) if use_intermediary else None

        params = {
            "sender_bank": sender_bank['code'],
            "receiver_bank": receiver_bank['code'],
            "transaction_ref": generate_reference("TRF"),
            "value_date": format_full_swift_date(random.randint(0, 5)),
            "currency": currency,
            "amount": generate_amount(50000, 2000000),
            "ordering_account": f"{random.randint(100000000, 999999999):09d}",
            "ordering_customer": ordering_customer,
            "ordering_address": f"{random.randint(100, 999)} Business Plaza, {sender_bank['city']}, {sender_bank['country']}",
            "ordering_bank": sender_bank['code'],
            "ordering_bank_name": sender_bank['name'],
            "ordering_bank_address": f"{sender_bank['city']}, {sender_bank['country']}",
            "beneficiary_bank": receiver_bank['code'],
            "beneficiary_account": f"{random.randint(100000000, 999999999):09d}",
            "beneficiary_name": beneficiary,
            "beneficiary_address": f"{random.randint(100, 999)} Commerce Street, {receiver_bank['city']}, {receiver_bank['country']}",
            "payment_details": f"Payment for {random.choice(['invoice', 'contract', 'services', 'goods'])} - Ref: {generate_reference()}",
            "invoice_ref": generate_reference("INV"),
            "uetr": generate_uetr(),
        }

        if use_intermediary:
            params["intermediary_bank"] = intermediary_bank['code']

        message = template.format(**params)

        messages.append({
            "messageType": "MT103",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt202_messages(count=30):
    """Generate realistic MT202 bank-to-bank transfer messages"""
    messages = []

    for i in range(count):
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        intermediary_bank = random.choice([b for b in BANKS if b not in [sender_bank, receiver_bank]])
        currency = random.choice(CURRENCIES)

        message = MT202_TEMPLATE.format(
            sender_bank=sender_bank['code'],
            receiver_bank=receiver_bank['code'],
            transaction_ref=generate_reference("COV"),
            related_ref=generate_reference("TRF"),
            value_date=format_full_swift_date(random.randint(0, 3)),
            currency=currency,
            amount=generate_amount(100000, 5000000),
            ordering_bank=sender_bank['code'],
            sender_correspondent=sender_bank['code'],
            receiver_correspondent=receiver_bank['code'],
            intermediary_bank=intermediary_bank['code'],
            beneficiary_bank=receiver_bank['code'],
            beneficiary_institution=receiver_bank['code'],
            payment_purpose=random.choice(["INTERBANK TRANSFER", "COVER PAYMENT", "NOSTRO SETTLEMENT"]),
            account_ref=f"{random.randint(100000000, 999999999):09d}"
        )

        messages.append({
            "messageType": "MT202",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt700_messages(count=100):
    """Generate realistic MT700 documentary credit messages"""
    messages = []

    for i in range(count):
        template = random.choice(MT700_TEMPLATES)
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        applicant = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != applicant])
        goods = random.choice(GOODS_DETAILED)
        currency = random.choice(CURRENCIES)

        # Select ports that make sense geographically
        loading_port = random.choice(PORTS)
        discharge_port = random.choice([p for p in PORTS if p != loading_port])

        params = {
            "sender_bank": sender_bank['code'],
            "receiver_bank": receiver_bank['code'],
            "lc_number": generate_reference("LC"),
            "issue_date": format_swift_date(0),
            "place_date": format_swift_date(random.randint(1, 5)),
            "applicant_bank": sender_bank['code'],
            "applicant_name": applicant,
            "applicant_address": f"{random.randint(100, 999)} Industrial Ave, {sender_bank['city']}, {sender_bank['country']}",
            "beneficiary_name": beneficiary,
            "beneficiary_address": f"{random.randint(100, 999)} Trade Center, {receiver_bank['city']}, {receiver_bank['country']}",
            "currency": currency,
            "amount": generate_amount(100000, 5000000),
            "available_bank": receiver_bank['code'],
            "partial_shipment": random.choice(["ALLOWED", "NOT ALLOWED", "PROHIBITED"]),
            "transhipment": random.choice(["ALLOWED", "NOT ALLOWED", "PROHIBITED"]),
            "loading_port": loading_port,
            "discharge_port": discharge_port,
            "shipment_date": format_swift_date(random.randint(30, 90)),
            "goods_description": f"{goods['name'].upper()}\n{goods['desc']}",
            "hs_code": goods['hs'],
            "quantity": random.randint(100, 50000),
            "issuing_bank": sender_bank['name'],
            "freight_terms": random.choice(["PREPAID", "COLLECT"]),
            "country_currency": sender_bank['country'],
            "issuing_country": sender_bank['country'],
            "reimbursing_bank": random.choice([b['code'] for b in BANKS if b != sender_bank]),
            "advising_bank": receiver_bank['code'],
            "additional_amount": generate_amount(10000, 100000),
            "contract_no": generate_reference("CTR"),
            "incoterm": random.choice(["FOB", "CIF", "CFR", "FCA", "DDP", "EXW"])
        }

        message = template.format(**params)

        messages.append({
            "messageType": "MT700",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt710_messages(count=40):
    """Generate realistic MT710 advice messages"""
    messages = []

    for i in range(count):
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        issuing_bank = random.choice([b for b in BANKS if b not in [sender_bank, receiver_bank]])
        applicant = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != applicant])
        goods = random.choice(GOODS_DETAILED)
        currency = random.choice(CURRENCIES)

        message = MT710_TEMPLATE.format(
            sender_bank=sender_bank['code'],
            receiver_bank=receiver_bank['code'],
            lc_number=generate_reference("LC"),
            related_lc=generate_reference("LC"),
            issue_date=format_swift_date(0),
            place_date=format_swift_date(random.randint(1, 3)),
            issuing_bank=issuing_bank['code'],
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Corporate Blvd, {issuing_bank['city']}",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Export Lane, {receiver_bank['city']}",
            currency=currency,
            amount=generate_amount(50000, 3000000),
            available_bank=receiver_bank['code'],
            partial_shipment=random.choice(["ALLOWED", "NOT ALLOWED"]),
            transhipment=random.choice(["ALLOWED", "NOT ALLOWED"]),
            loading_port=random.choice(PORTS),
            discharge_port=random.choice(PORTS),
            shipment_date=format_swift_date(random.randint(30, 90)),
            goods_description=f"{goods['name']} - {goods['desc']}",
            reimbursing_bank=random.choice([b['code'] for b in BANKS if b != sender_bank])
        )

        messages.append({
            "messageType": "MT710",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt760_messages(count=30):
    """Generate realistic MT760 guarantee messages"""
    messages = []

    for i in range(count):
        template = random.choice(MT760_TEMPLATES)
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])
        applicant = random.choice(COMPANIES)
        beneficiary = random.choice([c for c in COMPANIES if c != applicant])
        currency = random.choice(CURRENCIES)

        message = template.format(
            sender_bank=sender_bank['code'],
            receiver_bank=receiver_bank['code'],
            guarantee_ref=generate_reference("GTY"),
            issue_date=format_swift_date(0),
            expiry_date=format_swift_date(random.randint(180, 365)),
            applicant_bank=sender_bank['code'],
            applicant_name=applicant,
            applicant_address=f"{random.randint(100, 999)} Business Park, {sender_bank['city']}",
            beneficiary_name=beneficiary,
            beneficiary_address=f"{random.randint(100, 999)} Financial District, {receiver_bank['city']}",
            currency=currency,
            amount=generate_amount(100000, 10000000),
            available_bank=receiver_bank['code'],
            contract_no=generate_reference("CTR"),
            contract_date=format_swift_date(-random.randint(30, 90)),
            guarantee_purpose=random.choice([
                "PERFORMANCE OF CONTRACT",
                "ADVANCE PAYMENT",
                "BID BOND",
                "RETENTION MONEY"
            ])
        )

        messages.append({
            "messageType": "MT760",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def generate_mt799_messages(count=20):
    """Generate realistic MT799 free format messages"""
    messages = []

    subjects = [
        "PRE-ADVICE OF DOCUMENTARY CREDIT",
        "CONFIRMATION OF SWIFT TRANSFER",
        "REQUEST FOR CREDIT ASSESSMENT",
        "NOTIFICATION OF UPCOMING TRANSACTION",
        "INQUIRY REGARDING CREDIT FACILITY"
    ]

    for i in range(count):
        sender_bank = random.choice(BANKS)
        receiver_bank = random.choice([b for b in BANKS if b != sender_bank])

        subject = random.choice(subjects)
        ref = generate_reference()

        message_bodies = {
            "PRE-ADVICE OF DOCUMENTARY CREDIT": f"""WE WISH TO INFORM YOU THAT WE WILL BE ISSUING A
DOCUMENTARY CREDIT IN YOUR FAVOR WITHIN THE NEXT 3 BUSINESS DAYS.

APPROXIMATE AMOUNT: USD {generate_amount(100000, 1000000)}
BENEFICIARY: {random.choice(COMPANIES)}
APPLICANT: {random.choice(COMPANIES)}

PLEASE PREPARE TO RECEIVE THE FORMAL CREDIT ISSUANCE.
THIS IS FOR YOUR INFORMATION ONLY AND DOES NOT CONSTITUTE
A COMMITMENT ON OUR PART.""",

            "CONFIRMATION OF SWIFT TRANSFER": f"""WE HEREBY CONFIRM RECEIPT OF YOUR MESSAGE REFERENCE {ref}
REGARDING THE TRANSFER OF FUNDS.

WE HAVE PROCESSED THE PAYMENT AND FUNDS SHOULD BE AVAILABLE
IN THE BENEFICIARY ACCOUNT WITHIN 1-2 BUSINESS DAYS.

VALUE DATE: {format_full_swift_date(2)}
AMOUNT: {random.choice(CURRENCIES)} {generate_amount()}

PLEASE CONFIRM RECEIPT OF FUNDS.""",

            "REQUEST FOR CREDIT ASSESSMENT": f"""WE REQUEST YOUR ASSESSMENT ON THE CREDITWORTHINESS OF
{random.choice(COMPANIES)} FOR A POTENTIAL TRADE FINANCE
FACILITY.

PROPOSED FACILITY AMOUNT: {random.choice(CURRENCIES)} {generate_amount(500000, 5000000)}
PURPOSE: IMPORT FINANCING
TENOR: 90-180 DAYS

PLEASE PROVIDE YOUR RESPONSE WITHIN 5 BUSINESS DAYS.""",
        }

        body = message_bodies.get(subject, "PLEASE REFER TO OUR PREVIOUS CORRESPONDENCE.\nFURTHER DETAILS WILL FOLLOW.")

        message = MT799_TEMPLATE.format(
            sender_bank=sender_bank['code'],
            receiver_bank=receiver_bank['code'],
            message_ref=generate_reference("MSG"),
            related_ref=ref,
            receiver_name=receiver_bank['name'],
            sender_name=sender_bank['name'],
            subject=subject,
            message_body=body,
            sender_signature=f"{sender_bank['name']}\nTrade Finance Department"
        )

        messages.append({
            "messageType": "MT799",
            "rawContent": message,
            "senderId": sender_bank['code'],
            "receiverId": receiver_bank['code']
        })

    return messages


def login(email="admin@swift.com", password="SwiftAdmin2024"):
    """Login and get JWT token"""
    global JWT_TOKEN
    try:
        response = requests.post(
            f"{AUTH_API_URL}/login",
            json={"email": email, "password": password},
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        response.raise_for_status()
        data = response.json()
        JWT_TOKEN = data.get('token')
        return JWT_TOKEN is not None
    except requests.exceptions.RequestException as e:
        print(f"Error logging in: {e}")
        return False


def upload_message(message):
    """Upload a single message to the API"""
    global JWT_TOKEN
    try:
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {JWT_TOKEN}"
        }
        response = requests.post(
            f"{API_BASE_URL}/messages",
            json=message,
            headers=headers,
            timeout=10
        )
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        print(f"Error uploading message: {e}")
        return None


def main():
    print("=" * 70)
    print("Enhanced SWIFT Message Dataset Generator")
    print("High-Quality Multi-Type Message Loader")
    print("=" * 70)
    print()

    # Login first
    print("Authenticating with API...")
    if not login():
        print("✗ Failed to authenticate. Please check credentials.")
        return
    print("✓ Authentication successful")
    print()

    # Generate messages across all types
    print("Generating SWIFT messages across multiple message types...")
    print()

    mt103_messages = generate_mt103_messages(50)
    print(f"✓ Generated {len(mt103_messages)} MT103 (Customer Credit Transfer) messages")

    mt202_messages = generate_mt202_messages(30)
    print(f"✓ Generated {len(mt202_messages)} MT202 (Bank-to-Bank Transfer) messages")

    mt700_messages = generate_mt700_messages(100)
    print(f"✓ Generated {len(mt700_messages)} MT700 (Documentary Credit) messages")

    mt710_messages = generate_mt710_messages(40)
    print(f"✓ Generated {len(mt710_messages)} MT710 (Third Bank Advice) messages")

    mt760_messages = generate_mt760_messages(30)
    print(f"✓ Generated {len(mt760_messages)} MT760 (Guarantee) messages")

    mt799_messages = generate_mt799_messages(20)
    print(f"✓ Generated {len(mt799_messages)} MT799 (Free Format) messages")

    all_messages = (mt103_messages + mt202_messages + mt700_messages +
                    mt710_messages + mt760_messages + mt799_messages)

    print()
    print(f"Total messages generated: {len(all_messages)}")
    print("=" * 70)
    print()

    # Upload messages
    print("Uploading messages to the system...")
    print("-" * 70)

    success_count = 0
    fail_count = 0

    for i, message in enumerate(all_messages, 1):
        print(f"[{i}/{len(all_messages)}] Uploading {message['messageType']} from {message['senderId']}...", end=" ")

        result = upload_message(message)
        if result:
            print("✓")
            success_count += 1
        else:
            print("✗")
            fail_count += 1

    print("-" * 70)
    print()
    print("=" * 70)
    print("Upload Summary:")
    print("-" * 70)
    print(f"Total Attempted:  {len(all_messages)}")
    print(f"Success:          {success_count} ({success_count/len(all_messages)*100:.1f}%)")
    print(f"Failed:           {fail_count}")
    print()
    print("Message Type Breakdown:")
    print(f"  MT103 (Customer Transfer):     {len(mt103_messages)}")
    print(f"  MT202 (Bank Transfer):         {len(mt202_messages)}")
    print(f"  MT700 (Documentary Credit):    {len(mt700_messages)}")
    print(f"  MT710 (Third Bank Advice):     {len(mt710_messages)}")
    print(f"  MT760 (Guarantees):            {len(mt760_messages)}")
    print(f"  MT799 (Free Format):           {len(mt799_messages)}")
    print("=" * 70)

    if success_count > 0:
        print()
        print("✓ Enhanced dataset uploaded successfully!")
        print()
        print("Next steps:")
        print("1. Open http://localhost:3000 in your browser")
        print("2. Navigate to the Templates page")
        print("3. Click 'Extract Templates' to process messages")
        print("4. View clustered templates by message type")
        print("5. Check Transactions page for matched messages")
        print()

    print("=" * 70)


if __name__ == "__main__":
    main()
