import click
from cassandra.cluster import Cluster
import re

@click.command()
@click.option('--cluster', '-c', multiple=True)
@click.option('--keyspace', '-k', default='courier_realtime', help='The keyspace to use')
@click.option('--replication', '-r', default=1, help='Replication factor for keyspace')
@click.option('--column-family', '-cf', 'cf', default='place', help='The column family name for place data')
@click.option('--file', '-f', required=True, help='Place data file')
@click.option('--delete-before-write', '-d', 'delete', is_flag=True, help='Whether to delete existing data before writing')
def main(cluster, keyspace, replication, cf, file, delete):
	validate_name(keyspace, 'keyspace')
	validate_name(cf, 'column-family')
	if len(cluster) <= 0:
		raise click.BadParameter("Must set at least one --cluster value")
	cluster = Cluster(cluster)
	session = cluster.connect()
	session.execute(f"CREATE KEYSPACE IF NOT EXISTS {keyspace} WITH REPLICATION = {{'class': 'SimpleStrategy', 'replication_factor': {replication}}}")
	session.execute(f"CREATE TABLE IF NOT EXISTS {keyspace}.{cf} (place_id text, longitude decimal, latitude decimal, PRIMARY KEY (place_id))")

	f = open(file, 'r')
	for line in f.read().splitlines():
		parts = line.split(',')
		if len(parts) != 3:
			raise click.UsageError(f"Each line in file must have format place-id,longitude,latitude.  line = {line}")
	f.close()

	if delete:
		session.execute(f"TRUNCATE TABLE {keyspace}.{cf}")

	f = open(file, 'r')
	counter = 0
	for line in f.read().splitlines():
		parts = line.split(',')
		session.execute(f"INSERT INTO {keyspace}.{cf} (place_id, longitude, latitude) VALUES ('{parts[0]}', {parts[1]}, {parts[2]})")
		counter += 1
	print(f"Wrote {counter} records to table")
	f.close()

	cluster.shutdown()

def validate_name(name, name_type):
	if re.fullmatch('[a-z0-9_]+', name) is None:
		raise click.BadParameter("{} must consist of lower case letters, numbers and _ only".format(name_type))


if __name__ == '__main__':
    main()
