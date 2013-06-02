<html><head>
<meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <script type="text/javascript" src="http://releases.flowplayer.org/js/flowplayer-3.2.12.min.js"></script>
    <title>Flowplayer Demo</title>
</head><body>
    <div id="page">
        <h1>Flowplayer Demo</h1>
        <a
             href="http://pseudo01.hddn.com/vod/demo.flowplayervod/flowplayer-700.flv"
             style="display:block;width:520px;height:330px"  
             id="player"> 
        </a> 
        <script>
flowplayer("player", "http://releases.flowplayer.org/swf/flowplayer-3.2.16.swf", {
    plugins: {
        pseudo: {
            url: "http://releases.flowplayer.org/swf/flowplayer.pseudostreaming-3.2.12.swf"
        }
    },
    // clip properties
    clip: {
        url: 'http://localhost:8080/test.flv',
        provider: 'pseudo',
    }
});
        </script>
    </div>
    <div>
      <h1>Html5 demo</h1>
      <video src="http://localhost:8080/test.mp4" width="520" height="330" controls></video>
    </div>
    <div>
      <h1>Html5(sound only tag off)</h1>
      <audio src="http://localhost:8080/test.m4" height="330" controls></audio>
    </div>
    <div>
      <h1>Html5(sound only tag del)</h1>
      <audio src="http://localhost:8080/test.m4a" height="330" controls></audio>
    </div>
</body></html>