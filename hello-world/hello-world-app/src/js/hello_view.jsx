import {run} from '@cycle/run';
import {WSDriver} from "./websocket_driver";
import {SET_COUNT} from "./action";
import {createCycleMiddleware} from 'redux-cycles';
import {connect, Provider} from 'react-redux'
import {createStore, applyMiddleware} from 'redux'
import React from "react"

const mapStateToProps = (state) => {
    return {
        hellos: state.hellos
    }
};

const Hellos = connect(
    mapStateToProps,
    null
)(({hellos}) => {
    return (
        <ul>
            {hellos.map(hello =>
                <li key={hello.name}>{hello.name} {hello.count}</li>
            )}
        </ul>
    )
});

function main(sources) {
    const hello$ = sources.WEBSOCKET
        .map(message => {
            return {
                type: SET_COUNT,
                payload: {
                    name: message.name,
                    count: message.count
                }
            }
        });
    return {
        ACTION: hello$
    }
}

const cycleMiddleware = createCycleMiddleware();
const {makeActionDriver} = cycleMiddleware;

const reducer = (state = {hellos: []}, action) => {
    switch (action.type) {
        case SET_COUNT:
            const {name, count} = action.payload;
            const newHellos = [];
            let pushed = false;
            for (const hello of state.hellos) {
                if (hello.name == name) {
                    newHellos.push({name: name, count: count});
                    pushed = true;
                } else {
                    newHellos.push(hello);
                }
            }
            if (!pushed) {
                newHellos.push({name: name, count: count});
            }
            return Object.assign({}, state, {hellos: newHellos});
        default:
            return state;
    }
};

const store = createStore(
    reducer,
    applyMiddleware(cycleMiddleware)
);

run(main, {
    WEBSOCKET: WSDriver("ws://localhost:3000/hello"),
    ACTION: makeActionDriver()
});

const HellosComponent = () => (
    <Provider store={store}>
        <Hellos/>
    </Provider>
);

export default HellosComponent;