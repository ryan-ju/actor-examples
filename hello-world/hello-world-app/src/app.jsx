import React from 'react';
import ReactDOM from 'react-dom';
import { withStyles, createStyleSheet } from 'material-ui/styles';
import Paper from 'material-ui/Paper';
import { MuiThemeProvider } from 'material-ui/styles';
import HellosComponent from "./js/hello_view";

const style = {
    height: "44%",
    width: "44%",
    padding: "3%",
    float: "left"
};

const Quadrants = () => (
    <div>
        <Paper style={style} zDepth={1}>
            <HellosComponent/>
        </Paper>
        <Paper style={style} zDepth={1} />
        <Paper style={style} zDepth={1} />
        <Paper style={style} zDepth={1} />
    </div>
);

function App() {
    return (
        <MuiThemeProvider>
            <Quadrants />
        </MuiThemeProvider>
    );
}

ReactDOM.render(<App />, document.querySelector('#app'));