Add-Type -AssemblyName System.Net.WebSockets

$ws = New-Object System.Net.WebSockets.ClientWebSocket
$uri = [System.Uri]"ws://localhost:7070/websocket"
$cts = New-Object System.Threading.CancellationTokenSource
$cts.CancelAfter(10000)

try {
    $ws.ConnectAsync($uri, $cts.Token).Wait()
    Write-Host "Connected!" -ForegroundColor Green

    function Send-Message($ws, $json) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        $seg = New-Object System.ArraySegment[byte]($bytes, 0, $bytes.Length)
        $ws.SendAsync($seg, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).Wait()
        Write-Host ">>> $json" -ForegroundColor Yellow
    }

    function Receive-Message($ws) {
        $buf = New-Object byte[] 4096
        $seg = New-Object System.ArraySegment[byte]($buf, 0, $buf.Length)
        $result = $ws.ReceiveAsync($seg, $cts.Token).Result
        $str = [System.Text.Encoding]::UTF8.GetString($buf, 0, $result.Count)
        Write-Host "<<< $str" -ForegroundColor Cyan
        return $str
    }

    # 1. REGISTER
    Send-Message $ws '{"action":"REGISTER","login":"testuser1","password":"1234","name":"Test","surname":"User"}'
    Start-Sleep -Seconds 1
    $regResp = Receive-Message $ws
    $regJson = $regResp | ConvertFrom-Json
    if ($regJson.status -ne "SUCCESS") { Write-Host "REGISTER FAILED: $($regJson.error)" -ForegroundColor Red; exit 1 }
    $token = $regJson.payload.token
    $userCode = $regJson.payload.userCode
    Write-Host "Token: $token" -ForegroundColor Green

    # 2. LOGIN
    Send-Message $ws '{"action":"LOGIN","login":"testuser1","password":"1234"}'
    Start-Sleep -Seconds 1
    $loginResp = Receive-Message $ws
    $loginJson = $loginResp | ConvertFrom-Json
    if ($loginJson.status -ne "SUCCESS") { Write-Host "LOGIN FAILED: $($loginJson.error)" -ForegroundColor Red; exit 1 }
    $token = $loginJson.payload.token
    $userCode = $loginJson.payload.userCode
    Write-Host "Login OK, userCode: $userCode" -ForegroundColor Green

    # 3. CREATE_CHANNEL
    Send-Message $ws ('{{"action":"CREATE_CHANNEL","token":"{0}","name":"general","description":"General chat"}}' -f $token)
    Start-Sleep -Seconds 1
    $chResp = Receive-Message $ws
    $chJson = $chResp | ConvertFrom-Json
    if ($chJson.status -ne "SUCCESS") { Write-Host "CREATE_CHANNEL FAILED: $($chJson.error)" -ForegroundColor Red; exit 1 }
    $channelCode = $chJson.payload.code
    Write-Host "Channel created: $channelCode" -ForegroundColor Green

    # 4. SEARCH_CHANNELS
    Send-Message $ws ('{{"action":"SEARCH_CHANNELS","token":"{0}"}}' -f $token)
    Start-Sleep -Seconds 1
    $searchResp = Receive-Message $ws
    Write-Host "SEARCH_CHANNELS response: $searchResp" -ForegroundColor Cyan

    # 5. CREATE_MESSAGE
    Send-Message $ws ('{{"action":"CREATE_MESSAGE","token":"{0}","channelCode":"{1}","text":"Hello from test!"}}' -f $token, $channelCode)
    Start-Sleep -Seconds 1
    $msgResp = Receive-Message $ws
    Write-Host "CREATE_MESSAGE response: $msgResp" -ForegroundColor Cyan

    # 6. SEARCH_MESSAGES
    Send-Message $ws ('{{"action":"SEARCH_MESSAGES","token":"{0}","channelCode":"{1}"}}' -f $token, $channelCode)
    Start-Sleep -Seconds 1
    $srchResp = Receive-Message $ws
    Write-Host "SEARCH_MESSAGES response: $srchResp" -ForegroundColor Cyan

    Write-Host "ALL TESTS PASSED!" -ForegroundColor Green

}
catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
}
finally {
    $ws.Dispose()
}
