import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import {Button, Textfield, Grid, Cell, Card, DataTable, TableHeader} from "react-mdl"
import PubSub from "pubsub-js"

const mapStateToProps = state => {
    return {
        kinesisClusterStats: state.get("kinesisClusterStats"),
        courierClusterStats: state.get("courierClusterStats")
    }
}

const ClusterStatusPanel = ({kinesisClusterStats, courierClusterStats}) => {
    const map = new Map()
    // record of format {host: "", kinesisClusterName: entityNr, courierClusterName: entityNr}
    kinesisClusterStats.forEach(record => {
        map.set(record.host, Object.assign({}, record))
    })

    courierClusterStats.forEach(record => {
        const rec = map.get(record.host)
        if (rec == null) {
            map.set(record.host, Object.assign({}, record))
        } else {
            map.set(record.host, Object.assign(rec, record))
        }
    })

    const headers = new Set()

    Array.from(map.values()).forEach(record => {
        Object.getOwnPropertyNames(record).forEach(propName => {
            if (propName != "host") {
                headers.add(propName)
            }
        })
    })

    return (
        <Card shadow={1} style={{height: "100%", width: "auto", padding: 10}}>
            <p>Cluster stats (number of actors per cluster type per host)</p>
            <DataTable
                shadow={0}
                rows={Array.from(map.values())}
                style={{"margin-left": "auto", "margin-right": "auto"}}
            >
                <TableHeader name="host">host</TableHeader>
                {
                    Array.from(headers).map(header =>
                        <TableHeader name={header}>{header}</TableHeader>
                    )
                }
            </DataTable>
        </Card>
    )
}

export default connect(mapStateToProps, null)(ClusterStatusPanel)