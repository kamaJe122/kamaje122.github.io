<!DOCTYPE html>
<html lang="zh-Hant">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>時間碼編輯器</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            margin: 0;
            background-color: #f0f0f0;
        }

        .container {
            background-color: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }

        h1 {
            text-align: center;
            color: #333;
        }

        .time-input, .note-input, .filename-input {
            margin-bottom: 10px;
        }

        label {
            display: inline-block;
            width: 80px;
        }

        input[type="text"] {
            width: 30px;
            text-align: center;
        }

        input#note, input#filename {
            width: 200px;
        }

        button {
            margin-top: 10px;
            padding: 5px 10px;
            background-color: #4CAF50;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        button:hover {
            background-color: #45a049;
        }

        #output {
            margin-top: 20px;
            border-top: 1px solid #ddd;
            padding-top: 10px;
        }

        .delete-btn {
            margin-right: 5px;
            background-color: #f44336;
        }

        .delete-btn:hover {
            background-color: #d32f2f;
        }

        .footer {
            margin-top: 20px;
            text-align: center;
            font-size: 0.8em;
            color: #666;
        }
    </style>
</head>

<body>
    <div class="container">
        <h1>時間碼編輯器</h1>
        <div class="filename-input">
            <label for="filename">文件名稱：</label>
            <input type="text" id="filename" placeholder="請輸入文件名稱">
        </div>
        <div class="time-input">
            <label>開始時間：</label>
            <input type="text" id="startHour" maxlength="2" placeholder="時" onkeyup="autoTab(this, 'startMinute')">
            <span>:</span>
            <input type="text" id="startMinute" maxlength="2" placeholder="分" onkeyup="autoTab(this, 'startSecond')">
            <span>:</span>
            <input type="text" id="startSecond" maxlength="2" placeholder="秒" onkeyup="autoTab(this, 'startMillisecond')">
            <span>:</span>
            <input type="text" id="startMillisecond" maxlength="3" placeholder="毫秒" onkeyup="autoTab(this, 'endHour')">
        </div>
        <div class="time-input">
            <label>結束時間：</label>
            <input type="text" id="endHour" maxlength="2" placeholder="時" onkeyup="autoTab(this, 'endMinute')">
            <span>:</span>
            <input type="text" id="endMinute" maxlength="2" placeholder="分" onkeyup="autoTab(this, 'endSecond')">
            <span>:</span>
            <input type="text" id="endSecond" maxlength="2" placeholder="秒" onkeyup="autoTab(this, 'endMillisecond')">
            <span>:</span>
            <input type="text" id="endMillisecond" maxlength="3" placeholder="毫秒" onkeyup="autoTab(this, 'note')" onkeydown="handleEnterKey(event)">
        </div>
        <div class="note-input">
            <label for="note">備註：</label>
            <input type="text" id="note" onkeydown="handleEnterKey(event)">
        </div>
        <button onclick="addData()">新增</button>
        <button onclick="outputSRT()">輸出SRT</button>
        <div id="output"></div>
        <div class="footer">
            出處:Kamaje161(@) 2024/06/30更新<br>
            感謝Perplexity Pro強大的Claude 3.5 SONNET
        </div>
    </div>

    <script>
        let timeData = [];

        function updateCurrentTime() {
            const now = new Date();
            const filename = document.getElementById('filename');
            if (!filename.value) {
                filename.value = now.toLocaleString('zh-TW', { hour12: false });
            }
            setTimeout(updateCurrentTime, 1000);
        }

        updateCurrentTime();

        function autoTab(current, next) {
            if (current.value.length >= current.maxLength) {
                document.getElementById(next).focus();
            }
        }

        function handleEnterKey(event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                addData();
                clearTimeInputs();
                document.getElementById('startHour').focus();
            }
        }

        function clearTimeInputs() {
            const timeInputs = ['startHour', 'startMinute', 'startSecond', 'startMillisecond',
                'endHour', 'endMinute', 'endSecond', 'endMillisecond', 'note'
            ];
            timeInputs.forEach(id => document.getElementById(id).value = '');
        }

        function addData() {
            const startTime = getTime('start');
            let endTime = getTime('end');

            if (isTimeEmpty(endTime)) {
                endTime = addSecondsToTime(startTime, 10);
                setTime('end', endTime);
            }

            const note = document.getElementById('note').value;

            if (validateTime(startTime) && validateTime(endTime)) {
                timeData.push({
                    start: startTime,
                    end: endTime,
                    note: note
                });
                timeData.sort((a, b) => compareTime(a.start, b.start));
                updateOutput();
                clearTimeInputs();
            } else {
                alert('請輸入有效的時間格式');
            }
        }

        function getTime(prefix) {
            return {
                hour: document.getElementById(prefix + 'Hour').value.padStart(2, '0'),
                minute: document.getElementById(prefix + 'Minute').value.padStart(2, '0'),
                second: document.getElementById(prefix + 'Second').value.padStart(2, '0'),
                millisecond: document.getElementById(prefix + 'Millisecond').value.padStart(3, '0')
            };
        }

        function setTime(prefix, time) {
            document.getElementById(prefix + 'Hour').value = time.hour;
            document.getElementById(prefix + 'Minute').value = time.minute;
            document.getElementById(prefix + 'Second').value = time.second;
            document.getElementById(prefix + 'Millisecond').value = time.millisecond;
        }

        function isTimeEmpty(time) {
            return time.hour === '00' && time.minute === '00' && time.second === '00' && time.millisecond === '000';
        }

        function addSecondsToTime(time, seconds) {
            let date = new Date(0, 0, 0, time.hour, time.minute, time.second, time.millisecond);
            date.setSeconds(date.getSeconds() + seconds);
            return {
                hour: date.getHours().toString().padStart(2, '0'),
                minute: date.getMinutes().toString().padStart(2, '0'),
                second: date.getSeconds().toString().padStart(2, '0'),
                millisecond: date.getMilliseconds().toString().padStart(3, '0')
            };
        }

        function validateTime(time) {
            return time.hour >= 0 && time.hour < 24 &&
                time.minute >= 0 && time.minute < 60 &&
                time.second >= 0 && time.second < 60 &&
                time.millisecond >= 0 && time.millisecond < 1000;
        }

        function compareTime(time1, time2) {
            const t1 = new Date(0, 0, 0, time1.hour, time1.minute, time1.second, time1.millisecond);
            const t2 = new Date(0, 0, 0, time2.hour, time2.minute, time2.second, time2.millisecond);
            return t1 - t2;
        }

        function updateOutput() {
            const output = document.getElementById('output');
            output.innerHTML = timeData.map((item, index) =>
                `<button class="delete-btn" onclick="deleteData(${index})">刪除</button>${index + 1}. ${formatTime(item.start)} --> ${formatTime(item.end)} ${item.note}`
            ).join('<br>');
        }

        function deleteData(index) {
            timeData.splice(index, 1);
            updateOutput();
        }

        function formatTime(time) {
            return `${time.hour}:${time.minute}:${time.second},${time.millisecond}`;
        }

        function outputSRT() {
            let srtOutput = timeData.map((item, index) =>
                `${index + 1}\n${formatTime(item.start)} --> ${formatTime(item.end)}\n${item.note}\n`
            ).join('\n');

            const filename = document.getElementById('filename').value || '未命名';
            const blob = new Blob([srtOutput], {
                type: 'text/plain'
            });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${filename}.srt`;
            a.click();
            URL.revokeObjectURL(url);
        }
    </script>
</body>

</html>
