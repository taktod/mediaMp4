package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.junit.Test;

import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.version5.IndexFileCreator;
import com.ttProject.media.version5.Msh;
import com.ttProject.media.version5.Sond;
import com.ttProject.media.version5.Vdeo;
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
		IFileReadChannel fc = FileReadChannel.openFileReadChannel("http://49.212.39.17/mario.mp4");
/*		IFileReadChannel fc = FileReadChannel.openFileReadChannel(
			Thread.currentThread().getContextClassLoader().getResource("mario2.mp4")
		);*/
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
			int position = tmp.position();
			ByteBuffer buffer = BufferUtil.safeRead(tmp, 8);
			int size = buffer.getInt();
			String tag = BufferUtil.getDwordText(buffer);
			if("vdeo".equals(tag)) {
				vdeo = new Vdeo(size, position);
				vdeo.analyze(tmp);
				tmp.position(position + size);
			}
			else if("sond".equals(tag)) {
				sond = new Sond(size, position);
				sond.analyze(tmp);
				tmp.position(position + size);
			}
		}
		// ここから先データを取り出して調整します。
		System.out.println("videoつくるよ");
		makeVideo(source, tmp);
	}
	private FileChannel output;
	private void makeVideo(IFileReadChannel source, IFileReadChannel tmp) throws Exception {
		output = new FileOutputStream("target.flv").getChannel();
		// headerを書き込む
		FlvHeader flvHeader = new FlvHeader();
		flvHeader.setVideoFlg(true);
		flvHeader.setAudioFlg(false);
		flvHeader.write(output);
		// mediaSequenceHeaderを書き込む
		// 09 size 00 00 00 00 00 00 00 17 00 00 00 00 data endsize
		System.out.println("mshつくる");
		Msh msh = vdeo.getMsh();
		tmp.position(msh.getPosition() + 8);
		writeVideoTag(output, 0, 0, tmp, msh.getSize() - 8);
		System.out.println("here...");
		// データを書き込む
		// stssの場合はkeyFrame
		// stcoから位置をしる。
		vdeo.getStco().start(tmp, false); // dataPos
		vdeo.getStsc().start(tmp, false); // samples in chunk
		vdeo.getStsz().start(tmp, false); // sample size
		vdeo.getStss().start(tmp, false); // keyFrame
		vdeo.getStts().start(tmp, false); // time
		System.out.println("初期化おわり");
		int timePos = 0;
		int sampleCount = 0;
		vdeo.getStss().nextKeyFrame(); // 次のキーフレーム番号を取得しておく。
		System.out.println("ここきた。");
		// を延々と書き続けていけばよさそう。
		while(vdeo.getStco().nextChunkPos() != -1) {
			System.out.println("こうなった");
			// chunkデータがまだある場合
			source.position(vdeo.getStco().getChunkPos());
			vdeo.getStsc().nextChunk();
			int chunkSampleCount = vdeo.getStsc().getSampleCount();
			System.out.println(chunkSampleCount);
			for(int i = 0;i < chunkSampleCount;i ++) {
				// 読み込むサイズを知る。
				int sampleSize = vdeo.getStsz().nextSampleSize();
				if(sampleSize == -1) {
					break;
				}
				sampleCount ++;
				System.out.println("sampleCount:" + sampleCount);
				// ここでデータがわかるので、追記しておく
				if(vdeo.getStss().getKeyFrame() == sampleCount) {
					writeVideoTag(output, timePos, 1, source, sampleSize);
					vdeo.getStss().nextKeyFrame();
				}
				else {
					writeVideoTag(output, timePos, 2, source, sampleSize);
				}
				int delta = vdeo.getStts().nextDuration();
				System.out.println("delta:" + delta);
				if(delta == -1) {
					break;
				}
				timePos += delta;
			}
		}
		output.close();
	}
	/**
	 * 動画データを書き込む
	 * @param target 書き込み先
	 * @param timestamp timestamp値
	 * @param keyFrame キーフレームであるか? 1:keyFrame 0:msh 2:innerFrame
	 * @param source データ元のIFileReadChannel
	 * @param size データ元での読み込みバイト数
	 */
	private void writeVideoTag(WritableByteChannel target, int timestamp, int keyFrame, IFileReadChannel source, int size) throws Exception {
		// h.264の場合はsize + 5バイト、余計にデータが必要
		// 09 size timestamp 00 00 00 17 00 00 00 00 データ eof(msh)
		// 09 size timestamp 00 00 00 17 01 00 00 00 データ eof(keyFrame)
		// 09 size timestamp 00 00 00 27 01 00 00 00 データ eof(innerFrame)
		ByteBuffer buffer = ByteBuffer.allocate(11 + 5);
		buffer.putInt((size + 5) | 0x09000000);
		// timestamp
		buffer.put((byte)((timestamp >> 16) & 0xFF));
		buffer.put((byte)((timestamp >> 8) & 0xFF));
		buffer.put((byte)((timestamp >> 0) & 0xFF));
		buffer.put((byte)((timestamp >> 24) & 0xFF));
		switch(keyFrame) {
		case 0:
			buffer.putInt(0x17);
			buffer.putInt(0);
			break;
		case 1:
			buffer.putInt(0x17);
			buffer.putInt(0x01000000);
			break;
		case 2:
			buffer.putInt(0x27);
			buffer.putInt(0x01000000);
			break;
		default:
			break;
		}
		buffer.flip();
		output.write(buffer);
		System.out.println("timestamp:" +timestamp);
		System.out.println("pos:" + source.position());
		System.out.println("size:" + size);
		buffer = BufferUtil.safeRead(source, size);
		output.write(buffer);
		buffer = ByteBuffer.allocate(4);
		buffer.putInt(size + 5 + 11);
		buffer.flip();
		output.write(buffer);
	}
}
