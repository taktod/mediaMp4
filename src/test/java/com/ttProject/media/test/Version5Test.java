package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.junit.Test;

import com.ttProject.media.flv.CodecType;
import com.ttProject.media.flv.FlvHeader;
import com.ttProject.media.flv.FlvTagOrderManager;
import com.ttProject.media.flv.tag.AudioTag;
import com.ttProject.media.flv.tag.MetaTag;
import com.ttProject.media.flv.tag.VideoTag;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.version5.IndexFileCreator;
import com.ttProject.media.version5.mp4.Meta;
import com.ttProject.media.version5.mp4.Sond;
import com.ttProject.media.version5.mp4.Vdeo;
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
			Thread.currentThread().getContextClassLoader().getResource("test.mp4")
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
	private Meta meta;
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
			else if("meta".equals(tag)) {
				meta = new Meta(size, position);
				meta.analyze(tmp);
				tmp.position(position + size);
			}
		}
		// ここから先データを取り出して調整します。
//		System.out.println("videoつくるよ");
//		makeVideo(source, tmp);
//		System.out.println("audioつくるよ");
//		makeAudio(source, tmp);
	}
	private int startPos = 30000;
	private FileChannel output;
	/**
	 * 音声データの抜き出しを実行してみる。
	 * @param source
	 * @param tmp
	 * @throws Exception
	 */
	private void makeAudio(IFileReadChannel source, IFileReadChannel tmp) throws Exception {
		output = new FileOutputStream("target.flv").getChannel();
		// headerを書き込む
		FlvHeader flvHeader = new FlvHeader();
		flvHeader.setVideoFlg(false);
		flvHeader.setAudioFlg(true);
		flvHeader.writeTag(output);
		// データを書き込む
		sond.getStco().start(tmp, false);
		sond.getStsc().start(tmp, false);
		sond.getStsz().start(tmp, false);
		sond.getStts().start(tmp, false);
		System.out.println(sond.getTimescale());
		long timePos = 0;
		int sourcePos = 0;
		boolean isWriting = false;
		while(sond.getStco().nextChunkPos() != -1) {
			sourcePos = sond.getStco().getChunkPos();
			sond.getStsc().nextChunk();
			int chunkSampleCount = sond.getStsc().getSampleCount();
			for(int i = 0;i < chunkSampleCount;i ++) {
				int sampleSize = sond.getStsz().nextSampleSize();
				if(sampleSize == -1) {
					break;
				}
				if(timePos * 1000 / sond.getTimescale() > startPos && !isWriting) {
					isWriting = true;
					// metaデータをつくる。
					MetaTag metaTag = new MetaTag();
					metaTag.setTimestamp((int)(timePos * 1000 / sond.getTimescale()));
					metaTag.putData("duration", meta.getDuration() / 1000.0D);
					metaTag.writeTag(output);
					// mediaSequenceHeaderを書き込む
					System.out.println("mshつくる"); // aacの場合のみ
					// aacの場合
					AudioTag mshTag = sond.createFlvMshTag(tmp);
					if(mshTag != null) {
						mshTag.setTimestamp((int)(timePos * 1000 / sond.getTimescale()));
						mshTag.writeTag(output);
					}
				}
				if(isWriting) {
					AudioTag tag = new AudioTag();
					tag.setCodec(CodecType.AAC);
					tag.setChannels(sond.getChannelCount());
					tag.setSampleRate(sond.getSampleRate());
					tag.setTimestamp((int)(timePos * 1000 / sond.getTimescale()));
					source.position(sourcePos);
					tag.setData(source, sampleSize);
					tag.writeTag(output);
				}
				int delta = sond.getStts().nextDuration();
				if(delta == -1) {
					break;
				}
				timePos += delta;
				sourcePos += sampleSize;
			}
		}
		output.close();
	}
	/**
	 * 映像データの抜き出しを実行してみる。
	 * @param source
	 * @param tmp
	 * @throws Exception
	 */
	private void makeVideo(IFileReadChannel source, IFileReadChannel tmp) throws Exception {
		output = new FileOutputStream("target.flv").getChannel();
		// headerを書き込む
		FlvHeader flvHeader = new FlvHeader();
		flvHeader.setVideoFlg(true);
		flvHeader.setAudioFlg(false);
		flvHeader.writeTag(output);
		// データを書き込む
		// stssの場合はkeyFrame
		// stcoから位置をしる。
		vdeo.getStco().start(tmp, false); // dataPos
		vdeo.getStsc().start(tmp, false); // samples in chunk
		vdeo.getStsz().start(tmp, false); // sample size
		vdeo.getStss().start(tmp, false); // keyFrame
		vdeo.getStts().start(tmp, false); // time
		System.out.println("初期化おわり");
		long timePos = 0;
		int sampleCount = 0;
		int sourcePos = 0; // 大本データの読み込み先場所
		boolean isWriting = false;
		vdeo.getStss().nextKeyFrame(); // 次のキーフレーム番号を取得しておく。
		System.out.println("ここきた。");
		// を延々と書き続けていけばよさそう。
		while(vdeo.getStco().nextChunkPos() != -1) {
			// chunkデータがまだある場合
			sourcePos = vdeo.getStco().getChunkPos();
			vdeo.getStsc().nextChunk();
			int chunkSampleCount = vdeo.getStsc().getSampleCount();
			for(int i = 0;i < chunkSampleCount;i ++) {
				// 読み込むサイズを知る。
				int sampleSize = vdeo.getStsz().nextSampleSize();
				if(sampleSize == -1) {
					break;
				}
				sampleCount ++;
				VideoTag tag = null;
				if(isWriting) {
					tag = new VideoTag();
					tag.setCodec(CodecType.AVC);
					tag.setTimestamp((int)(timePos * 1000 / vdeo.getTimescale()));
				}
				// ここでデータがわかるので、追記しておく
				if(vdeo.getStss().getKeyFrame() == sampleCount) {
					if(timePos * 1000 / vdeo.getTimescale() > startPos && !isWriting) {
						isWriting = true;
						// metaデータをつくる。
						MetaTag metaTag = meta.createFlvMetaTag();
						metaTag.setTimestamp((int)(timePos * 1000 / vdeo.getTimescale()));
						metaTag.writeTag(output);
						// mediaSequenceHeaderを書き込む
						VideoTag mshTag = vdeo.createFlvMshTag(tmp);
						if(mshTag != null) {
							mshTag.setTimestamp((int)(timePos * 1000 / vdeo.getTimescale()));
							mshTag.writeTag(output);
						}

						// videoTagをつくっていく。
						tag = new VideoTag();
						tag.setCodec(CodecType.AVC);
						tag.setTimestamp((int)(timePos * 1000 / vdeo.getTimescale()));
					}
					if(isWriting) {
						tag.setFrameType(true);
						source.position(sourcePos);
						tag.setData(source, sampleSize);
						tag.writeTag(output);
					}
					vdeo.getStss().nextKeyFrame();
				}
				else {
					if(isWriting) {
						tag.setFrameType(false);
						source.position(sourcePos);
						tag.setData(source, sampleSize);
						tag.writeTag(output);
					}
				}
				int delta = vdeo.getStts().nextDuration();
				sourcePos += sampleSize;
				if(delta == -1) {
					break;
				}
				timePos += delta;
			}
		}
		output.close();
	}
}
