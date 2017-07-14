import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"
import {SelectField, Option} from "react-mdl-extra"
import PubSub from "pubsub-js"
import {startCourierEvt, stopCourierEvt, courierUrlChangedEvt, injectorUrlChangedEvt, connectCourierEvt, connectInjectorEvt, refreshEvt} from "./Action"

const mapStateToProps = state => {
    return {
        courierNr: state.get("courierNr")
    }
}

const mapDispatchToProps = dispatch => {
    return {
        startCourier: (courierId) => () => dispatch(startCourierEvt(courierId)),
        stopCourier: (courierId) => () => dispatch(stopCourierEvt(courierId))
    }
}

class CourierControllerPanel extends React.Component {
    constructor(props) {
        super(props)
        this.state = {
            data: {
                courierId: ""
            }
        }
        this.selectCourier = this.selectCourier.bind(this)
    }

    selectCourier(courierId) {
        this.setState({data: {courierId: courierId}})
    }

    render() {
        return (
            <Card id="courier-control-panel" shadow={1} style={{height: "100%", width: "auto", padding: 10}}>
                <div style={{display: "flex", flexDirection: "row", "alignContent": "flex-start"}}>
                    <SelectField label="Courier ID" value={this.state.data.courierId} onChange={this.selectCourier}>
                        {
                            [...Array(this.props.courierNr).keys()].map(id => {
                                    const courierId = "courier-" + id
                                    return <Option value={courierId}>{courierId}</Option>
                                }
                            )
                        }
                    </SelectField>
                    <Button raised colored ripple onClick={this.props.startCourier(this.state.data.courierId)}>Start</Button>
                    <Button raised accent ripple onClick={this.props.stopCourier(this.state.data.courierId)}>Stop</Button>
                </div>
            </Card>
        )
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(CourierControllerPanel)