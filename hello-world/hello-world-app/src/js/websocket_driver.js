import {adapt} from '@cycle/run/lib/adapt';
import xs from "xstream";

class HelloWorldMessage {
    constructor(input) {
        const arr = input.split("|");
        this.name = arr[0];
        this.count = arr[1];
    }
}

function WSDriver(url) {
    const websocket = new WebSocket(url);

    function wsDriver() {
        const source = xs.create({
            start: listener => {
                websocket.onerror = (err) => {
                    listener.error(err)
                };
                websocket.onmessage = (msg) => {
                    listener.next(new HelloWorldMessage(msg.data))
                };
            },
            stop: () => {
                websocket.close();
            },
        });

        return adapt(source);
    }

    return wsDriver;
}

export {WSDriver, HelloWorldMessage};