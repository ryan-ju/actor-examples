import {createAction, handleActions} from "redux-actions"
import {Map} from "immutable"

export const getGridSizeEvt = createAction("get_grid_size")
export const gridSizeEvt = createAction("grid_size")
export const getCourierNrEvt = createAction("get_courier_nr")
export const courierNrEvt = createAction("courier_nr")
export const getPlaceEvt = createAction("get_place")
export const placeEvt = createAction("place")
export const startCourierEvt = createAction("start_courier")
export const stopCourierEvt = createAction("stop_courier")
export const courierStatusEvt = createAction("courier_status")
export const courierLocationEvt = createAction("courier_location")
export const courierUrlChangedEvt = createAction("courier_url_changed")
export const injectorUrlChangedEvt = createAction("injector_url_changed")
export const connectCourierEvt = createAction("connect_to_courier_cluster")
export const connectInjectorEvt = createAction("connect_to_injector_cluster")
export const refreshEvt = createAction("refresh")
export const refreshClusterStatsEvt = createAction("refresh_cluster_stats")
export const kinesisClusterStatsEvt = createAction("kinesis_cluster_stats")
export const courierClusterStatsEvt = createAction("courier_cluster_stats")
export const gridClusterStatsEvt = createAction("grid_cluster_stats")
export const getCourierRecommendationEvt = createAction("get_courier_recommendation")
export const courierRecommendationEvt = createAction("courier_recommendation")
export const noEvt = createAction("no_event") // Placeholder event

export const reducer = handleActions({
        [gridSizeEvt](state, {payload: size}) {
            return state.updateIn(["gridSize"], () => size)
        },
        [courierNrEvt](state, {payload: courierNr}) {
            return state.updateIn(["courierNr"], () => courierNr)
        },
        [placeEvt](state, {payload: places}) {
            return state.updateIn(["places"], () => places)
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
        },
        [kinesisClusterStatsEvt](state, {payload: table}) {
            return state.updateIn(["kinesisClusterStats"], () => table)
        },
        [courierClusterStatsEvt](state, {payload: table}) {
            return state.updateIn(["courierClusterStats"], () => table)
        },
        [gridClusterStatsEvt](state, {payload: table}) {
            return state.updateIn(["gridClusterStats"], () => table)
        },
        [courierRecommendationEvt](state, {payload}) {
            const courierRecommendations = payload.couriers.map(courier => {
                const distance = Math.sqrt(courier.distance)
                return {courierId: courier.courierId, distance: distance}
            })
            return state
                .updateIn(["courierRecommendation"], () => courierRecommendations)
                .updateIn(["recommendationArea"], () => payload.grids)
        }
    },
    Map(
        {
            courierLocation: Map(),
            courierStatus: Map(),
            places: [],
            courierUrl: "ws://localhost:3001/service",
            injectorUrl: "ws://localhost:30001/injector",
            gridSize: 5,
            courierNr: 0,
            kinesisClusterStats: [],
            courierClusterStats: [],
            gridClusterStats: [],
            courierRecommendation: [],
            recommendationArea: []
        }
    )
)