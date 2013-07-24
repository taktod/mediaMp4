package com.ttProject.media.test;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ttProject.media.aac.Frame;
import com.ttProject.media.aac.FrameAnalyzer;
import com.ttProject.media.aac.IFrameAnalyzer;
import com.ttProject.media.mpegts.CodecType;
import com.ttProject.media.mpegts.field.PmtElementaryField;
import com.ttProject.media.mpegts.field.PtsField;
import com.ttProject.media.mpegts.packet.Pat;
import com.ttProject.media.mpegts.packet.Pes;
import com.ttProject.media.mpegts.packet.Pmt;
import com.ttProject.media.mpegts.packet.Sdt;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * mpegtsのpesを構築するテスト
 * 他のメディアデータを扱う必要がありそうなので、連携がとれるmediaMp4にかいておきます。
 * @author taktod
 *
 */
public class PesTest {
	@Test
	public void test() throws Exception {
		FileChannel output = null;
		IReadChannel target = null;
		ByteBuffer buffer;
		int left;
		try {
			output = new FileOutputStream("output.ts").getChannel();
			target = FileReadChannel.openFileReadChannel(
					Thread.currentThread().getContextClassLoader().getResource("smile.aac")
			);
			// とりあえずファイルにして再生できるかで判定したいので、つくってみることにする。
			// sdt
			Sdt sdt = new Sdt();
			sdt.writeDefaultProvider("taktodTools", "mpegtsMuxer");
			output.write(sdt.getBuffer());

			// pat
			Pat pat = new Pat();
			output.write(pat.getBuffer());
			
			// pmt
			Pmt pmt = new Pmt();
			pmt.addNewField(PmtElementaryField.makeNewField(CodecType.AUDIO_AAC));
			output.write(pmt.getBuffer());

			// ここからpesをつくっていく
			int counter = 0;
			int j = 0;
			while(true) {
				j ++;
				// pesをつくる。
				// これからpesに含めるデータを取り出す(複数のpesに渡るデータで問題ない)
				List<Frame> frames = new ArrayList<Frame>();
				IFrameAnalyzer analyzer = new FrameAnalyzer();
				int dataSize = 0;
				Frame frame = null;
				int findFrame = 0;
				while((frame = analyzer.analyze(target)) != null) {
					findFrame ++;
					// みつけたフレーム
					counter ++;
					// 現在のカウンター / 44.1fが再生時刻
					frames.add(frame);
					dataSize += frame.getSize();
					if(counter / 44.1f > 1 * j) {
						break;
					}
				}
				System.out.println(counter);
				if(findFrame == 0) {
					// もうデータがない
					break;
				}
				buffer = ByteBuffer.allocate(dataSize);
				for(Frame f : frames) {
					buffer.put(f.getBuffer());
				}
				buffer.flip();
				// TODO adaptationFieldが別にある状態でデータが足りなくなった場合、adaptationFieldをうまく操作する必要がある。
				// とりあえず対処できないので、データをすてておく。
				if(buffer.remaining() < 184) {
					// データが足りない場合はあきらめる。
					// 本来だったらadaptationFieldでうめてつじつまを合わせるべきだと思う。
					break;
				}
				// データ準備おわり
				// pesの情報をつくりだす。
				Pes pes = new Pes(CodecType.AUDIO_AAC, true, (short)0x0100, buffer, (long)(90000 * counter / 44.1f));
				// とりあえずadaptationFieldのpcrBaseにデータをいれたい。(初データなので、とりあえず放置でよい。)
//				pes.getAdaptationField().setPcrBase((long)(90000 * counter / 44.1f));
				// つづいてPesPacketLengthを書き込む dataSizeのこと
//				pes.setPesPacketLength((short)dataSize);
				// ptsDtsIndicatorは10を指定してptsのみ記述予定
				PtsField pts = new PtsField();
				pts.setPts((long)(90000 * counter / 44.1f));
				pes.setPts(pts);
				// どうやらh.264(映像？)でbframeをつかっている場合に設定する必要があるっぽい。
				// http://vfrmaniac.fushizen.eu/contents/pts_dts_generation.html
				// よくわからんので、とりあえずptsのみ書き込んでおく。
				// なおptsは盛り込んだデータ量でよさそう。
				// よって今回追加するデータ量は90000となります。(1秒分いれるため。)
				// PESHeaderLengthは5になります。
				ByteBuffer buf = pes.getBuffer();
				// TODO フレームはみつかるけど、始めのパケットのデータを満たすにはデータが足りないということがあるっぽい。うーん。
				left = 188 - buf.remaining();
				output.write(buf);
				byte[] b = new byte[left];
				buffer.get(b);
				buf = ByteBuffer.wrap(b);
				output.write(buf);
	
				while(true) {
					left = 184;
					if(183 > buffer.remaining()) {
						buf = pes.getSubHeaderBuffer(true);
						output.write(buf);
						System.out.println("データが減ってます。:" + buffer.remaining());
						// adaptationFieldで埋めるデータ量をきめる。
						left = 183 - buffer.remaining(); // このサイズ分だけ、0xffでうめる必要あり。
						System.out.println(left + 1);
						buf = ByteBuffer.allocate(left + 1);
						buf.put((byte)left);
						buf.put((byte)0);
						for(int i = 0;i < left - 1;i ++) {
							buf.put((byte)0xFF);
						}
						buf.flip();
						output.write(buf);
						output.write(buffer);
						break;
					}
					else if(183 == buffer.remaining()){
						buf = pes.getSubHeaderBuffer(true);
						output.write(buf);
						System.out.println("データが減ってます。:" + buffer.remaining());
						// adaptationFieldで埋めるデータ量をきめる。
						left = 2; // このサイズ分だけ、0xffでうめる必要あり。
						buf = ByteBuffer.allocate(2);
						buf.put((byte)1);
						buf.put((byte)0);
						buf.flip();
						output.write(buf);
						// bufferの部分からは、182バイト分だけコピーしてもよい。
						b= new byte[182];
						buffer.get(b);
						output.write(ByteBuffer.wrap(b));
					}
					else {
						buf = pes.getSubHeaderBuffer(false);
						output.write(buf);
						b = new byte[left];
						buffer.get(b);
						output.write(ByteBuffer.wrap(b));
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(output != null) {
				try {
					output.close();
				}
				catch (Exception e) {
				}
			}
			if(target != null) {
				try {
					target.close();
				}
				catch (Exception e) {
				}
			}
		}
	}
}
