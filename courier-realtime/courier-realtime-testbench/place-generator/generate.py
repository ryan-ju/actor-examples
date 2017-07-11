import click
import numpy as np
import matplotlib
matplotlib.use('TkAgg')
import matplotlib.pyplot as plt


@click.command()
@click.option(
    '--number',
    '-n',
    default=10,
    type=int,
    help='The number of places to generate')
@click.option(
    '--gridSize',
    '-g',
    'grid_size',
    default=10,
    type=int,
    help='The size of the grid.')
@click.option(
    '--cluster',
    '-c',
    type=(float, float),
    multiple=True,
    help='The cluster centers.  This overrides --cluster-number.')
@click.option(
    '--cluster-number',
    '-cn',
    'cluster_number',
    type=int,
    help='The number of clusters.')
@click.option('--scale', '-s', default=1, type=float)
@click.option('--visual', '-v', is_flag=True)
def main(number, grid_size, cluster, cluster_number, scale, visual):
    """Simple program that greets NAME for a total of COUNT times."""
    if len(cluster) > 0:
        validate(grid_size, cluster)
        centers = list(cluster)
    else:
        if cluster_number is None:
            raise click.BadParameter(
                "Must set --cluster-number if not setting --cluster")
        centers = [
            np.random.uniform(0, grid_size, 2) for i in range(cluster_number)
        ]
    d, r = divmod(number, len(centers))
    nums = [d for i in range(len(centers))]
    for i in range(r):
        nums[i] += 1
    # cs = centers
    cs = [
        move_inside_bound(grid_size,
                          np.random.normal(centers[i], scale, [nums[i], 2]))
        for i in range(len(centers))
    ]
    # Flatten cs
    places = [place for c in cs for place in c]
    # Print to stdout
    for index, place in enumerate(places):
        print("place-{}".format(index), place[0], place[1], sep=",")
    if visual:
        plt.scatter(
            [place[0] for place in places], [place[1] for place in places],
            alpha=0.5,
            s=0.5)
        plt.show()


def validate(grid_size, cluster):
    invalid = [
        c for c in cluster
        if c[0] < 0 or c[0] > grid_size or c[1] < 0 or c[1] > grid_size
    ]
    if len(invalid) > 0:
        raise click.BadParameter("Cluster position must be within grid size")


def move_inside_bound(grid_size, places):
    result = []
    for place in places:
        if place[0] < 0:
            x = -place[0]
        elif place[0] > grid_size:
            x = grid_size - place[0] % grid_size
        else:
            x = place[0]

        if place[1] < 0:
            y = -place[1]
        elif place[1] > grid_size:
            y = grid_size - place[1] % grid_size
        else:
            y = place[1]

        result.append([x, y])

    return result


if __name__ == '__main__':
    main()
