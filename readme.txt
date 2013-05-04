タイトル：mediaMp4

　musicTubeで使っているmp4ファイルへのhttpProxy動作させるプログラムの公開バージョン
　こんなことができますよという紹介のために作りました。
　興味のある方はお気軽にボクにコンタクトとってみてください。

ライセンス：LGPL

　こちらもとりあえずLGPLにしときます。

使い方：

　myLib(http://github.com/taktod/myLib)と同じでmaven2とjava6が必要です。
　myLibのリポジトリに依存してあるので、myLibもcloneして自分のローカルレポに登録しておいてください。
　$ mvn installを実行すればOK

　pom.xmlのあるディレクトリに移動して、以下のmavenコマンドを実行してもらえれば勝手にjettyが起動します。
　$ mvn jetty:run
　これでhttp://localhost:8080/test.mp4でアクセスできます。
　mp4の再生がhtml5で可能なchromeあたりを使ってもらえると良いかと思います。

　いまのところ実装しているのは以下の２つ
　単純proxy : http://localhost:8080/test.mp4
　映像off proxy : http://localhost:8080/test.m4a

実践で利用できそうなものは？

　musicTubeで利用しているみたいに映像部分をoffにすることでiOS6等でもBGM再生させる。
　CDNサービスのデータ取得先にこのservletを指定することでソースは１つなのにいろいろなメディアデータを提供させる。
　ということができそうです。
