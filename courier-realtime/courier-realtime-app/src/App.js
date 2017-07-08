import React, {Component} from 'react';
import logo from './logo.svg';
import './App.css';
import 'react-mdl/extra/material.css';
import 'react-mdl/extra/material.js';
import ConsolePanel from "./ConsolePanel"
import CourierStatusPanel from "./CourierStatusPanel"
import GridPanel from "./GridPanel"
import PubSub from "pubsub-js"
import {connect, Provider} from 'react-redux'
import {createStore, applyMiddleware} from 'redux'
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"
import xs from "xstream";
import {run} from '@cycle/run'
import {makeHTTPDriver} from "@cycle/http"
import {createCycleMiddleware} from 'redux-cycles'
import {reducer} from "./Action"
import WSDriver from "./WebsocketDriver"
import HTTPCycle from "./HTTPCycle"

const cycleMiddleware = createCycleMiddleware()
const {makeActionDriver, makeStateDriver} = cycleMiddleware;

function main(sources) {
    const httpCycle = HTTPCycle({STATE: sources.STATE, ACTION: sources.ACTION, HTTP: sources.HTTP})
    const httpAction$ = httpCycle.ACTION
    const httpHttp$ = httpCycle.HTTP

    return {
        ACTION: xs.merge(httpAction$, sources.COURIER_WEBSOCKET),
        COURIER_WEBSOCKET: sources.ACTION,
        // INJECTOR_WEBSOCKET: sources.ACTION,
        HTTP: httpHttp$
    }
}

const store = createStore(
    reducer,
    applyMiddleware(cycleMiddleware)
);

run(main, {
    COURIER_WEBSOCKET: WSDriver("connect_to_courier_cluster"),
    // INJECTOR_WEBSOCKET: WSDriver("connect_to_injector_cluster"),
    HTTP: makeHTTPDriver(),
    ACTION: makeActionDriver(),
    STATE: makeStateDriver()
});

class App extends Component {
    render() {
        return (
            <Provider store={store}>
                <div className="App">
                    <div className="App-header">
                        <img src={logo} className="App-logo" alt="logo"/>
                        <h2>Welcome to React</h2>
                    </div>
                    <p className="App-intro">
                        To get started, edit <code>src/App.js</code> and save to reload.
                    </p>
                    <Grid>
                        <Cell col={6}><GridPanel/></Cell>
                        <Cell col={6}><CourierStatusPanel/></Cell>
                    </Grid>
                    <div>
                        <ConsolePanel/>
                    </div>
                </div>
            </Provider>
        );
    }
}

export default App;
