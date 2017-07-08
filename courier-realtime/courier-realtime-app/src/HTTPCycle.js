import xs from "xstream"
import sampleCombine from "xstream/extra/sampleCombine"
import PubSub from "pubsub-js"
import {gridSizeEvt, courierNrEvt} from "./Action"
import {adapt} from '@cycle/run/lib/adapt'

function HTTPCycle(sources) {
    const state$ = sources.STATE
    const action$ = sources.ACTION
    const http$ = sources.HTTP

    const request$ = action$.compose(sampleCombine(state$)).map(([message, state]) => {
        if ("get_grid_size" == message.type) {
            PubSub.publish("events", "Getting grid size ...")
            return {
                url: `http${state.get("injectorUrl").slice(2)}/gridSize`,
                category: message.type
            }
        } else if ("get_courier_nr" == message.type) {
            PubSub.publish("events", "Getting #courier ...")
            return {
                url: `http${state.get("injectorUrl").slice(2)}/courierNr`,
                category: message.type
            }
        }
    })

    const gridSize$ = http$
        .select("get_grid_size")
        .flatten()
        .map(res => {
            PubSub.publish("events", `Received grid size ${res.text}`)
            return gridSizeEvt(parseInt(res.text))
        })

    const courierNr$ = http$
        .select("get_courier_nr")
        .flatten()
        .map(res => {
            PubSub.publish("events", `Received #courier ${res.text}`)
            return courierNrEvt(parseInt(res.text))
        })

    return {
        HTTP: request$,
        ACTION: xs.merge(gridSize$, courierNr$)
    }
}

export default HTTPCycle