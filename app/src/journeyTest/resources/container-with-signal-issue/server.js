const express = require('express');
const app = express();

app.get('/health', (req, res) => res.status(200).send());
app.listen(3000);
console.log('Server ready.');
