タイトル：mediaMp4

　musicTubeで使っているmp4ファイルへのhttpProxy動作させるプログラムの公開バージョン
　こんなことができますよという紹介のために作りました。
　興味のある方はお気軽にボクにコンタクトとってみてください。

ライセンス：LGPL

　こちらもとりあえずLGPLにしときます。

使い方：

　maven3とjava1.6以降のjdkを入手します。
　pom.xmlのあるディレクトリに移動して、以下のmavenコマンドを実行してもらえれば勝手にjettyが起動します。
　$ mvn jetty:run
　これでhttp://localhost:8080/test.mp4でアクセスできます。
　mp4の再生がhtml5で可能なchromeあたりを使ってもらえると良いかと思います。

　maven2を利用している場合はpom.xmlのgithubのリポジトリ指定を書き換えてください。

　いまのところ実装しているのは以下の３つ
　単純proxy : http://localhost:8080/test.mp4
　映像off proxy : http://localhost:8080/test.m4
　映像削除 proxy : http://localhost:8080/test.m4a

実践で利用できそうなものは？

　musicTubeで利用しているみたいに映像部分をoffにすることでiOS6等でもBGM再生させる。
　CDNサービスのデータ取得先にこのservletを指定することでソースは１つなのにいろいろなメディアデータを提供させる。
　ということができそうです。
