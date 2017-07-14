import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import {Button, Textfield, Grid, Cell, Card, DataTable, TableHeader} from "react-mdl"
import {SelectField, Option} from "react-mdl-extra"
import PubSub from "pubsub-js"
import {
    startCourierEvt,
    stopCourierEvt,
    courierUrlChangedEvt,
    injectorUrlChangedEvt,
    connectCourierEvt,
    connectInjectorEvt,
    getCourierRecommendationEvt,
    refreshEvt
} from "./Action"

const mapStateToProps = state => {
    return {
        places: state.get("places"),
        courierRecommendation: state.get("courierRecommendation")
    }
}

const mapDispatchToProps = dispatch => {
    return {
        getRecommendation: (longitude, latitude, radius, limit) => {
            dispatch(getCourierRecommendationEvt({longitude, latitude, radius, limit}))
        }
    }
}

class CourierRecommendationPanel extends React.Component {
    constructor(props) {
        super(props)
        const map = new Map()
        this.props.places.forEach(place => {
            map.set(place.placeId, place)
        })
        this.state = {
            placeMap: map,
            radius: 1,
            limit: 5
        }
        this.selectPlace = this.selectPlace.bind(this)
        this.setRadius = this.setRadius.bind(this)
        this.setLimit = this.setLimit.bind(this)
        this.getRecommendation = this.getRecommendation.bind(this)
    }

    selectPlace(placeId) {
        this.setState({placeId: placeId})
    }

    setRadius(radius) {
        this.setState({radius: radius})
    }

    setLimit(limit) {
        this.setState({limit: limit})
    }

    getRecommendation() {
        const placeId = this.state.placeId
        const place = this.state.placeMap.get(placeId)
        if (place == null) {
            PubSub.publish("events", `Place ${placeId} has no coordinates`)
            return
        }
        const coordinates = place.coordinates
        const radius = this.state.radius
        const limit = this.state.limit
        this.props.getRecommendation(coordinates.longitude, coordinates.latitude, radius, limit)
    }

    componentWillReceiveProps(nextProps) {
        if (this.props.places != nextProps.places) {
            const map = new Map()
            nextProps.places.forEach(place => {
                map.set(place.placeId, place)
            })
            this.setState({placeMap: map})
        }
    }

    render() {
        return (
            <Card id="courier-control-panel" shadow={1} style={{height: "100%", width: "auto"}}>
                <Grid>
                    <Cell col={3}>
                        <SelectField label="Place ID" value={this.state.placeId} onChange={this.selectPlace}>
                            {
                                Array.from(this.state.placeMap.keys()).sort().map(placeId => {
                                        return <Option value={placeId}>{placeId}</Option>
                                    }
                                )
                            }
                        </SelectField>
                    </Cell>
                    <Cell col={2}>
                        <Textfield label="Radius..." floatingLabel pattern="[1-9][0-9]*"
                                   error="Input must be a positive number" value={this.state.radius.toString()}
                                   onChange={t => this.setRadius(t.target.value)}/>
                    </Cell>
                    <Cell col={2}>
                        <Textfield label="Limit..." floatingLabel pattern="[1-9][0-9]*"
                                   error="Input must be a positive number" value={this.state.limit.toString()}
                                   onChange={t => this.setLimit(t.target.value)}/>
                    </Cell>
                    <Cell col={5}>
                        <Button raised colored ripple onClick={this.getRecommendation}>Recommendations</Button>
                    </Cell>
                </Grid>
                <DataTable shadow={0} rows={this.props.courierRecommendation}
                           style={{"margin-left": "auto", "margin-right": "auto"}}>
                    <TableHeader name="courierId">Courier ID</TableHeader>
                    <TableHeader name="distance">Distance</TableHeader>
                </DataTable>
            </Card>
        )
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(CourierRecommendationPanel)