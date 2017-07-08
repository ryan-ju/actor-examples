import {createAction, handleActions} from "redux-actions"
import {Map} from "immutable"

export const getGridSizeEvt = createAction("get_grid_size")
export const gridSizeEvt = createAction("grid_size")
export const getCourierNrEvt = createAction("get_courier_nr")
export const courierNrEvt = createAction("courier_nr")
export const courierStatusEvt = createAction("courier_status")
export const courierLocationEvt = createAction("courier_location")
export const courierUrlChangedEvt = createAction("courier_url_changed")
export const injectorUrlChangedEvt = createAction("injector_url_changed")
export const connectCourierEvt = createAction("connect_to_courier_cluster")
export const connectInjectorEvt = createAction("connect_to_injector_cluster")
export const refreshEvt = createAction("refresh")
export const noEvt = createAction("no_event") // Placeholder event

export const reducer = handleActions({
        [gridSizeEvt](state, {payload: size}) {
            return state.updateIn(["gridSize"], () => size)
        },
        [courierUrlChangedEvt](state, {payload: url}) {
            return state.updateIn(["courierUrl"], () => url)
        },
        [injectorUrlChangedEvt](state, {payload: url}) {
            return state.updateIn(["injectorUrl"], () => url)
        },
        [courierStatusEvt](state, {payload: {courierId, status}}) {
            return state.updateIn(["courierStatus", courierId], () => status)
        },
        [courierLocationEvt](state, {payload: {courierId, x, y}}) {
            return state.updateIn(["courierLocation", courierId], () => {
                return {x, y}
            })
        }
    },
    Map(
        {
            courierLocation: Map(),
            courierStatus: Map(),
            courierUrl: "ws://localhost:30001/events",
            injectorUrl: "ws://localhost:3001/injector",
            gridSize: 5
        }
    )
)