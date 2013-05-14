package com.ttProject.media.version4;

import java.io.File;

/**
 * 一時ファイル作成用のプログラム
 * @author taktod
 */
public class TmpFile extends File {
	private static final long serialVersionUID = 5154948834042168651L;
	public final long expire = 3600;
	public TmpFile(String path) {
		// 一時ファイルを作成します。
		super(System.getProperty("java.io.tmpdir") + path);
		getParentFile().mkdirs();
		deleteOnExit(); // プロセス終了時の自動削除候補にしておく。
	}
}
