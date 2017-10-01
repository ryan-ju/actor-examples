import click
from enum import Enum
import requests

class PriceType(Enum):
    ON_DEMAND = 1
    SPOT = 2

def compute_best_price(region, type, margin):
    on_demand_price = get_on_demand_price(region, type)
    spot_price = get_spot_price(region, type)
    spot_price_with_margin = spot_price + margin
    if on_demand_price > spot_price:
        return (PriceType.SPOT, spot_price, spot_price_with_margin)
    else:
        return (PriceType.ON_DEMAND, on_demand_price)

def get_spot_price(region, type):
    pass

def get_on_demand_price(region, type):
    body = requests.get('pricing.us-east-1.amazonaws.com').json()
    body['regions']['']
    print(response.read().decode())


@click.command()
def main():
    get_on_demand_price("eu-west-1", None)


if __name__ == '__main__':
    main()