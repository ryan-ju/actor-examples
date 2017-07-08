import React from "react"
import PropTypes from "prop-types"
import {connect} from 'react-redux'
import paper from "paper"
import {Button, Textfield, Grid, Cell, Card} from "react-mdl"

const mapStateToProps = state => {
    return {
        grid: {
            courierLocation: state.get("courierLocation"),
            courierStatus: state.get("courierStatus"),
            gridSize: state.get("gridSize")
        }
    }
}

function drawGrid(gridSize, strokeWidth) {
    const border = new paper.Path.Rectangle(new paper.Point(0, 0), new paper.Size(gridSize, gridSize))
    border.strokeColor = "DimGray"
    border.strokeWidth = strokeWidth * 3
    const gridLineGroup = new paper.Group()
    for (let i = 1; i < gridSize; i++) {
        const path1 = new paper.Path([new paper.Point(i, 0), new paper.Point(i, gridSize)])
        const path2 = new paper.Path([new paper.Point(0, i), new paper.Point(gridSize, i)])
        gridLineGroup.addChildren([path1, path2])
    }
    gridLineGroup.strokeColor = "DimGray"
    gridLineGroup.strokeWidth = strokeWidth
    gridLineGroup.dashArray = [strokeWidth * 1, strokeWidth * 2]
    gridLineGroup.opacity = 0.5
    const origin = new paper.Path.Circle({
        center: [0, 0],
        radius: strokeWidth * 5
    })
    origin.fillColor = "yellow"
}

class GridPanel extends React.Component {
    constructor() {
        super()
    }

    componentDidMount() {
        const gridSize = this.props.grid.gridSize
        const gridCanvas = document.getElementById("gridCanvas")
        paper.setup(gridCanvas)
        const viewHeight = paper.view.size.height
        paper.view.center = [gridSize / 2, gridSize / 2]
        paper.view.zoom = viewHeight / gridSize
        const gridLayer = new paper.Layer()
        const courierLayer = new paper.Layer()
        const popupLayer = new paper.Layer()
        const strokeWidth = viewHeight / 10000
        this.setState({
            gridLayer,
            courierLayer,
            popupLayer,
            strokeWidth,
            originalHeight: viewHeight
        })
        gridLayer.activate()
        drawGrid(this.props.grid.gridSize, strokeWidth)
        paper.view.draw()
    }

    componentWillReceiveProps(nextProps) {
        if (this.props.grid.gridSize != nextProps.grid.gridSize) {
            const gridSize = nextProps.grid.gridSize
            const viewHeight = this.state.originalHeight
            paper.view.center = [gridSize / 2, gridSize / 2]
            paper.view.zoom = viewHeight / gridSize

            this.state.gridLayer.activate()
            paper.project.activeLayer.removeChildren()
            const strokeWidth = viewHeight / 10000
            drawGrid(nextProps.grid.gridSize, strokeWidth)

            this.setState({strokeWidth: strokeWidth})
        }
    }

    componentWillUpdate(nextProps, nextState) {
        const strokeWidth = nextState.strokeWidth
        const courierRadius = strokeWidth * 3
        const gridSize = nextProps.grid.gridSize

        nextState.popupLayer.activate()
        paper.project.activeLayer.removeChildren()

        nextState.courierLayer.activate()
        const courierLayer = paper.project.activeLayer
        courierLayer.removeChildren()

        nextProps.grid.courierLocation.forEach((location, courierId) => {
            const status = nextProps.grid.courierStatus.get(courierId)
            const circle = new paper.Path.Circle({
                center: [location.x, location.y],
                radius: courierRadius,
                fillColor: status == "ONLINE" ? "lightgreen" : "lightcoral"
            })
            circle.onClick = function (event) {

            }
            circle.onMouseEnter = function (event) {
                nextState.popupLayer.activate()
                const popupLayer = paper.project.activeLayer
                popupLayer.removeChildren()

                const text = new paper.PointText({
                    point: [location.x + 2 * strokeWidth, location.y - 10 * strokeWidth],
                    content: courierId,
                    fillColor: "DimGray",
                    fontSize: 8 * strokeWidth
                })

                const popup = new paper.Path()
                popup.moveTo([location.x, location.y - courierRadius])
                popup.lineBy([0, -15 * strokeWidth])
                popup.lineBy([text.bounds.width + 5 * strokeWidth, 0])
                popup.lineBy([0, 10 * strokeWidth])
                popup.lineBy([-text.bounds.width, 0])
                popup.closePath()
                popup.strokeWidth = strokeWidth
                popup.strokeColor = "DimGray"
                popup.fillColor = "white"
                if (popup.bounds.right > paper.view.bounds.right) {
                    popup.scale(-1, 1, popup.bounds.topLeft)
                    text.translate([-popup.bounds.width, 0])
                }
                if (popup.bounds.top < paper.view.bounds.top) {
                    popup.scale(1, -1, popup.bounds.bottomLeft)
                    popup.translate([0, 2 * courierRadius])
                    text.translate([0, popup.bounds.height + 2 * courierRadius + 5 * strokeWidth])
                }
                popup.insertBelow(text)

                paper.view.draw()
            }
            circle.onMouseLeave = function (event) {
                nextState.popupLayer.activate()
                const popupLayer = paper.project.activeLayer
                popupLayer.removeChildren()
                paper.view.draw()
            }
        })
        paper.view.draw()
    }

    render() {
        return (
            <Card shadow={1} style={{height: "100%", width: "auto"}}>
                <canvas id="gridCanvas" data-paper-resize="true"
                        style={{width: "400px", height: "400px", margin: "0 auto"}}/>
            </Card>
        )
    }
}

GridPanel.PropTypes = {
    grid: PropTypes.shape({
        courierLocation: PropTypes.object.isRequire,
        courierStatus: PropTypes.object.isRequire,
        gridSize: PropTypes.number.isRequire
    })
}

export default connect(mapStateToProps, null)(GridPanel)