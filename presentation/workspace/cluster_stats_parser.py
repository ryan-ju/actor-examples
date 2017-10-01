import click

@click.command()
@click.pass_context
@click.argument('input')
def main(ctx, input):
    """CLI tool for summing the number of Akka cluster shards per host.  Input is what the courier realtime service websocket returns when sending "report_kinesis_cluster", "report_courier_cluster" or "report_grid_cluster" """
    hosts = input.split('|')
    total_shards = 0
    total = 0
    for host in hosts:
        [ip, shards] = host.split('->')
        ip_splits = ip.split(':', 1)
        if ':' in ip_splits[1]:
            ip = ip_splits[1]
        shard_count = 0
        count = 0
        shard_ids = []
        shards = shards.strip('()').split(',')
        if len(shards) > 0 and len(shards[0]) > 0:
            for shard in shards:
                [shard_id, num] = shard.split(':')
                shard_id = int(shard_id)
                shard_ids.append(shard_id)
                num = int(num)
                count += num
                shard_count += 1
        shard_ids.sort()
        total_shards += shard_count
        total += count
        print(f"{ip}: shards = {shard_count}, entities = {count}")
        print(f"shards = {shard_ids}")
    print(f"total: shards = {total_shards}, entities = {total}")


if __name__ == '__main__':
    main(obj={})