import csv
import gzip
import sys
import os

def convert_csv_to_sql_gz(csv_file, output_gz_file, batch_size=5000):
    if not os.path.exists(csv_file):
        print(f"Error: {csv_file} not found.")
        sys.exit(1)

    print(f"Reading {csv_file} and generating {output_gz_file}...")

    # Ensure output directory exists
    os.makedirs(os.path.dirname(output_gz_file), exist_ok=True)

    with open(csv_file, 'r', encoding='utf-8') as f_in, \
         gzip.open(output_gz_file, 'wt', encoding='utf-8') as f_out:
        
        reader = csv.reader(f_in)
        header = next(reader, None) # Skip header (assuming query,count)
        
        insert_prefix = "INSERT INTO search_queries (query, count) VALUES\n"
        
        batch = []
        total_processed = 0

        for row in reader:
            if len(row) < 2:
                continue
                
            query = row[0].strip().replace("'", "''")  # Escape single quotes for SQL
            try:
                count = int(row[1].strip())
            except ValueError:
                continue # Skip invalid counts
                
            if not query:
                continue
                
            batch.append(f"('{query}', {count})")
            total_processed += 1
            
            if len(batch) >= batch_size:
                f_out.write(insert_prefix + ",\n".join(batch) + ";\n\n")
                batch = []
                print(f"Processed {total_processed} rows...", end='\r')

        # Write remaining
        if batch:
            f_out.write(insert_prefix + ",\n".join(batch) + ";\n")
            print(f"Processed {total_processed} rows...", end='\r')

    print(f"\nSuccess! Wrote {total_processed} queries to {output_gz_file}")
    print("When you run 'docker-compose up -d', PostgreSQL will automatically decompress and load this file.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python csv_to_sql.py <input.csv>")
        print("Example: python csv_to_sql.py dataset.csv")
    else:
        input_csv = sys.argv[1]
        output_sql_gz = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'init', '02_data.sql.gz')
        convert_csv_to_sql_gz(input_csv, output_sql_gz)
