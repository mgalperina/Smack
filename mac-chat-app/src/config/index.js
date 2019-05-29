import mongodb from 'mongodb';

export default {
  // "port": 3005,
  // "mongoUrl": "mongodb://localhost:27017/chat-api",
  "port": process.env.PORT,
  "mongoUrl": "mongodb://MariDG:qwerty123@ds125588.mlab.com:25588/heroku_l0d42d0m",
  "bodyLimit": "100kb"
}
