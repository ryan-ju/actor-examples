import xs from "xstream"
import sampleCombine from "xstream/extra/sampleCombine"
import PubSub from "pubsub-js"
import {gridSizeEvt, courierNrEvt, noEvt} from "./Action"

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
        } else if ("start_courier" == message.type) {
            const courierId = message.payload
            PubSub.publish("events", `Starting courier ${courierId} ...`)
            return {
                method: "POST",
                url: `http${state.get("injectorUrl").slice(2)}/startCourier`,
                query: {courierId: courierId},
                category: "confirmation"
            }
        } else if ("stop_courier" == message.type) {
            const courierId = message.payload
            PubSub.publish("events", `Stopping courier ${courierId} ...`)
            return {
                method: "POST",
                url: `http${state.get("injectorUrl").slice(2)}/stopCourier`,
                query: {courierId: courierId},
                category: "confirmation"
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

    const confirmation$ = http$
        .select("confirmation")
        .flatten()
        .map(res => {
            PubSub.publish("events", res.text)
            return noEvt()
        })

    return {
        HTTP: request$,
        ACTION: xs.merge(gridSize$, courierNr$, confirmation$)
    }
}

export default HTTPCycle