# Google Play listing

The text of the store page, kept here so it changes with the app. Limits: title 30 characters, short description
80, full description 4000. Privacy policy: <https://github.com/kennyfs/Thirteen/blob/main/docs/privacy.md>.

Images: `icon-512.png` and `feature-zh.png` / `feature-en.png` (1024 x 500) come from `images.py`. The phone
screenshots (1920 x 1080) come from `STORE=1 ../device-tests.sh`, in `../build/screenshots`.

## zh-TW

**Title:** 十三支：AI 分析與連線對戰

**Short description:**

和神經網路練習十三支，分析你的擺法，也能和朋友連線或面對面比賽。

**Full description:**

四人十三支：每人把 13 張牌擺成 3、5、5 張的頭、中、尾三墩，再和其他三家比牌。

新手教學
• 幾頁就學會規則，點數依你目前選的規則說明
• 擺一手讓 AI 評分，並附上 AI 分析一萬手牌得出的技巧
• 導覽帶你看過每個畫面

練習
• 和三位 AI 對手對局，對手可以選神經網路、蒙地卡羅或較簡單的模型
• 每局結束都看得到四家的擺法、打槍、全壘打與得分
• 擺下一手時，可以回看上一手的結果與分析

分析
• 列出這手牌的候選擺法，附上期望得分，以及打槍、被打槍、贏各墩的機率
• 練習或對戰中的任何一手都能帶入分析，看看自己的擺法差多少

連線對戰
• 解題比賽：每個人拿到同一副牌，看誰擺得最好；一個人也可以玩，當作挑戰
• 同桌對局：一到四人同桌一起打，空位由 AI 補上
• 可以透過網路伺服器連線，也可以用「附近」功能以藍牙和 Wi-Fi 面對面連線，不需要網路
• 賽後可以逐手查看每個人的擺法並分析

其他
• 計分規則可以調整（墩數計分、特殊牌型加分等）
• 對局紀錄存在手機上，可以匯出
• 練習和分析完全在手機上離線運算
• 沒有廣告、沒有內購、不需要帳號
• 也有網頁版，電腦也能玩，還能和 app 的玩家連線對戰：https://thirteen.1li.tw
• 開放原始碼（GPLv3）：https://github.com/kennyfs/Thirteen

本 app 不涉及任何金錢或下注。

## en-US

**Title:** Thirteen: Chinese Poker AI

**Short description:**

Practice Chinese poker against a neural network, analyze hands, play friends.

**Full description:**

Four-player Thirteen (Chinese poker): set your 13 cards into rows of 3, 5 and 5, then compare them with the
three other players.

Learn
• A short tutorial teaches the rules, with the points under the rules you chose
• Set a hand the AI judges, and read tips from the AI's analysis of 10,000 hands
• A tour shows each screen

Practice
• Play against three AI opponents: a neural network, Monte Carlo search, or simpler models
• After every hand, see all four arrangements, scoops, home runs and points
• While you set the next hand, look back at the last one's result and analysis

Analysis
• See the candidate arrangements of a hand with their expected points and the chances of scooping, being scooped
  and winning each row
• Take any hand from practice or a battle into the analysis and see how far your arrangement was from the best

Multiplayer
• Puzzle contest: everyone gets the same cards, the best arrangement wins; alone, it is a challenge
• Table game: one to four people at one table, AI players fill the empty seats
• Play over a server on the internet, or face to face with Nearby, over Bluetooth and Wi-Fi, without a network
• Afterwards, look through every hand and everyone's arrangement, with analysis

Also
• Adjustable scoring rules (points per row, bonus hands, ...)
• Records stay on the phone and can be exported
• Practice and analysis run offline, on the phone
• No ads, no purchases, no account
• Also in a browser, on a computer too, and in the same rooms as the app: https://thirteen.1li.tw
• Open source (GPLv3): https://github.com/kennyfs/Thirteen

No money or betting is involved.

## What's new (release notes, 500 characters each)

### 2.1.1

zh-TW:

新增教學：幾頁就學會規則（點數依你目前選的規則說明），還能擺一手讓 AI 評分，並附上 AI 分析一萬手牌得出的技巧。導覽帶你看過每個畫面。「上一手」可在擺下一手時回看上一手的結果與分析。分析頁可滑過多張牌一次選取。

en-US:

New to Thirteen? A short tutorial teaches the rules (with the points under your own rules), lets you try a hand the
AI judges, and shares tips from the AI's analysis of 10,000 hands. A tour shows each screen. Last hand brings back
the previous hand's result and analysis while you set the next. Swipe across the cards on Analyze to pick them.
