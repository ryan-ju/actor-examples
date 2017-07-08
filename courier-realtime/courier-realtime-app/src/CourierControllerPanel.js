import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"
import PubSub from "pubsub-js"
import {courierUrlChangedEvt, injectorUrlChangedEvt, connectCourierEvt, connectInjectorEvt, refreshEvt} from "./Action"


const CourierControllerPanel = ({courierNr}) => {
    return (
        <Card shadow={1} style={{height: "100%", width: "auto"}}>

        </Card>
    )
}