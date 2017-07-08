import React from "react"
import PropTypes from "prop-types"
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"
import {connect} from 'react-redux'

const mapStateToProps = (state) => {
    return {
        courierStatus: state.get("courierStatus")
    }
};

const CourierStatusPanel = ({courierStatus}) => {
    const sorted = courierStatus.mapKeys((k) => parseInt(k.split("-")[1], 10)).sortBy((v, k) => k)
    return (
        <Card id="courier-wrap" shadow={1} style={{height: "100%", width: "auto"}}>
            {
                sorted.map((status, id) =>
                    <div key={id}
                         className={status == "ONLINE" ? "mdl-shadow--3dp courier-status-online" : "mdl-shadow--3dp courier-status-offline"}>{id}</div>
                ).toList()
            }
        </Card>
    )
}

CourierStatusPanel.PropTypes = {
    courierStatus: PropTypes.object.isRequired
}

export default connect(mapStateToProps, null)(CourierStatusPanel)