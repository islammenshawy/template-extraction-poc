#!/usr/bin/env python3
"""
Admin User Creation Script - CLI Version
Creates an admin user in MongoDB for the Template Extraction Platform
Usage: python3 create_admin_cli.py <email> <password>
"""

import os
import sys
from datetime import datetime
import bcrypt
from pymongo import MongoClient

def hash_password(password):
    """Hash password using BCrypt (compatible with Spring Security BCrypt)"""
    salt = bcrypt.gensalt(rounds=10)
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    hashed_str = hashed.decode('utf-8')
    if hashed_str.startswith('$2b$'):
        hashed_str = '$2a$' + hashed_str[4:]
    return hashed_str

def validate_email(email):
    """Basic email validation"""
    return '@' in email and '.' in email.split('@')[1]

def validate_password(password):
    """Validate password meets minimum requirements"""
    if len(password) < 8:
        return False, "Password must be at least 8 characters long"
    return True, ""

def main():
    if len(sys.argv) != 3:
        print("Usage: python3 create_admin_cli.py <email> <password>")
        print("Example: python3 create_admin_cli.py admin@example.com MySecurePassword123")
        sys.exit(1)

    email = sys.argv[1]
    password = sys.argv[2]

    print("=" * 60)
    print("Template Extraction Platform - Admin User Creation")
    print("=" * 60)
    print()

    # Validate email
    if not validate_email(email):
        print(f"✗ Error: Invalid email format: {email}")
        sys.exit(1)

    # Validate password
    valid, error_msg = validate_password(password)
    if not valid:
        print(f"✗ Error: {error_msg}")
        sys.exit(1)

    # Get MongoDB connection string from environment or use default
    mongo_uri = os.getenv('MONGODB_URI', 'mongodb://localhost:27017/swift_templates')

    try:
        # Connect to MongoDB
        print(f"Connecting to MongoDB: {mongo_uri.split('@')[-1] if '@' in mongo_uri else mongo_uri}")
        client = MongoClient(mongo_uri)
        db = client.get_database()
        users_collection = db.users

        # Test connection
        client.server_info()
        print("✓ Connected to MongoDB successfully\n")

        # Check if user already exists
        existing_user = users_collection.find_one({"email": email})
        if existing_user:
            print(f"User '{email}' already exists. Updating password...")

        print(f"Creating/updating admin user: {email}")

        # Hash the password
        hashed_password = hash_password(password)

        # Create or update user document
        user_doc = {
            "email": email,
            "password": hashed_password,
            "enabled": True,
            "createdAt": datetime.utcnow(),
            "lastLogin": None
        }

        # Insert or update
        if existing_user:
            users_collection.update_one(
                {"email": email},
                {"$set": user_doc}
            )
            print(f"✓ Admin user '{email}' updated successfully!")
        else:
            users_collection.insert_one(user_doc)
            print(f"✓ Admin user '{email}' created successfully!")

        print("\nYou can now log in to the Template Extraction Platform with these credentials.")
        print("Backend API: http://localhost:8080")
        print("Frontend: http://localhost:3000")

    except Exception as e:
        print(f"\n✗ Error: {str(e)}", file=sys.stderr)
        sys.exit(1)
    finally:
        if 'client' in locals():
            client.close()

if __name__ == "__main__":
    main()
