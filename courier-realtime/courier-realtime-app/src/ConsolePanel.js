import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"
import PubSub from "pubsub-js"
import {
    getGridSizeEvt,
    getCourierNrEvt,
    courierUrlChangedEvt,
    injectorUrlChangedEvt,
    connectCourierEvt,
    connectInjectorEvt,
    refreshEvt
} from "./Action"

const mapStateToProps = state => {
    return {
        courierUrl: state.get("courierUrl"),
        injectorUrl: state.get("injectorUrl")
    }
}

const mapDispatcherToProps = dispatch => {
    return {
        dispatch: dispatch,
        onCourierUrlChanged: url => {
            dispatch(courierUrlChangedEvt(url))
        },
        onInjectorUrlChanged: url => {
            dispatch(injectorUrlChangedEvt(url))
        },
        onRefresh: () => {
            dispatch(refreshEvt())
        }
    }
}

const merge = (stateProps, dispatchProps, ownProps) => {
    const {courierUrl, injectorUrl} = stateProps
    const {dispatch} = dispatchProps

    return {
        ...ownProps,
        ...stateProps,
        ...dispatchProps,
        onCourierConnectClick: () => dispatch(connectCourierEvt(courierUrl)),
        onInjectorConnectClick: () => {
            dispatch(getGridSizeEvt())
            dispatch(getCourierNrEvt())
            dispatch(connectInjectorEvt(injectorUrl))
        }
    }
}

const ConnectCourierPanel = ({onCourierConnectClick, onCourierUrlChanged}) => {
    return (
        <div className="mui-panel">
            <Textfield label="host:port..." floatingLabel onChange={({target}) => onCourierUrlChanged(target.value)}
                       value="ws://localhost:30001/events"/>
            <Button raised colored ripple onClick={onCourierConnectClick}>Connect</Button>
        </div>
    )
}

const ConnectInjectorPanel = ({onInjectorConnectClick, onInjectorUrlChanged}) => {
    return (
        <div className="mui-panel">
            <Textfield label="host:port..." floatingLabel onChange={({target}) => onInjectorUrlChanged(target.value)}
                       value="ws://localhost:3001/injector"/>
            <Button raised colored ripple onClick={onInjectorConnectClick}>Connect</Button>
        </div>
    )
}

class EventOutputPanel extends React.Component {
    constructor(props) {
        super(props)
        this.state = {data: []}
        // this.setState({data: []})
    }

    componentDidMount() {
        this.token = PubSub.subscribe("events", (msg, d) => {
            if (this.state.data.length >= 10) {
                this.setState({data: [...this.state.data.slice(1), d]})
            } else {
                this.setState({data: [...this.state.data, d]})
            }
        })
    }

    componentWillUnmount() {
        PubSub.unsubscribe(this.token)
    }

    render() {
        return (
            <div style={{textAlign: "left"}}>
                {this.state.data.map(d =>
                    <div><code>{d}</code><br/></div>
                )}
            </div>
        )
    }
}

const ConsolePanel = ({courierUrl, onInjectorConnectClick, onInjectorUrlChanged, onCourierConnectClick, onCourierUrlChanged, onRefresh}) => {
    return (
        <div>
            <Grid style={{height: '10%'}}>
                <Cell col={6}>
                    <Card shadow={1} style={{width: 'auto', padding: 10}}>
                        <ConnectCourierPanel onCourierConnectClick={onCourierConnectClick}
                                             onCourierUrlChanged={onCourierUrlChanged}/>
                        <br/>
                        <ConnectInjectorPanel onInjectorConnectClick={onInjectorConnectClick}
                                              onInjectorUrlChanged={onInjectorUrlChanged}/>
                        <br/>
                        <div className="mui-panel">
                            <Button raised accent ripple onClick={onRefresh}>Refresh</Button>
                        </div>
                    </Card>
                </Cell>
                <Cell col={6}>
                    <Card shadow={1} style={{width: 'auto'}}>
                        <EventOutputPanel/>
                    </Card>
                </Cell>
            </Grid>
        </div>
    )
}

ConsolePanel.propTypes = {
    courierUrl: PropTypes.string.isRequired,
    onCourierUrlChanged: PropTypes.func.isRequired,
    onCourierConnectClick: PropTypes.func.isRequired
}

export default connect(mapStateToProps, mapDispatcherToProps, merge)(ConsolePanel)