import {adapt} from '@cycle/run/lib/adapt';
import xs from "xstream";
import PubSub from "pubsub-js"
import {
    courierStatusEvt,
    courierLocationEvt,
    kinesisClusterStatsEvt,
    courierClusterStatsEvt,
    refreshClusterStatsEvt,
    noEvt
} from "./Action"

// data of format "host1->(shard1:7,shard2:8)|host2->(shard1:8,shard2:7)"
function createClusterStatsEvt(prefix, data) {
    const hosts = data.split("|")
    const table = hosts.map(hostStats => {
        let [host, shardStats] = hostStats.split("->")
        const result = {host: host}
        let count = 0
        // Trim ( and )
        shardStats = shardStats.replace(/^\(|\)$/g, "")
        shardStats.split(",").forEach(stats => {
            const [ignored, entities] = stats.split(":")
            count += parseInt(entities)
        })
        result[prefix] = count
        return result
    })

    switch (prefix) {
        case "kinesis_cluster_stats":
            return kinesisClusterStatsEvt(table)
        case "courier_cluster_stats":
            return courierClusterStatsEvt(table)
        default:
            return noEvt()
    }
}

function createEvt(input) {
    if (!input.includes(":")) {
        PubSub.publish("events", input)
        return noEvt()
    }

    const split = input.split(":")
    const prefix = split[0]
    let cmd = null
    let courierId = null

    switch (prefix) {
        case "courier_status":
            cmd = split[1].split("|")
            courierId = cmd[0]
            const status = cmd[1]
            return courierStatusEvt({courierId, status})
        case "courier_location":
            cmd = split[1].split("|")
            courierId = cmd[0]
            const x = parseFloat(cmd[1])
            const y = parseFloat(cmd[2])
            return courierLocationEvt({courierId, x, y})
        case "kinesis_cluster_stats":
        case "courier_cluster_stats":
            const i = input.indexOf(":")
            const data = input.slice(i + 1)
            return createClusterStatsEvt(prefix, data)
        default:
            return noEvt()
    }
}

function WSDriver(typeOfConnection) {
    let websocket = null
    // let listener = null
    let listener = null

    function wsDriver(sink$) {
        const refreshClusterStats$ = xs.periodic(1000).mapTo(refreshClusterStatsEvt())

        xs.merge(sink$, refreshClusterStats$).addListener({
            next: message => {
                if (typeOfConnection == message.type) {
                    PubSub.publish("events", `${typeOfConnection} connecting ...`)
                    if (websocket != null) {
                        websocket.close()
                    }
                    try {
                        websocket = new WebSocket(message.payload)
                        websocket.onerror = (err) => {
                            listener.error(err)
                        }
                        websocket.onmessage = (msg) => {
                            listener.next(createEvt(msg.data))
                        }
                    } catch (error) {
                        PubSub.publish("events", error.message)
                    }
                } else if ("refresh" == message.type) {
                    if (websocket == null) {
                        PubSub.publish("events", "Cannot refresh when not connected")
                    } else {
                        websocket.send("report_courier")
                        websocket.send("report_host")
                        websocket.send("report_kinesis_cluster")
                        websocket.send("report_courier_cluster")
                    }
                } else if ("refresh_cluster_stats" == message.type && websocket != null) {
                    websocket.send("report_kinesis_cluster")
                    websocket.send("report_courier_cluster")
                }
            },
            error: (err) => {
                console.log(`Error from WSDriver.  typeOfConnection=${typeOfConnection}, err=${JSON.stringify(err)}`)
            },
            complete: () => {
                console.log(`WSDriver completed.  typeOfConnection=${typeOfConnection}`)
            }
        })

        const source$ = xs.create({
            start: l => {
                listener = l
            },
            stop: () => {
                if (websocket != null) {
                    websocket.close();
                }
            },
        });

        return adapt(source$)
    }

    return wsDriver
}

export default WSDriver