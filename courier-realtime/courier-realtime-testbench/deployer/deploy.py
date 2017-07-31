import click
from troposphere import Ref, Template, Tags, Join, Output, Export, Sub, Base64, ImportValue, Parameter
from troposphere.ec2 import VPC, Subnet, NetworkAcl, NetworkAclEntry, InternetGateway, PortRange, \
    VPCGatewayAttachment, RouteTable, Route, SecurityGroup, SecurityGroupRule, SubnetRouteTableAssociation, SubnetNetworkAclAssociation, NetworkInterfaceProperty, Instance
from troposphere.autoscaling import LaunchConfiguration, AutoScalingGroup
from troposphere.elasticloadbalancing import LoadBalancer, Listener, HealthCheck
from troposphere.kinesis import Stream
import boto3
import re

VPC_STACK = 'CourierRealtimeVpcStack'
CASSANDRA_STACK = 'CassandraStack'
KINESIS_STACK = 'KinesisStack'
SERVICE_STACK = 'CourierRealtimeServiceStack'
INJECTOR_STACK = 'InjectorStack'
LOAD_TEST_STACK = 'LoadTestStack'

VPC_NETWORK = '10.0.0.0/16'
SERVICE_NETWORK = '10.0.1.0/24'
CASSANDRA_NETWORK = '10.0.2.0/24'
INJECTOR_NETWORK = '10.0.3.0/24'
LOAD_TEST_NETWORK = '10.0.4.0/24'

SERVICE_INSTANCE = 'c4.large'
CASSANDRA_INSTANCE = 'c4.large'
INJECTOR_INSTANCE = 'c4.large'
LOAD_TEST_INSTANCE = 'c4.large'

SERVICE_AMI = 'ami-c5ee06bc'
CASSANDRA_AMI = 'ami-dc2bcda5'
INJECTOR_AMI = 'ami-854da4fc'
LOAD_TEST_STACK_AMI = 'ami-c5ee06bc'

SERVICE_AKKA_REMOTE_PORT = '2550'
SERVICE_HTTP_PORT = '8080'

KEY_PAIR = 'courier-realtime'

ref_stack_id = Ref('AWS::StackId')


def instance_ip(network, index):
    return re.search(r'(\d+\.\d+\.\d+\.).*', network).group(1) + index


def create_vpc():
    return VPC(
        'CourierRealtimeVpc',
        CidrBlock=VPC_NETWORK,
        EnableDnsSupport=True,
        EnableDnsHostnames=True,
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_cassandra_subnet(vpc):
    return Subnet(
        'CassandraSubnet',
        CidrBlock=CASSANDRA_NETWORK,
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_service_subnet(vpc):
    return Subnet(
        'ServiceSubnet',
        CidrBlock=SERVICE_NETWORK,
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_injector_subnet(vpc):
    return Subnet(
        'InjectorSubnet',
        CidrBlock=INJECTOR_NETWORK,
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_load_test_subnet(vpc):
    return Subnet(
        'LoadTestSubnet',
        CidrBlock=LOAD_TEST_NETWORK,
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_internet_gateway():
    return InternetGateway(
        'InternetGateway',
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_gateway_attachment(vpc, gateway):
    return VPCGatewayAttachment(
        'AttachGateway',
        VpcId=Ref(vpc),
        InternetGatewayId=Ref(gateway)
    )


def create_route_table(vpc):
    return RouteTable(
        'RouteTable',
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )


def create_route(route_table, gateway):
    return Route(
        'Route',
        DependsOn=route_table.title,
        GatewayId=Ref(gateway),
        DestinationCidrBlock='0.0.0.0/0',
        RouteTableId=Ref(route_table)
    )


def create_subnet_route_table_associations(cassandra_subnet, service_subnet, injector_subnet, load_test_subnet, route_table):
    return [
        SubnetRouteTableAssociation(
            'CassandraSubnetRouteTableAssociation',
            SubnetId=Ref(cassandra_subnet),
            RouteTableId=Ref(route_table)
        ),
        SubnetRouteTableAssociation(
            'ServiceSubnetRouteTableAssociation',
            SubnetId=Ref(service_subnet),
            RouteTableId=Ref(route_table)
        ),
        SubnetRouteTableAssociation(
            'InjectorSubnetRouteTableAssociation',
            SubnetId=Ref(injector_subnet),
            RouteTableId=Ref(route_table)
        ),
        SubnetRouteTableAssociation(
            'LoadTestSubnetRouteTableAssociation',
            SubnetId=Ref(load_test_subnet),
            RouteTableId=Ref(route_table)
        )
    ]


def create_acl(vpc, cassandra_subnet, service_subnet, injector_subnet, load_test_subnet, home_ip, office_ip):
    resources = []
    # Cassandra
    # Inbound
    cassandra_acl = NetworkAcl(
        'CassandraAcl',
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )
    resources.append(cassandra_acl)
    resources.append(NetworkAclEntry(
        'CassandraSshInboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='22', To='22'),
        Egress=False,
        RuleAction='allow'
    ))
    # Traffic from service instance to Cassandra
    resources.append(NetworkAclEntry(
        'CassandraInternalInboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='200',
        CidrBlock=VPC_NETWORK,
        Protocol='6',
        PortRange=PortRange(From='1024', To='65535'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'CassandraEphemeralInboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='201',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='32768', To='65535'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'CassandraClientInboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='300',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='9042', To='9042'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'CassandraClientInboundAcl2',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='301',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='9042', To='9042'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'CassandraJmxInboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='400',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'CassandraJmxInboundAcl2',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='401',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    # Outbound
    resources.append(NetworkAclEntry(
        'CassandraOutboundAcl',
        NetworkAclId=Ref(cassandra_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='-1',
        Egress=True,
        RuleAction='allow'
    ))
    resources.append(SubnetNetworkAclAssociation(
        'CassandraAclAssociation',
        NetworkAclId=Ref(cassandra_acl),
        SubnetId=Ref(cassandra_subnet)
    ))
    # Service
    # Inbound
    service_acl = NetworkAcl(
        'ServiceAcl',
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )
    resources.append(service_acl)
    resources.append(NetworkAclEntry(
        'ServiceSshInboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='22', To='22'),
        Egress=False,
        RuleAction='allow'
    ))
    # Return traffic from Cassandra to service instance, and from service instance to ELB node
    resources.append(NetworkAclEntry(
        'ServiceEphemeralInternalInboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='200',
        CidrBlock=VPC_NETWORK,
        Protocol='-1',
        Egress=False,
        RuleAction='allow'
    ))
    # Return traffic from internet to service instance
    resources.append(NetworkAclEntry(
        'ServiceEphemeralInboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='201',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='32768', To='65535'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'ServiceJmxInboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='400',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'ServiceJmxInboundAcl2',
        NetworkAclId=Ref(service_acl),
        RuleNumber='401',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    )),
    resources.append(NetworkAclEntry(
        'ServiceHttpInboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='500',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='80', To='80'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'ServiceHttpInboundAcl2',
        NetworkAclId=Ref(service_acl),
        RuleNumber='501',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='80', To='80'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'ServiceHttpInboundAcl3',
        NetworkAclId=Ref(service_acl),
        RuleNumber='502',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='8080', To='8080'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'ServiceHttpInboundAcl4',
        NetworkAclId=Ref(service_acl),
        RuleNumber='503',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='8080', To='8080'),
        Egress=False,
        RuleAction='allow'
    ))
    # Outbound
    resources.append(NetworkAclEntry(
        'ServiceOutboundAcl',
        NetworkAclId=Ref(service_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='-1',
        Egress=True,
        RuleAction='allow'
    ))
    resources.append(SubnetNetworkAclAssociation(
        'ServiceAclAssociation',
        NetworkAclId=Ref(service_acl),
        SubnetId=Ref(service_subnet)
    ))
    # Injector
    # Inbound
    injector_acl = NetworkAcl(
        'InjectorAcl',
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )
    resources.append(injector_acl)
    resources.append(NetworkAclEntry(
        'InjectorSshInboundAcl',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='22', To='22'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorEphemeralInboundAcl',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='200',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='32768', To='65535'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorJmxInboundAcl',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='400',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorJmxInboundAcl2',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='401',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorHttpInboundAcl',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='500',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='80', To='80'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorHttpInboundAcl2',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='501',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='80', To='80'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorHttpInboundAcl3',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='502',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='8080', To='8080'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'InjectorHttpInboundAcl4',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='503',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='8080', To='8080'),
        Egress=False,
        RuleAction='allow'
    ))
    # Outbound
    resources.append(NetworkAclEntry(
        'InjectorOutboundAcl',
        NetworkAclId=Ref(injector_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='-1',
        Egress=True,
        RuleAction='allow'
    ))
    resources.append(SubnetNetworkAclAssociation(
        'InjectorAclAssociation',
        NetworkAclId=Ref(injector_acl),
        SubnetId=Ref(injector_subnet)
    ))
    # Load test
    # Inbound
    load_test_acl = NetworkAcl(
        'LoadTestAcl',
        VpcId=Ref(vpc),
        Tags=Tags(
            Application=ref_stack_id
        )
    )
    resources.append(load_test_acl)
    resources.append(NetworkAclEntry(
        'LoadTestSshInboundAcl',
        NetworkAclId=Ref(load_test_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='22', To='22'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'LoadTestEphemeralInboundAcl',
        NetworkAclId=Ref(load_test_acl),
        RuleNumber='200',
        CidrBlock='0.0.0.0/0',
        Protocol='6',
        PortRange=PortRange(From='32768', To='65535'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'LoadTestJmxInboundAcl',
        NetworkAclId=Ref(load_test_acl),
        RuleNumber='400',
        CidrBlock=home_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    resources.append(NetworkAclEntry(
        'LoadTestJmxInboundAcl2',
        NetworkAclId=Ref(load_test_acl),
        RuleNumber='401',
        CidrBlock=office_ip + '/32',
        Protocol='6',
        PortRange=PortRange(From='7199', To='7199'),
        Egress=False,
        RuleAction='allow'
    ))
    # Outbound
    resources.append(NetworkAclEntry(
        'LoadTestOutboundAcl',
        NetworkAclId=Ref(load_test_acl),
        RuleNumber='100',
        CidrBlock='0.0.0.0/0',
        Protocol='-1',
        Egress=True,
        RuleAction='allow'
    ))
    resources.append(SubnetNetworkAclAssociation(
        'LoadTestAclAssociation',
        NetworkAclId=Ref(load_test_acl),
        SubnetId=Ref(load_test_subnet)
    ))
    return resources


def create_vpc_stack_outputs(vpc, cassandra_subnet, service_subnet, injector_subnet, load_test_subnet):
    return [
        Output(
            'VpcOutput',
            Description='VPC',
            Value=Ref(vpc),
            Export=Export(
                Sub('${AWS::StackName}-VPC')
            )
        ),
        Output(
            'CassandraSubnetOutput',
            Description='Cassandra subnet',
            Value=Ref(cassandra_subnet),
            Export=Export(
                Sub('${AWS::StackName}-CSD-SUBNET')
            )
        ),
        Output(
            'ServiceSubnetOutput',
            Description='Service subnet',
            Value=Ref(service_subnet),
            Export=Export(
                Sub('${AWS::StackName}-SRV-SUBNET')
            )
        ),
        Output(
            'InjectorSubnetOutput',
            Description='Injector subnet',
            Value=Ref(injector_subnet),
            Export=Export(
                Sub('${AWS::StackName}-INJ-SUBNET')
            )
        ),
        Output(
            'LoadTestSubnetOutput',
            Description='Load test subnet',
            Value=Ref(load_test_subnet),
            Export=Export(
                Sub('${AWS::StackName}-LT-SUBNET')
            )
        )
    ]


def create_cassandra_user_data(seed_id, aws_access_key_param, aws_secret_key_param, seed_nodes_param, bucket):
    if seed_id:
        data = [
            '#!/bin/bash\n',
            'export AWS_ACCESS_KEY_ID=', Ref(aws_access_key_param), '\n',
            'export AWS_SECRET_ACCESS_KEY=', Ref(aws_secret_key_param), '\n',
            'export SEED_NODES=', Ref(seed_nodes_param), '\n',
            # Install cfn-init
            'apt-get update\n',
            'apt-get -y install python-pip\n',
            'pip install https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz\n',
            'cp /usr/local/init/ubuntu/cfn-hup /etc/init.d/cfn-hup \n',
            'chmod +x /etc/init.d/cfn-hup \n',
            'update-rc.d cfn-hup defaults \n',
            'service cfn-hup start \n',
            # Install init scripts
            'mkdir -p /tmp/init\n',
            'cd /tmp/init\n'
            'aws s3 cp s3://', bucket, '/cassandra-config.tar.gz ./\n',
            'tar -zxf cassandra-config.tar.gz\n',
            'cd /tmp/init/cassandra-config\n',
            'chmod +x ./init.sh\n'
            './init.sh\n'
            # Signal stack boot finished
            'cfn-signal -e $? ',
            '--stack ', Ref('AWS::StackName'),
            '--resource ', seed_id, '\n'
        ]
    else:
        data = [
            '#!/bin/bash\n',
            'export AWS_ACCESS_KEY_ID=', Ref(aws_access_key_param), '\n',
            'export AWS_SECRET_ACCESS_KEY=', Ref(aws_secret_key_param), '\n',
            'export SEED_NODES=', Ref(seed_nodes_param), '\n',
            # Install init scripts
            'mkdir -p /tmp/init\n',
            'cd /tmp/init\n'
            'aws s3 cp s3://', bucket, '/cassandra-config.tar.gz ./\n',
            'tar -zxf cassandra-config.tar.gz\n',
            'cd /tmp/init/cassandra-config\n',
            'chmod +x ./init.sh\n'
            './init.sh\n'
        ]
    return Base64(Join('', data))


def create_service_user_data(
        aws_access_key_param,
        aws_secret_key_param,
        datadog_api_key_param,
        seed_nodes_param,
        cassandra_seeds,
        kinesis_stream,
        bucket,
        grid_size,
        shard_nr,
        snapshot_after,
        offline_after_s,
        ws):
    data = [
        '#!/bin/bash\n',
        'export AWS_ACCESS_KEY_ID=', Ref(aws_access_key_param), '\n',
        'export AWS_SECRET_ACCESS_KEY=', Ref(aws_secret_key_param), '\n',
        'export DATADOG_API_KEY=', Ref(datadog_api_key_param), '\n',
        'export SEED_NODES=', Ref(seed_nodes_param), '\n',
        'export CASSANDRA_SEEDS=', cassandra_seeds, '\n',
        'export BUCKET=', bucket, '\n',
        'export KINESIS=', kinesis_stream, '\n',
        'export GRID_SIZE=', grid_size, '\n',
        'export SHARD_NR=', shard_nr, '\n',
        'export SNAPSHOT_AFTER=', snapshot_after, '\n',
        'export OFFLINE_AFTER_S=', offline_after_s, '\n',
        'export WS=', str(ws).lower(), '\n',
        # Install init scripts
        'mkdir -p /tmp/init\n',
        'cd /tmp/init\n'
        'aws s3 cp s3://', bucket, '/service-config.tar.gz ./\n',
        'tar -zxf service-config.tar.gz\n',
        'cd /tmp/init/service-config\n',
        'chmod +x ./init.sh\n'
        './init.sh\n'
    ]
    return Base64(Join('', data))


def create_injector_userdata(
        instance,
        aws_access_key_param,
        aws_secret_key_param,
        kinesis_stream,
        bucket,
        grid_size,
        batch_size,
        batch_timeout_ms,
        courier_nr,
        courier_ping_ms,
        courier_step_size):
    data = [
        '#!/bin/bash\n',
        'export AWS_ACCESS_KEY_ID=', Ref(aws_access_key_param), '\n',
        'export AWS_SECRET_ACCESS_KEY=', Ref(aws_secret_key_param), '\n',
        'export BUCKET=', bucket, '\n',
        'export KINESIS=', kinesis_stream, '\n',
        'export GRID_SIZE=', grid_size, '\n',
        'export BATCH_SIZE=', batch_size, '\n',
        'export BATCH_TIMEOUT_MS=', batch_timeout_ms, '\n',
        'export COURIER_NR=', courier_nr, '\n',
        'export COURIER_PING_MS=', courier_ping_ms, '\n',
        'export COURIER_STEP_SIZE=', courier_step_size, '\n',
        # Install init scripts
        'mkdir -p /tmp/init\n',
        'cd /tmp/init\n'
        'aws s3 cp s3://', bucket, '/injector-config.tar.gz ./\n',
        'tar -zxf injector-config.tar.gz\n',
        'cd /tmp/init/injector-config\n',
        'chmod +x ./init.sh\n'
        './init.sh\n'
        # Signal stack boot finished
        'cfn-signal -e $? ',
        '--stack ', Ref('AWS::StackName'),
        '--resource ', instance, '\n'
    ]
    return Base64(Join('', data))


def create_cassandra_cluster(vpc, seed_nr, instance_type, key_pair, cassandra_subnet, home_ip, office_ip, bucket):
    parameters = []
    resources = []
    outputs = []
    aws_access_key_param = Parameter(
        'AWSAccessKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_access_key_param)
    aws_secret_key_param = Parameter(
        'AWSSecretKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_secret_key_param)
    seed_nodes = ','.join([instance_ip(CASSANDRA_NETWORK, str(i)) for i in range(10, 10 + seed_nr)])
    seed_nodes_param = Parameter(
        'CassandraSeedNodes',
        Type='String',
        Default=seed_nodes
    )
    parameters.append(seed_nodes_param)
    cassandra_sg = SecurityGroup(
        'CassandraSg',
        GroupDescription='Cassandra security group',
        SecurityGroupIngress=[
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='22',
                ToPort='22',
                CidrIp='0.0.0.0/0'
            ),
            # Allow all VPC internal communications
            SecurityGroupRule(
                IpProtocol='-1',
                FromPort='-1',
                ToPort='-1',
                CidrIp='10.0.0.0/16'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='9042',
                ToPort='9042',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='9042',
                ToPort='9042',
                CidrIp=office_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=office_ip + '/32'
            )
        ],
        VpcId=vpc
    )
    resources.append(cassandra_sg)
    ips = []
    # Seed nodes
    for i in range(10, 10 + seed_nr):
        ip = instance_ip(CASSANDRA_NETWORK, str(i))
        ips.append(ip)
        instance = Instance(
            f'CassandraSeed{i}',
            ImageId=CASSANDRA_AMI,
            InstanceType=instance_type,
            KeyName=key_pair,
            NetworkInterfaces=[
                NetworkInterfaceProperty(
                    GroupSet=[Ref(cassandra_sg)],
                    AssociatePublicIpAddress='true',
                    DeviceIndex='0',
                    DeleteOnTermination='true',
                    SubnetId=cassandra_subnet,
                    PrivateIpAddress=ip
                )
            ],
            UserData=create_cassandra_user_data(f'CassandraSeed{i}', aws_access_key_param, aws_secret_key_param, seed_nodes_param, bucket),
            Tags=Tags(
                Name=f'CassandraSeed{i}'
            )
        )
        resources.append(instance)

    # Auto scaling nodes
    launch_config = LaunchConfiguration(
        'CassandraAutoScalingLaunchConfig',
        AssociatePublicIpAddress='true',
        ImageId=CASSANDRA_AMI,
        InstanceType=instance_type,
        SecurityGroups=[Ref(cassandra_sg)],
        KeyName=key_pair,
        UserData=create_cassandra_user_data(
            seed_id=None,
            aws_access_key_param=aws_access_key_param,
            aws_secret_key_param=aws_secret_key_param,
            seed_nodes_param=seed_nodes_param,
            bucket=bucket
        )
    )
    resources.append(launch_config)

    autoscaling = AutoScalingGroup(
        'CassandraAutoScalingGroup',
        LaunchConfigurationName=Ref(launch_config),
        MinSize=1,
        MaxSize=3,
        VPCZoneIdentifier=[cassandra_subnet]
    )
    resources.append(autoscaling)

    outputs.append(
        Output(
            'CassandraSeedsOutput',
            Description='Cassandra seed node private IPs as comma separated list',
            Value=','.join(ips),
            Export=Export(
                Sub('${AWS::StackName}-CSD-SEEDS')
            )
        )
    )

    return parameters, resources, outputs


def create_service_cluster(
        vpc,
        seed_nr,
        instance_type,
        key_pair,
        service_subnet,
        home_ip,
        office_ip,
        bucket,
        kinesis_stream,
        cassandra_seeds,
        grid_size,
        shard_nr,
        snapshot_after,
        offline_after_ms,
        ws):
    parameters = []
    resources = []
    aws_access_key_param = Parameter(
        'AWSAccessKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_access_key_param)
    aws_secret_key_param = Parameter(
        'AWSSecretKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_secret_key_param)
    datadog_api_key_param = Parameter(
        'DatadogApiKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(datadog_api_key_param)
    seed_nodes = ','.join([instance_ip(SERVICE_NETWORK, str(i)) for i in range(10, 10 + seed_nr)])
    seed_nodes_param = Parameter(
        'ServiceSeedNodes',
        Type='String',
        Default=seed_nodes
    )
    parameters.append(seed_nodes_param)
    # Service
    service_sg = SecurityGroup(
        'ServiceSg',
        GroupDescription='Service security group',
        SecurityGroupIngress=[
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='22',
                ToPort='22',
                CidrIp='0.0.0.0/0'
            ),
            # Allow all VPC internal communications
            SecurityGroupRule(
                IpProtocol='-1',
                FromPort='-1',
                ToPort='-1',
                CidrIp='10.0.0.0/16'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=office_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='8080',
                ToPort='8080',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='8080',
                ToPort='8080',
                CidrIp=office_ip + '/32'
            )
        ],
        VpcId=vpc
    )
    resources.append(service_sg)
    instances = []
    for i in range(10, 10 + seed_nr):
        ip = instance_ip(SERVICE_NETWORK, str(i))
        instance = Instance(
            f'ServiceSeed{i}',
            ImageId=SERVICE_AMI,
            InstanceType=instance_type,
            KeyName=key_pair,
            NetworkInterfaces=[
                NetworkInterfaceProperty(
                    GroupSet=[Ref(service_sg)],
                    AssociatePublicIpAddress='true',
                    DeviceIndex='0',
                    DeleteOnTermination='true',
                    SubnetId=service_subnet,
                    PrivateIpAddress=ip
                )
            ],
            UserData=create_service_user_data(
                aws_access_key_param=aws_access_key_param,
                aws_secret_key_param=aws_secret_key_param,
                datadog_api_key_param=datadog_api_key_param,
                seed_nodes_param=seed_nodes_param,
                cassandra_seeds=cassandra_seeds,
                kinesis_stream=kinesis_stream,
                bucket=bucket,
                grid_size=grid_size,
                shard_nr=shard_nr,
                snapshot_after=snapshot_after,
                offline_after_s=offline_after_ms,
                ws=ws
            ),
            Tags=Tags(
                Name=f'ServiceSeed{i}'
            )
        )
        resources.append(instance)
        instances.append(instance)
    # ELB
    elb_sg = SecurityGroup(
        'ServiceElbSg',
        GroupDescription='Service ELB security group',
        SecurityGroupIngress=[
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='22',
                ToPort='22',
                CidrIp='0.0.0.0/0'
            ),
            # Allow all VPC internal communications
            SecurityGroupRule(
                IpProtocol='-1',
                FromPort='-1',
                ToPort='-1',
                CidrIp='10.0.0.0/16'
            ),
            # Http communication only allowed from trusted source
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='80',
                ToPort='80',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='80',
                ToPort='80',
                CidrIp=office_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=office_ip + '/32'
            )
        ],
        VpcId=vpc
    )
    resources.append(elb_sg)
    load_balancer = LoadBalancer(
        'ServiceElb',
        LoadBalancerName='ServiceElb',
        Scheme='internet-facing',
        Listeners=[
            Listener(
                'ServiceHttpsListener',
                LoadBalancerPort=80,
                InstancePort=8080,
                Protocol='HTTP'
            )
        ],
        HealthCheck=HealthCheck(
            'ServiceHealthCheck',
            HealthyThreshold='2',
            Interval='15',
            Target='HTTP:8080/healthcheck',
            Timeout='5',
            UnhealthyThreshold='5'
        ),
        Subnets=[service_subnet],
        SecurityGroups=[Ref(elb_sg)],
        Instances=[Ref(i) for i in instances]
    )
    resources.append(load_balancer)
    # Auto scaling nodes
    launch_config = LaunchConfiguration(
        'ServiceAutoScalingLaunchConfig',
        AssociatePublicIpAddress='true',
        ImageId=SERVICE_AMI,
        InstanceType=instance_type,
        SecurityGroups=[Ref(service_sg)],
        KeyName=key_pair,
        UserData=create_service_user_data(
            aws_access_key_param=aws_access_key_param,
            aws_secret_key_param=aws_secret_key_param,
            datadog_api_key_param=datadog_api_key_param,
            seed_nodes_param=seed_nodes_param,
            cassandra_seeds=cassandra_seeds,
            kinesis_stream=kinesis_stream,
            bucket=bucket,
            grid_size=grid_size,
            shard_nr=shard_nr,
            snapshot_after=snapshot_after,
            offline_after_s=offline_after_ms,
            ws=ws
        )
    )
    resources.append(launch_config)
    autoscaling = AutoScalingGroup(
        'ServiceAutoScalingGroup',
        LaunchConfigurationName=Ref(launch_config),
        MinSize=1,
        MaxSize=3,
        VPCZoneIdentifier=[service_subnet],
        LoadBalancerNames=[Ref(load_balancer)]
    )
    resources.append(autoscaling)
    return parameters, resources


def create_injector(
        vpc,
        instance_type,
        key_pair,
        injector_subnet,
        home_ip,
        office_ip,
        bucket,
        kinesis_stream,
        grid_size,
        batch_size,
        batch_timeout_ms,
        courier_nr,
        courier_ping_ms,
        courier_step_size):
    parameters = []
    resources = []
    aws_access_key_param = Parameter(
        'AWSAccessKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_access_key_param)
    aws_secret_key_param = Parameter(
        'AWSSecretKey',
        Type='String',
        NoEcho=True
    )
    parameters.append(aws_secret_key_param)
    # Service
    injector_sg = SecurityGroup(
        'InjectorSg',
        GroupDescription='Injector security group',
        SecurityGroupIngress=[
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='22',
                ToPort='22',
                CidrIp='0.0.0.0/0'
            ),
            # Allow all VPC internal communications
            SecurityGroupRule(
                IpProtocol='-1',
                FromPort='-1',
                ToPort='-1',
                CidrIp='10.0.0.0/16'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='7199',
                ToPort='7199',
                CidrIp=office_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='80',
                ToPort='80',
                CidrIp=home_ip + '/32'
            ),
            SecurityGroupRule(
                IpProtocol='tcp',
                FromPort='80',
                ToPort='80',
                CidrIp=office_ip + '/32'
            )
        ],
        VpcId=vpc
    )
    resources.append(injector_sg)
    instance = Instance(
        'InjectorInstance',
        ImageId=SERVICE_AMI,
        InstanceType=instance_type,
        KeyName=key_pair,
        NetworkInterfaces=[
            NetworkInterfaceProperty(
                GroupSet=[Ref(injector_sg)],
                AssociatePublicIpAddress='true',
                DeviceIndex='0',
                DeleteOnTermination='true',
                SubnetId=injector_subnet
            )
        ],
        UserData=create_injector_userdata(
            instance='InjectorInstance',
            aws_access_key_param=aws_access_key_param,
            aws_secret_key_param=aws_secret_key_param,
            kinesis_stream=kinesis_stream,
            bucket=bucket,
            grid_size=grid_size,
            batch_size=batch_size,
            batch_timeout_ms=batch_timeout_ms,
            courier_nr=courier_nr,
            courier_ping_ms=courier_ping_ms,
            courier_step_size=courier_step_size
        ),
        Tags=Tags(
            Name='InjectorInstance'
        )
    )
    resources.append(instance)
    return parameters, resources


@click.group()
@click.pass_context
def main(ctx):
    ctx.obj['client'] = boto3.client('cloudformation')


@main.command()
@click.option('--deploy', 'operation', flag_value='deploy')
@click.option('--undeploy', 'operation', flag_value='undeploy')
@click.option('--status', 'operation', flag_value='status')
@click.option('--dry-run', '-d', 'dryrun', is_flag=True)
@click.option('--home-ip', 'home_ip', help='Trusted IP address')
@click.option('--office-ip', 'office_ip', help='Trusted office IP address')
@click.pass_context
def vpc(ctx, operation, dryrun, home_ip, office_ip):
    if operation == 'deploy':
        if not home_ip:
            raise click.BadParameter('Must specify home IP')
        if not office_ip:
            raise click.BadParameter('Must specify office IP')
        vpc = create_vpc()
        cassandra_subnet = create_cassandra_subnet(vpc)
        service_subnet = create_service_subnet(vpc)
        injector_subnet = create_injector_subnet(vpc)
        load_test_subnet = create_load_test_subnet(vpc)
        internet_gateway = create_internet_gateway()
        gateway_attachment = create_gateway_attachment(vpc, internet_gateway)
        route_table = create_route_table(vpc)
        route = create_route(route_table, internet_gateway)
        route_association = create_subnet_route_table_associations(cassandra_subnet, service_subnet, injector_subnet, load_test_subnet, route_table)
        acl_resources = create_acl(vpc, cassandra_subnet, service_subnet, injector_subnet, load_test_subnet, home_ip, office_ip)
        vpc_output = create_vpc_stack_outputs(vpc, cassandra_subnet, service_subnet, injector_subnet, load_test_subnet)

        t = Template()
        t.add_description("""Courier realtime VPC stack""")
        t.add_resource(vpc)
        t.add_resource(cassandra_subnet)
        t.add_resource(service_subnet)
        t.add_resource(injector_subnet)
        t.add_resource(load_test_subnet)
        t.add_resource(internet_gateway)
        t.add_resource(gateway_attachment)
        t.add_resource(route_table)
        t.add_resource(route)
        t.add_resource(route_association)
        t.add_resource(acl_resources)
        t.add_output(vpc_output)
        if dryrun:
            print(t.to_json())
        else:
            client = ctx.obj['client']
            response = client.create_stack(
                StackName=VPC_STACK,
                TemplateBody=t.to_json()
            )
            print(response)
    elif operation == 'undeploy':
        if dryrun:
            print(f'Will delete stack "{VPC_STACK}"')
        else:
            client = ctx.obj['client']
            client.delete_stack(
                StackName=VPC_STACK
            )
            print(f'Deleting "{VPC_STACK}"')
    elif operation == 'status':
        client = ctx.obj['client']
        response = client.describe_stacks(
            StackName=VPC_STACK
        )
        print(response)
    else:
        raise click.BadParameter('Must specify action')


@main.command()
@click.option('--deploy', 'operation', flag_value='deploy')
@click.option('--undeploy', 'operation', flag_value='undeploy')
@click.option('--status', 'operation', flag_value='status')
@click.option('--number', default=2, help='Number of seed nodes')
@click.option('--instance', default=CASSANDRA_INSTANCE, help='EC2 instance type')
@click.option('--key-pair', 'key_pair', default=KEY_PAIR, help='EC2 SSH key pair')
@click.option('--bucket', default='courier-realtime', help='S3 bucket to download init tar')
@click.option('--aws-access-key', 'aws_access_key', envvar='AWS_ACCESS_KEY_ID', help='Defaults to AWS_ACCESS_KEY_ID')
@click.option('--aws-secret-key', 'aws_secret_key', envvar='AWS_SECRET_ACCESS_KEY', help='Defaults to AWS_SECRET_ACCESS_KEY')
@click.option('--home-ip', 'home_ip', help='Trusted home IP address')
@click.option('--office-ip', 'office_ip', help='Trusted office IP address')
@click.option('--dry-run', '-d', 'dryrun', is_flag=True)
@click.pass_context
def cassandra(ctx, operation, number, instance, key_pair, bucket, aws_access_key, aws_secret_key, home_ip, office_ip, dryrun):
    if operation == 'deploy':
        if not home_ip:
            raise click.BadParameter('Must specify home IP')
        if not office_ip:
            raise click.BadParameter('Must specify office IP')
        vpc = ImportValue(f'{VPC_STACK}-VPC')
        cassandra_subnet = ImportValue(f'{VPC_STACK}-CSD-SUBNET')
        parameters, resources, outputs = create_cassandra_cluster(vpc, number, instance, key_pair, cassandra_subnet, home_ip, office_ip, bucket)

        t = Template()
        t.add_description("""Cassandra cluster stack""")
        t.add_parameter(parameters)
        t.add_resource(resources)
        t.add_output(outputs)

        if dryrun:
            print(t.to_json())
        else:
            client = ctx.obj['client']
            response = client.create_stack(
                StackName=CASSANDRA_STACK,
                TemplateBody=t.to_json(),
                Parameters=[
                    {
                        'ParameterKey': 'AWSAccessKey',
                        'ParameterValue': aws_access_key
                    },
                    {
                        'ParameterKey': 'AWSSecretKey',
                        'ParameterValue': aws_secret_key
                    }
                ]
            )
            print(response)
    elif operation == 'undeploy':
        if dryrun:
            print(f'Will delete stack "{CASSANDRA_STACK}"')
        else:
            client = ctx.obj['client']
            client.delete_stack(
                StackName=CASSANDRA_STACK
            )
            print(f'Deleting "{CASSANDRA_STACK}"')
    elif operation == 'status':
        client = ctx.obj['client']
        response = client.describe_stacks(
            StackName=CASSANDRA_STACK
        )
        print(response)
    else:
        raise click.BadParameter('Must specify action')


@main.command()
@click.option('--deploy', 'operation', flag_value='deploy')
@click.option('--undeploy', 'operation', flag_value='undeploy')
@click.option('--status', 'operation', flag_value='status')
@click.option('--name', default='CourierRealtimeStream', help='Name of stream')
@click.option('--shards', default=2, help='Number of shards')
@click.option('--dry-run', '-d', 'dryrun', is_flag=True)
@click.pass_context
def kinesis(ctx, operation, name, shards, dryrun):
    if operation == 'deploy':
        t = Template()
        kinesis_stream = t.add_resource(
            Stream(
                name,
                ShardCount=shards
            )
        )

        t.add_output(
            Output(
                'KinesisStreamNameOutput',
                Description='Stream name (physical ID)',
                Value=Ref(kinesis_stream),
                Export=Export(
                    Sub('${AWS::StackName}-STREAM-NAME')
                )
            )
        )

        if dryrun:
            print(t.to_json())
        else:
            client = ctx.obj['client']
            response = client.create_stack(
                StackName=KINESIS_STACK,
                TemplateBody=t.to_json()
            )
            print(response)
    elif operation == 'undeploy':
        if dryrun:
            print(f'Will delete stack "{KINESIS_STACK}"')
        else:
            client = ctx.obj['client']
            client.delete_stack(
                StackName=KINESIS_STACK
            )
            print(f'Deleting "{KINESIS_STACK}"')
    elif operation == 'status':
        client = ctx.obj['client']
        response = client.describe_stacks(
            StackName=KINESIS_STACK
        )
        print(response)
    else:
        raise click.BadParameter('Must specify action')


@main.command()
@click.option('--deploy', 'operation', flag_value='deploy')
@click.option('--undeploy', 'operation', flag_value='undeploy')
@click.option('--status', 'operation', flag_value='status')
@click.option('--number', default=2, help='Number of seed instances')
@click.option('--dry-run', '-d', 'dryrun', is_flag=True)
@click.option('--home-ip', 'home_ip', help='Trusted IP address')
@click.option('--office-ip', 'office_ip', help='Trusted office IP address')
@click.option('--instance', default=SERVICE_INSTANCE, help='EC2 instance type')
@click.option('--key-pair', 'key_pair', default=KEY_PAIR, help='EC2 SSH key pair')
@click.option('--aws-access-key', 'aws_access_key', envvar='AWS_ACCESS_KEY_ID', help='Defaults to AWS_ACCESS_KEY_ID')
@click.option('--aws-secret-key', 'aws_secret_key', envvar='AWS_SECRET_ACCESS_KEY', help='Defaults to AWS_SECRET_ACCESS_KEY')
@click.option('--datadog-api-key', 'datadog_api_key', help='Datadog API key')
@click.option('--bucket', default='courier-realtime', help='S3 bucket to download init tar')
@click.option('--grid-size', 'grid_size', default=1000)
@click.option('--shard-nr', 'shard_nr', default=100, help='Number of Akka cluster shards for each shard region')
@click.option('--snapshot-after', 'snapshot_after', default=1000, help='Number of events before Akka persists a snapshot for each persistent actor')
@click.option('--offline-after', 'offline_after', default=300, help='Number of seconds before a courier is marked offline')
@click.option('--ws', is_flag=True, help='Enable active messages over websocket.  Those messages include courier location pings.  This is network intensive, so do not use this in load test')
@click.pass_context
def service(ctx, operation, number, dryrun, home_ip, office_ip, instance, key_pair, aws_access_key, aws_secret_key, datadog_api_key, bucket, grid_size, shard_nr, snapshot_after, offline_after, ws):
    if operation == 'deploy':
        if not home_ip:
            raise click.BadParameter('Must specify home IP')
        if not office_ip:
            raise click.BadParameter('Must specify office IP')
        if not datadog_api_key:
            raise click.BadParameter('Must specify Datadog API key')

        vpc = ImportValue(f'{VPC_STACK}-VPC')
        service_subnet = ImportValue(f'{VPC_STACK}-SRV-SUBNET')
        cassandra_seeds = ImportValue(f'{CASSANDRA_STACK}-CSD-SEEDS')
        kinesis_stream = ImportValue(f'{KINESIS_STACK}-STREAM-NAME')

        parameters, resources = create_service_cluster(
            vpc=vpc,
            seed_nr=number,
            instance_type=instance,
            key_pair=key_pair,
            service_subnet=service_subnet,
            home_ip=home_ip,
            office_ip=office_ip,
            bucket=bucket,
            kinesis_stream=kinesis_stream,
            cassandra_seeds=cassandra_seeds,
            grid_size=grid_size,
            shard_nr=shard_nr,
            snapshot_after=snapshot_after,
            offline_after_ms=offline_after,
            ws=ws
        )

        t = Template()
        t.add_parameter(parameters)
        t.add_resource(resources)

        if dryrun:
            print(t.to_json())
        else:
            client = ctx.obj['client']
            response = client.create_stack(
                StackName=SERVICE_STACK,
                TemplateBody=t.to_json(),
                Parameters=[
                    {
                        'ParameterKey': 'AWSAccessKey',
                        'ParameterValue': aws_access_key
                    },
                    {
                        'ParameterKey': 'AWSSecretKey',
                        'ParameterValue': aws_secret_key
                    },
                    {
                        'ParameterKey': 'DatadogApiKey',
                        'ParameterValue': datadog_api_key
                    }
                ]
            )
            print(response)
    elif operation == 'undeploy':
        if dryrun:
            print(f'Will delete stack "{SERVICE_STACK}"')
        else:
            client = ctx.obj['client']
            client.delete_stack(
                StackName=SERVICE_STACK
            )
            print(f'Deleting "{SERVICE_STACK}"')
    elif operation == 'status':
        client = ctx.obj['client']
        response = client.describe_stacks(
            StackName=SERVICE_STACK
        )
        print(response)
    else:
        raise click.BadParameter('Must specify action')


@main.command()
@click.option('--deploy', 'operation', flag_value='deploy')
@click.option('--undeploy', 'operation', flag_value='undeploy')
@click.option('--status', 'operation', flag_value='status')
@click.option('--dry-run', '-d', 'dryrun', is_flag=True)
@click.option('--home-ip', 'home_ip', help='Trusted IP address')
@click.option('--office-ip', 'office_ip', help='Trusted office IP address')
@click.option('--instance', default=SERVICE_INSTANCE, help='EC2 instance type')
@click.option('--key-pair', 'key_pair', default=KEY_PAIR, help='EC2 SSH key pair')
@click.option('--aws-access-key', 'aws_access_key', envvar='AWS_ACCESS_KEY_ID', help='Defaults to AWS_ACCESS_KEY_ID')
@click.option('--aws-secret-key', 'aws_secret_key', envvar='AWS_SECRET_ACCESS_KEY', help='Defaults to AWS_SECRET_ACCESS_KEY')
@click.option('--bucket', default='courier-realtime', help='S3 bucket to download init tar')
@click.option('--grid-size', 'grid_size', default=1000)
@click.option('--batch-size', 'batch_size', default=1000)
@click.option('--batch-timeout', 'batch_timeout', default=1000, help='The period of sending to Kinesis in milliseconds')
@click.option('--courier-nr', 'courier_nr', default=500, help='Number of couriers')
@click.option('--courier-ping', 'courier_ping', default=60000, help='The period of couriers sending pings in milliseconds')
@click.option('--courier-step', 'courier_step', default=0.8, help='The step size of courier movement in each period.  Courier can move with step size in x direction (left or righ) and step size in y direction (up or down).')
@click.pass_context
def injector(ctx, operation, dryrun, home_ip, office_ip, instance, key_pair, aws_access_key, aws_secret_key, bucket, grid_size, batch_size, batch_timeout, courier_nr, courier_ping, courier_step):
    if operation == 'deploy':
        if not home_ip:
            raise click.BadParameter('Must specify home IP')
        if not office_ip:
            raise click.BadParameter('Must specify office IP')

        vpc = ImportValue(f'{VPC_STACK}-VPC')
        injector_subnet = ImportValue(f'{VPC_STACK}-INJ-SUBNET')
        kinesis_stream = ImportValue(f'{KINESIS_STACK}-STREAM-NAME')

        parameters, resources = create_injector(
            vpc=vpc,
            instance_type=instance,
            key_pair=key_pair,
            injector_subnet=injector_subnet,
            home_ip=home_ip,
            office_ip=office_ip,
            bucket=bucket,
            kinesis_stream=kinesis_stream,
            grid_size=grid_size,
            batch_size=batch_size,
            batch_timeout_ms=batch_timeout,
            courier_nr=courier_nr,
            courier_ping_ms=courier_ping,
            courier_step_size=courier_step
        )

        t = Template()
        t.add_parameter(parameters)
        t.add_resource(resources)

        if dryrun:
            print(t.to_json())
        else:
            client = ctx.obj['client']
            response = client.create_stack(
                StackName=INJECTOR_STACK,
                TemplateBody=t.to_json(),
                Parameters=[
                    {
                        'ParameterKey': 'AWSAccessKey',
                        'ParameterValue': aws_access_key
                    },
                    {
                        'ParameterKey': 'AWSSecretKey',
                        'ParameterValue': aws_secret_key
                    }
                ]
            )
            print(response)
    elif operation == 'undeploy':
        if dryrun:
            print(f'Will delete stack "{INJECTOR_STACK}"')
        else:
            client = ctx.obj['client']
            client.delete_stack(
                StackName=INJECTOR_STACK
            )
            print(f'Deleting "{INJECTOR_STACK}"')
    elif operation == 'status':
        client = ctx.obj['client']
        response = client.describe_stacks(
            StackName=INJECTOR_STACK
        )
        print(response)
    else:
        raise click.BadParameter('Must specify action')


if __name__ == '__main__':
    main(obj={})
