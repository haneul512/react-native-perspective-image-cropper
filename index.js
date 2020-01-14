import React, { Component } from 'react';
import {
    NativeModules,
    PanResponder,
    Dimensions,
    Image,
    View,
    Animated,
} from 'react-native';
import Svg, { Polygon } from 'react-native-svg';

const AnimatedPolygon = Animated.createAnimatedComponent(Polygon);

class CustomCrop extends Component {
    constructor(props) {
        super(props);
        this.state = {
            height: 0,
            width: Dimensions.get('window').width - 60,
            imageX: 0,
            imageY: 0,
            image: props.initialImage,
            moving: false,
        };

        this.state = {
            ...this.state,
            topLeft: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.topLeft,
                        true,
                    )
                    : { x: 100, y: 100 },
            ),
            topRight: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.topRight,
                        true,
                    )
                    : { x: Dimensions.get('window').width - 100, y: 100 },
            ),
            bottomLeft: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.bottomLeft,
                        true,
                    )
                    : { x: 100, y: this.state.height - 100 },
            ),
            bottomRight: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.bottomRight,
                        true,
                    )
                    : {
                        x: Dimensions.get('window').width - 100,
                        y: this.state.height - 100,
                    },
            ),
        };
        this.state = {
            ...this.state,
            overlayPositions: `${this.state.topLeft.x._value},${
                this.state.topLeft.y._value
                } ${this.state.topRight.x._value},${this.state.topRight.y._value} ${
                this.state.bottomRight.x._value
                },${this.state.bottomRight.y._value} ${
                this.state.bottomLeft.x._value
                },${this.state.bottomLeft.y._value}`,
        };

        this.panResponderTopLeft = this.createPanResponser(this.state.topLeft);
        this.panResponderTopRight = this.createPanResponser(
            this.state.topRight,
        );
        this.panResponderBottomLeft = this.createPanResponser(
            this.state.bottomLeft,
        );
        this.panResponderBottomRight = this.createPanResponser(
            this.state.bottomRight,
        );
    }

    componentWillMount() {
        Image.getSize(this.props.initialImage, (width, height) => {
            // get the proportion
            const proportion = height / width;
            this.setState({
                height: (Dimensions.get('window').width - 60) * proportion,
            });
        });
    }

    createPanResponser(corner) {
        return PanResponder.create({
            onMoveShouldSetResponderCapture: () => true,
            onMoveShouldSetPanResponderCapture: () => true,
            onPanResponderMove: (e, gestureState) => {
                Animated.event([
                    null,
                    {
                        dx: corner.x,
                        dy: corner.y,
                    },
                ])(e, gestureState);
            },
            onPanResponderRelease: () => {
                corner.flattenOffset();
                this.updateOverlayString();
            },
            onPanResponderGrant: () => {
                corner.setOffset({ x: corner.x._value, y: corner.y._value });
                corner.setValue({ x: 0, y: 0 });
            },
        });
    }

    returnLimiter(corner) {
        return [{
            translateX: corner.x.interpolate({
                inputRange: [this.state.imageX, this.state.imageX + this.state.width],
                outputRange: [this.state.imageX, this.state.imageX + this.state.width],
                extrapolate: 'clamp'
            })
        },
        {

            translateY: corner.y.interpolate({
                inputRange: [this.state.imageY, this.state.imageY + this.state.height],
                outputRange: [this.state.imageY, this.state.imageY + this.state.height],
                extrapolate: 'clamp'
            }),
        }];
    }

    crop() {
        const coordinates = {
            topLeft: this.viewCoordinatesToImageCoordinates(this.state.topLeft),
            topRight: this.viewCoordinatesToImageCoordinates(
                this.state.topRight,
            ),
            bottomLeft: this.viewCoordinatesToImageCoordinates(
                this.state.bottomLeft,
            ),
            bottomRight: this.viewCoordinatesToImageCoordinates(
                this.state.bottomRight,
            ),
            height: this.state.height,
            width: this.state.width,
        };
        NativeModules.CustomCropManager.crop(
            coordinates,
            this.state.image,
            (err, res) => this.props.updateImage(res.image, coordinates),
        );
    }

    updateOverlayString() {
        const rightMax = this.state.imageX + this.state.width;
        const bottomMax = this.state.imageY + this.state.height;

        const topLeftX = this.state.topLeft.x._value < this.state.imageX ? this.state.imageX : this.state.topLeft.x._value;
        const topLeftY = this.state.topLeft.y._value < this.state.imageY ? this.state.imageY : this.state.topLeft.y._value;
        const topRightX = this.state.topRight.x._value < rightMax ? this.state.topRight.x._value : rightMax;
        const topRightY = this.state.topRight.y._value > this.state.imageY ? this.state.topRight.y._value : this.state.imageY;
        const bottomRightX = this.state.bottomRight.x._value < rightMax ? this.state.bottomRight.x._value : rightMax;
        const bottomRightY = this.state.bottomRight.y._value > bottomMax ? bottomMax : this.state.bottomRight.y._value;
        const bottomLeftX = this.state.bottomLeft.x._value < this.state.imageX ? this.state.imageX : this.state.bottomLeft.x._value;
        const bottomLeftY = this.state.bottomLeft.y._value < bottomMax ? this.state.bottomLeft.y._value : bottomMax;
        this.setState({
            overlayPositions: `${topLeftX},${topLeftY} 
                ${topRightX},${topRightY} 
                ${bottomRightX},${bottomRightY} 
                ${bottomLeftX},${bottomLeftY}`,
        });
    }

    imageCoordinatesToViewCoordinates(corner) {
        return {
            x: (corner.x * Dimensions.get('window').width) / this.state.width,
            y: (corner.y * this.state.height) / this.state.height,
        };
    }

    viewCoordinatesToImageCoordinates(corner) {
        return {
            x:
                (corner.x._value / Dimensions.get('window').width) *
                this.state.width,
            y: (corner.y._value / this.state.height) * this.state.height,
        };
    }

    onLayout = event => {
        var layout = event.nativeEvent.layout;
        this.setState({
            imageX: layout.x,
            imageY: layout.y
        },
            () => {
                this.state.topLeft.setValue({ x: layout.x, y: layout.y });
                this.state.topRight.setValue({ x: layout.x + layout.width, y: layout.y });
                this.state.bottomLeft.setValue({ x: layout.x, y: layout.y + layout.height });
                this.state.bottomRight.setValue({ x: layout.x + layout.width, y: layout.y + layout.height });
                this.updateOverlayString();

            }
        );
    }

    render() {
        return (
            <View
                style={{
                    flex: 1,
                    justifyContent: 'flex-end',
                }}
            >
                <View
                    style={[
                        s(this.props).cropContainer,
                    ]}
                >
                    <Image
                        style={[
                            s(this.props).image,
                            { height: this.state.height },
                        ]}
                        onLayout={e => this.onLayout(e)}
                        source={{ uri: this.state.image }}
                        resizeMode="center"
                    />
                    <Svg
                        height={Dimensions.get('window').height}
                        width={Dimensions.get('window').width}
                        style={{ position: 'absolute', left: 0, top: 0 }}
                    >
                        <AnimatedPolygon
                            ref={(ref) => (this.polygon = ref)}
                            fill={this.props.overlayColor || 'blue'}
                            fillOpacity={this.props.overlayOpacity || 0.5}
                            stroke={this.props.overlayStrokeColor || 'blue'}
                            points={this.state.overlayPositions}
                            strokeWidth={this.props.overlayStrokeWidth || 3}
                        />
                    </Svg>
                    <Animated.View

                        {...this.panResponderTopLeft.panHandlers}
                        style={[
                            s(this.props).handler,
                            {
                                top: 0,
                                left: 0,
                                transform: this.returnLimiter(this.state.topLeft),
                            }
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: -10, top: -10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { left: 31, top: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderTopRight.panHandlers}
                        style={[
                            s(this.props).handler,
                            {
                                top: 0,
                                left: 0,
                                transform: this.returnLimiter(this.state.topRight),
                            }
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: 10, top: -10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { right: 31, top: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderBottomLeft.panHandlers}
                        style={[
                            s(this.props).handler,
                            {
                                top: 0,
                                left: 0,
                                transform: this.returnLimiter(this.state.bottomLeft),
                            }
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: -10, top: 10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { left: 31, bottom: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderBottomRight.panHandlers}
                        style={[
                            s(this.props).handler,
                            {
                                top: 0,
                                left: 0,
                                transform: this.returnLimiter(this.state.bottomRight),
                            }
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: 10, top: 10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { right: 31, bottom: 31 },
                            ]}
                        />
                    </Animated.View>
                </View>
            </View>
        );
    }
}

const s = (props) => ({
    handlerI: {
        borderRadius: 0,
        height: 20,
        width: 20,
        backgroundColor: props.handlerColor || 'blue',
    },
    handlerRound: {
        width: 39,
        position: 'absolute',
        height: 39,
        borderRadius: 100,
        backgroundColor: props.handlerColor || 'blue',
    },
    image: {
        width: Dimensions.get('window').width - 60,
        position: 'absolute',
        left: 30,
    },
    bottomButton: {
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'blue',
        width: 70,
        height: 70,
        borderRadius: 100,
    },
    handler: {
        height: 140,
        width: 140,
        overflow: 'visible',
        marginLeft: -70,
        marginTop: -70,
        alignItems: 'center',
        justifyContent: 'center',
        position: 'absolute',
    },
    cropContainer: {
        flex: 1,
        justifyContent: 'center',
    },
});

export default CustomCrop;
