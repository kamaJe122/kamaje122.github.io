require('dotenv').config();
const express = require('express');
const cors = require('cors');
const axios = require('axios');
const app = express();
const port = 3001;

// 中間件設定
app.use(cors({
  origin: 'http://localhost:3000' // 前端開發伺服器位置
}));
app.use(express.json({ limit: '10mb' }));

// OpenAI代理路由
app.post('/api/analyze', async (req, res) => {
  try {
    const response = await axios.post(
      'https://api.openai.com/v1/chat/completions',
      req.body,
      {
        headers: {
          'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`,
          'Content-Type': 'application/json'
        }
      }
    );
    res.json(response.data);
  } catch (error) {
    console.error('API Error:', error.response?.data || error.message);
    res.status(500).json({
      error: 'OpenAI API請求失敗',
      details: error.response?.data?.error || error.message
    });
  }
});

// 啟動伺服器
app.listen(port, () => {
  console.log(`後端服務運行在 http://localhost:${port}`);
});
