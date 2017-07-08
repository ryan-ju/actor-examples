module.exports = {
    entry: "./src/app.jsx",
    output: {
        path: __dirname,
        filename: "bundle.js"
    },
    module: {
        loaders: [
            { test: /\.css$/, loader: "style!css" },
            {
                test : /\.jsx?/,
                exclude: /node_modules/,
                loader : 'babel-loader'
            }
        ]
    },
    resolve: {
        extensions: ['.js', '.jsx'],
    }
};