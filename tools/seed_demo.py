#!/usr/bin/env python3
"""Optional synthetic screenshot data. Refuses to modify a populated inventory.

Run while the Java application is CLOSED:
    python3 tools/seed_demo.py --database ~/.northstar-scheduler/schedule.db
"""
import argparse
import datetime as dt
import pathlib
import sqlite3


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=pathlib.Path, required=True)
    args = parser.parse_args()
    database = args.database.expanduser().resolve()
    if not database.is_file():
        parser.error('Database does not exist. Run the application and initialize it first.')
    with sqlite3.connect(database) as conn:
        conn.execute('PRAGMA foreign_keys=ON')
        required = ['customers', 'contacts', 'appointments']
        if any(conn.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0] for table in required):
            parser.error('The database already has scheduling data. Refusing to overwrite or mix datasets.')
        contacts = [('Alex Morgan', 'alex@example.test'), ('Jordan Lee', 'jordan@example.test'),
                    ('Taylor Kim', 'taylor@example.test')]
        clients = [
            ('Aster Design Co.', '12 Pine Street', '90210', '555-0101', 'California', 'United States'),
            ('Harborstone Labs', '81 Harbor Avenue', '94110', '555-0102', 'California', 'United States'),
            ('Northwind Workshop', '7 Maple Court', '98101', '555-0103', 'Washington', 'United States'),
            ('Juniper Creative', '226 Oak Way', '10001', '555-0104', 'New York', 'United States')]
        conn.executemany('INSERT INTO contacts(name,email) VALUES (?,?)', contacts)
        conn.executemany('INSERT INTO customers(name,address,postal_code,phone,region,country) VALUES (?,?,?,?,?,?)', clients)
        contact_ids = [x[0] for x in conn.execute('SELECT id FROM contacts ORDER BY id')]
        customer_ids = [x[0] for x in conn.execute('SELECT id FROM customers ORDER BY id')]
        today = dt.datetime.now(dt.timezone.utc).replace(hour=16, minute=0, second=0, microsecond=0)
        events = [
            ('Kickoff meeting', 'Initial goals and timeline', 'Video call', 'Consultation', 1, 0, 0),
            ('Project planning', 'Scope review', 'Office', 'Planning', 2, 1, 1),
            ('Progress review', 'Milestone checkpoint', 'Video call', 'Review', 3, 2, 2),
            ('Delivery discussion', 'Final handoff', 'Office', 'Delivery', 5, 0, 3),
            ('Follow-up call', 'Review next steps', 'Video call', 'Follow-up', 8, 1, 0),
        ]
        for title, description, location, kind, days, contact_idx, customer_idx in events:
            start = today + dt.timedelta(days=days)
            end = start + dt.timedelta(minutes=45)
            conn.execute('''INSERT INTO appointments
                (title,description,location,type,start_utc,end_utc,customer_id,contact_id)
                VALUES (?,?,?,?,?,?,?,?)''',
                (title, description, location, kind, int(start.timestamp()*1000), int(end.timestamp()*1000),
                 customer_ids[customer_idx], contact_ids[contact_idx]))
    print('Created 3 fictional contacts, 4 fictional customers and 5 example appointments.')
    print('You can now reopen Northstar Scheduler to capture portfolio screenshots.')


if __name__ == '__main__':
    main()
