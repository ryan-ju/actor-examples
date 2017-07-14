import xs from "xstream"
import sampleCombine from "xstream/extra/sampleCombine"
import PubSub from "pubsub-js"
import {gridSizeEvt, courierNrEvt, placeEvt, courierRecommendationEvt, noEvt} from "./Action"

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
        } else if ("get_place" == message.type) {
            PubSub.publish("events", "Getting places ...")
            return {
                url: `http${state.get("courierUrl").slice(2)}/places`,
                category: "get_place"
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
        } else if ("get_courier_recommendation" == message.type) {
            PubSub.publish("events", "Getting courier recommendations ...")
            const {longitude, latitude, radius, limit} = message.payload
            return {
                url: `http${state.get("courierUrl").slice(2)}/recommendations`,
                query: {longitude, latitude, radius, limit},
                category: "courier_recommendation"
            }
        } else if ("refresh" == message.type) {
            PubSub.publish("events", "Getting places ...")
            return {
                url: `http${state.get("courierUrl").slice(2)}/places`,
                category: "get_place"
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

    const place$ = http$
        .select("get_place")
        .flatten()
        .map(res => {
            PubSub.publish("events", `Received places`)
            // Response of format
            // {
            //     "places": [
            //     {
            //         "placeId": "place-7",
            //         "coordinates": {
            //             "longitude": 9.45245953473,
            //             "latitude": 1.47742691815
            //         }
            //     },
            //     ...
            //     ]
            // }
            const places = JSON.parse(res.text)["places"]
            return placeEvt(places)
        })

    const courierRecommendation$ = http$
        .select("courier_recommendation")
        .flatten()
        .map(res => {
            PubSub.publish("events", "Received courier recommendations")
            return courierRecommendationEvt(JSON.parse(res.text))
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
        ACTION: xs.merge(gridSize$, courierNr$, place$, courierRecommendation$, confirmation$)
    }
}

export default HTTPCycle