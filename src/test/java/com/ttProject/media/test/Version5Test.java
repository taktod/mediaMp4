package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.version5.AtomAnalyzer;
import com.ttProject.media.version5.IndexFileCreator;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * version5 flv変換のテスト
 * とりあえず変換用の一時データは次のようにします。
 * 
 * 通常のmp4のstsc stsz stco stts stssの情報を保持しておく。
 * keyFrameの部分は別途タグをつくって、保存しておく。
 * カスタムタグとしてvdeo sond metaをつくっておく(videoとsound、metaの入れ物という意味)
 * カスタムタグとしてmsh もつくっておく
 * vdeo   size vdeo flg version resSize inheritBox
 *  msh   size msh data
 *  stco
 *  stsc
 *  stsz
 *  stts
 *  stss
 * sond
 *  msh 
 *  stco
 *  stsc
 *  stsz
 *  stts
 * meta
 *  msh
 * @author taktod
 */
public class Version5Test {
	public void test() throws Exception {
		// mp4の内容を解析して、flvに変換しつつ応答するservletを書きたい。
		/*
		 * やること
		 * 1:音声と映像のtrakがあるか確認する。
		 * 2:トラックがflvに適合しているかしらべておく。映像はavc音声はmp4a
		 * 3:mediaSequenceHeaderになるべき部分を抜き出す。
		 * 4:とりあえずmetaタグはどうでもいい。
		 * どうしてもというならduration、width、heightあたりは解析して応答してもいいかも
		 * 5:できたら任意の場所以降のデータを応答することに対応しておきたい。
		 */
		FileChannel fc = new FileOutputStream("test.txt").getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(5);
		buffer.put("test\n".getBytes());
		buffer.flip();
		fc.write(buffer);
		buffer = ByteBuffer.allocate(5);
		buffer.put("test\n".getBytes());
		buffer.flip();
		fc.write(buffer);
		fc.truncate(5);
		fc.close();
	}
	@Test
	public void analyzeTest() throws Exception {
//		IFileReadChannel fc = FileReadChannel.openFileReadChannel("http://49.212.39.17/test.mp4");
		IFileReadChannel fc = FileReadChannel.openFileReadChannel(
				Thread.currentThread().getContextClassLoader().getResource("mario2.mp4")
		);
		IndexFileCreator analyzer = new IndexFileCreator("output.tmp");
		Atom atom = null;
		while((atom = analyzer.analyze(fc)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		analyzer.updatePrevTag();
		analyzer.close();
		IFileReadChannel tmp = FileReadChannel.openFileReadChannel("output.tmp");
		makeupFlv(fc, tmp);
		// ここまでで一時ファイルの作成(サイズの設定等はまだ)がおわっています。
		// このタイミングでデータを読み込み直して必要な情報を調べます。
		// 一時ファイルをみて、データを取り出しつつ処理をすすめていく。
		tmp.close();
		fc.close();
	}
	private Vdeo vdeo;
	private Sond sond;
	private void makeupFlv(IFileReadChannel source, IFileReadChannel tmp) throws Exception {
		// tmpファイルからVdeoとSondを取り出す。
		while(tmp.position() < tmp.size()) {
			System.out.println("pos:" +tmp.position());
			System.out.println("size:" + tmp.size());
			int position = tmp.position();
			ByteBuffer buffer = BufferUtil.safeRead(tmp, 8);
			int size = buffer.getInt();
			System.out.println(size);
			String tag = BufferUtil.getDwordText(buffer);
			if("vdeo".equals(tag)) {
				System.out.println("vdeo");
				vdeo = new Vdeo(size, position);
				vdeo.analyze(tmp);
				tmp.position(position + size);
			}
			else if("sond".equals(tag)) {
				System.out.println("sond");
				sond = new Sond(size, position);
				sond.analyze(tmp);
				tmp.position(position + size);
			}
			System.out.println("nextPos:" + position + size);
		}
		System.out.println(vdeo);
		System.out.println(sond);
	}
}